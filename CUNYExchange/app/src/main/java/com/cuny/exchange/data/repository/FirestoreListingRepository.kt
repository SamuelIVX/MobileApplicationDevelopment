package com.cuny.exchange.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cuny.exchange.data.model.Campus
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

class FirestoreListingRepository(
    private val context: Context? = null,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ListingRepository {

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // In-memory cache of saved listing IDs keyed by userId: Map<userId, Set<listingId>>
    private val savedListingIdsCache = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    init {
        loadSavedListingsFromStorage()
    }

    private fun loadSavedListingsFromStorage() {
        val currentUserId = auth.currentUser?.uid ?: return
        val saved = prefs?.getStringSet(getKeyForUser(currentUserId), emptySet()) ?: emptySet()
        savedListingIdsCache.update { current ->
            current + (currentUserId to saved)
        }
    }

    private fun getKeyForUser(userId: String): String = "saved_listings_$userId"

    override fun getListingsFlow(category: Category): Flow<List<Listing>> = callbackFlow {
        var query: Query = firestore.collection("listings")
            .whereIn("status", listOf("active", "sold"))

        if (category != Category.ALL) {
            query = query.whereEqualTo("category", category.label)
        }
        query = query.orderBy("createdAt", Query.Direction.DESCENDING).limit(100)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val listings = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject(Listing::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }?.filter { listing ->
                listing.status in listOf("active", "sold") &&
                    (category == Category.ALL || listing.category.equals(category.label, ignoreCase = true))
            }?.sortedByDescending { it.createdAt.seconds } ?: emptyList()

            trySend(listings)
        }

        awaitClose { listener.remove() }
    }

    override fun getSavedListingIdsFlow(userId: String): Flow<Set<String>> {
        // Initialize cache for user if not loaded yet
        if (!savedListingIdsCache.value.containsKey(userId)) {
            val saved = prefs?.getStringSet(getKeyForUser(userId), emptySet()) ?: emptySet()
            savedListingIdsCache.update { it + (userId to saved) }
        }
        return savedListingIdsCache.asStateFlow().map { it[userId] ?: emptySet() }
    }

    override fun getListingById(listingId: String): Flow<Listing?> = callbackFlow {
        val docRef = firestore.collection("listings").document(listingId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val listing = snapshot?.takeIf { it.exists() }?.let { doc ->
                try {
                    doc.toObject(Listing::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }
            trySend(listing)
        }

        awaitClose { listener.remove() }
    }

    override fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean> {
        return getSavedListingIdsFlow(userId).map { it.contains(listingId) }
    }

    override suspend fun toggleBookmark(listingId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val currentSet = savedListingIdsCache.value[userId]
            ?: prefs?.getStringSet(getKeyForUser(userId), emptySet())
            ?: emptySet()

        val updatedSet = if (currentSet.contains(listingId)) {
            currentSet - listingId
        } else {
            currentSet + listingId
        }

        // Persist to SharedPreferences
        prefs?.edit()?.putStringSet(getKeyForUser(userId), updatedSet)?.apply()

        // Update in-memory StateFlow
        savedListingIdsCache.update { current ->
            current + (userId to updatedSet)
        }
    }

    override suspend fun updateListingStatus(listingId: String, status: String): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val doc = firestore.collection("listings").document(listingId).get().await()
        val sellerId = doc.getString("sellerId")
        if (sellerId != currentUserId) {
            throw IllegalAccessException("Only the listing seller can update status to $status")
        }

        firestore.collection("listings").document(listingId)
            .update("status", status)
            .await()
        Unit
    }

    override suspend fun createListing(listing: Listing): Result<String> = runCatching {
        val currentUserId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        val currentUserEmail = auth.currentUser?.email
            ?: throw IllegalStateException("User has no verified email")

        val docRef = firestore.collection("listings").document()
        val listingId = docRef.id

        val userDoc = firestore.collection("users").document(currentUserId).get().await()
        val sellerName = userDoc.getString("displayName") ?: ""
        val sellerMajor = userDoc.getString("major") ?: ""
        val sellerRating = userDoc.getDouble("rating") ?: 5.0

        val campusSafeZone = Campus.findByName(listing.campus)?.safeZone
            ?: Campus.findById(listing.campus)?.safeZone
            ?: "Library lobby (security desk)"

        val fullListing = listing.copy(
            id = listingId,
            sellerId = currentUserId,
            sellerEmail = currentUserEmail,
            sellerName = sellerName,
            sellerMajor = sellerMajor,
            sellerRating = sellerRating,
            safeZone = campusSafeZone,
            status = "active",
            createdAt = Timestamp.now()
        )

        docRef.set(fullListing).await()
        listingId
    }

    companion object {
        private const val PREFS_NAME = "cuny_saved_listings_prefs"
    }
}
