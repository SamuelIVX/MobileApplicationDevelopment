package com.cuny.exchange.data.repository

import android.content.Context
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.Message
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreChatRepository(
    private val context: Context? = null,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    override fun getThreadsFlow(userId: String): Flow<List<Thread>> = callbackFlow {
        val buyerThreads = mutableMapOf<String, Thread>()
        val sellerThreads = mutableMapOf<String, Thread>()
        var buyerReady = false
        var sellerReady = false

        fun emitCombined() {
            if (!buyerReady || !sellerReady) return
            val combined = (buyerThreads.values + sellerThreads.values)
                .distinctBy { it.id }
                .sortedByDescending { it.lastUpdated.seconds }
            trySend(combined)
        }

        val buyerQuery = firestore.collection("threads")
            .whereEqualTo("buyerId", userId)
        val buyerListener = buyerQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            buyerThreads.clear()
            snapshot?.documents?.forEach { doc ->
                runCatching { doc.toObject(Thread::class.java)?.copy(id = doc.id) }
                    .getOrNull()
                    ?.let { buyerThreads[doc.id] = it }
            }
            buyerReady = true
            emitCombined()
        }

        val sellerQuery = firestore.collection("threads")
            .whereEqualTo("sellerId", userId)
        val sellerListener = sellerQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            sellerThreads.clear()
            snapshot?.documents?.forEach { doc ->
                runCatching { doc.toObject(Thread::class.java)?.copy(id = doc.id) }
                    .getOrNull()
                    ?.let { sellerThreads[doc.id] = it }
            }
            sellerReady = true
            emitCombined()
        }

        awaitClose {
            buyerListener.remove()
            sellerListener.remove()
        }
    }

    override fun getThreadFlow(threadId: String): Flow<Thread?> = callbackFlow {
        val docRef = firestore.collection("threads").document(threadId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val thread = snapshot?.takeIf { it.exists() }?.let { doc ->
                try {
                    doc.toObject(Thread::class.java)?.copy(id = doc.id)
                } catch (_: Exception) {
                    null
                }
            }
            trySend(thread)
        }

        awaitClose { listener.remove() }
    }

    override fun getMessagesFlow(threadId: String): Flow<List<Message>> = callbackFlow {
        val query = firestore.collection("threads")
            .document(threadId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val messages = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()
            trySend(messages)
        }

        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(threadId: String, text: String): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val threadRef = firestore.collection("threads").document(threadId)
        val threadDoc = threadRef.get().await()
        if (!threadDoc.exists()) {
            throw IllegalArgumentException("Thread not found: $threadId")
        }

        val buyerId = threadDoc.getString("buyerId").orEmpty()
        val sellerId = threadDoc.getString("sellerId").orEmpty()
        if (currentUserId != buyerId && currentUserId != sellerId) {
            throw IllegalAccessException("User is not a participant in this thread")
        }

        val userDoc = firestore.collection("users").document(currentUserId).get().await()
        val senderName = userDoc.getString("displayName") ?: "Student"
        val counterpartId = if (currentUserId == buyerId) sellerId else buyerId

        val messageRef = threadRef.collection("messages").document()
        val now = Timestamp.now()

        val batchTask = firestore.runBatch { batch ->
            // 1. Insert message document
            batch.set(
                messageRef,
                mapOf(
                    "id" to messageRef.id,
                    "senderId" to currentUserId,
                    "senderName" to senderName,
                    "text" to text,
                    "createdAt" to now
                )
            )

            // 2. Atomically update parent thread summary and unreadUserIds
            batch.update(
                threadRef,
                mapOf(
                    "lastMessage" to text,
                    "lastUpdated" to now,
                    "unreadUserIds" to FieldValue.arrayUnion(counterpartId)
                )
            )
        }
        batchTask.addOnFailureListener { e ->
            android.util.Log.w("FirestoreChatRepository", "Deferred message write failed", e)
        }
        // Timeout is intentional: the write stays queued while offline.
        withTimeoutOrNull(3000L) {
            batchTask.await()
        }
        Unit
    }

    override suspend fun markThreadRead(threadId: String, userId: String): Result<Unit> = runCatching {
        firestore.collection("threads")
            .document(threadId)
            .update("unreadUserIds", FieldValue.arrayRemove(userId))
            .await()
        Unit
    }

    override suspend fun getOrCreateThread(listing: Listing, buyer: User): Result<String> = runCatching {
        // Query authorized participant threads for this listing and buyer
        val existing = firestore.collection("threads")
            .whereEqualTo("listingId", listing.id)
            .whereEqualTo("buyerId", buyer.id)
            .limit(1)
            .get()
            .await()

        val existingDoc = existing.documents.firstOrNull()
        if (existingDoc != null) {
            return@runCatching existingDoc.id
        }

        // Fetch verified participant profile data to satisfy Firestore security rules
        val sellerDoc = firestore.collection("users").document(listing.sellerId).get().await()
        val sellerName = sellerDoc.getString("displayName") ?: listing.sellerName.ifBlank { "CUNY Student" }
        val sellerCampus = sellerDoc.getString("campus") ?: listing.campus

        val buyerDoc = firestore.collection("users").document(buyer.id).get().await()
        val buyerName = buyerDoc.getString("displayName") ?: buyer.displayName.ifBlank { "CUNY Student" }
        val buyerCampus = buyerDoc.getString("campus") ?: buyer.campus

        val threadId = "${listing.id}_${buyer.id}"
        val threadRef = firestore.collection("threads").document(threadId)
        val newThread = Thread(
            id = threadId,
            listingId = listing.id,
            listingTitle = listing.title,
            buyerId = buyer.id,
            sellerId = listing.sellerId,
            buyerName = buyerName,
            sellerName = sellerName,
            buyerCampus = buyerCampus,
            sellerCampus = sellerCampus,
            lastMessage = "",
            lastUpdated = Timestamp.now(),
            unreadUserIds = emptyList()
        )

        threadRef.set(newThread).await()
        threadId
    }
}
