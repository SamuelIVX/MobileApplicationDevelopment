package com.cuny.exchange.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cuny.exchange.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.actionCodeSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("cuny_auth_prefs", Context.MODE_PRIVATE)
    }

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val currentUserFlow: Flow<User?> = callbackFlow {
        var profileListener: ListenerRegistration? = null
        var lastObservedUid: String? = null
        var lastValidUser: User? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                profileListener?.remove()
                profileListener = null
                lastObservedUid = null
                lastValidUser = null
                trySend(null)
            } else {
                if (currentUser.uid != lastObservedUid) {
                    profileListener?.remove()
                    lastObservedUid = currentUser.uid
                    lastValidUser = null

                    val targetUid = currentUser.uid
                    profileListener = firestore.collection("users").document(targetUid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                // Preserve last valid profile on transient read error
                                if (lastValidUser != null) {
                                    trySend(lastValidUser)
                                } else {
                                    trySend(
                                        User(
                                            id = targetUid,
                                            displayName = currentUser.displayName.orEmpty(),
                                            isVerified = currentUser.isEmailVerified
                                        )
                                    )
                                }
                                return@addSnapshotListener
                            }
                            if (snapshot != null && snapshot.exists()) {
                                val user = snapshot.toObject(User::class.java)
                                if (user != null) {
                                    lastValidUser = user
                                    trySend(user)
                                }
                            } else {
                                val fallback = User(
                                    id = targetUid,
                                    displayName = currentUser.displayName.orEmpty(),
                                    isVerified = currentUser.isEmailVerified
                                )
                                trySend(fallback)
                            }
                        }
                }
            }
        }

        auth.addAuthStateListener(authListener)
        awaitClose {
            auth.removeAuthStateListener(authListener)
            profileListener?.remove()
            profileListener = null
            lastValidUser = null
        }
    }.flowOn(ioDispatcher)

    override fun isEmailValid(email: String): Boolean = isCunyEmail(email)

    override suspend fun sendSignInLink(email: String): Result<Unit> = withContext(ioDispatcher) {
        if (!isEmailValid(email)) {
            return@withContext Result.failure(IllegalArgumentException("Must end in .cuny.edu"))
        }

        try {
            val projectId = try {
                auth.app.options.projectId
            } catch (e: Exception) {
                null
            } ?: "cuny-exchange-4f43e"

            val actionCodeSettings = actionCodeSettings {
                url = "https://$projectId.firebaseapp.com/__/auth/links"
                handleCodeInApp = true
                setAndroidPackageName(
                    context.packageName,
                    true,
                    "1"
                )
            }

            auth.sendSignInLinkToEmail(email.trim(), actionCodeSettings).await()
            // Store pending email ONLY after the remote send succeeds
            prefs.edit().putString(KEY_PENDING_EMAIL, email.trim()).apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPendingEmail(): String? = prefs.getString(KEY_PENDING_EMAIL, null)

    fun clearPendingEmail() {
        prefs.edit().remove(KEY_PENDING_EMAIL).apply()
    }

    override suspend fun verifyEmailLink(email: String, link: String): Result<User> = withContext(ioDispatcher) {
        if (!isEmailValid(email)) {
            return@withContext Result.failure(IllegalArgumentException("Must end in .cuny.edu"))
        }

        if (!auth.isSignInWithEmailLink(link)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid or malformed sign-in link."))
        }

        try {
            val authResult = auth.signInWithEmailLink(email.trim(), link).await()
            val firebaseUser = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("Authentication succeeded but no user session was returned."))

            clearPendingEmail()

            val existingUser = try {
                val snapshot = firestore.collection("users").document(firebaseUser.uid).get().await()
                if (snapshot.exists()) {
                    snapshot.toObject(User::class.java)
                } else null
            } catch (ignored: Exception) {
                null
            }

            Result.success(
                existingUser ?: User(
                    id = firebaseUser.uid,
                    displayName = firebaseUser.displayName.orEmpty(),
                    isVerified = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(userId: String): Result<User?> = withContext(ioDispatcher) {
        try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.toObject(User::class.java))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveUserProfile(
        userId: String,
        displayName: String,
        campus: String,
        major: String
    ): Result<User> = withContext(ioDispatcher) {
        try {
            val userRef = firestore.collection("users").document(userId)
            val snapshot = userRef.get().await()
            if (snapshot.exists()) {
                // Use field-level updates to guarantee existing stats (rating, soldCount) are never overwritten
                userRef.update(
                    mapOf(
                        "displayName" to displayName.trim(),
                        "campus" to campus.trim(),
                        "major" to major.trim(),
                        "isVerified" to true
                    )
                ).await()

                val updatedSnapshot = userRef.get().await()
                val updatedUser = updatedSnapshot.toObject(User::class.java)
                    ?: User(id = userId, displayName = displayName.trim(), campus = campus.trim(), major = major.trim(), isVerified = true)
                Result.success(updatedUser)
            } else {
                val newUser = User(
                    id = userId,
                    displayName = displayName.trim(),
                    campus = campus.trim(),
                    major = major.trim(),
                    rating = 5.0,
                    soldCount = 0,
                    isVerified = true,
                    createdAt = Timestamp.now()
                )
                userRef.set(newUser).await()
                Result.success(newUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        clearPendingEmail()
        auth.signOut()
    }

    companion object {
        const val KEY_PENDING_EMAIL = "email_for_sign_in"
    }
}
