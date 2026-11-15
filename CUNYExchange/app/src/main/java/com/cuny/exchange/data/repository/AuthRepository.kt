package com.cuny.exchange.data.repository

import com.cuny.exchange.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<User?>
    val currentUserId: String?
    suspend fun sendSignInLink(email: String): Result<Unit>
    suspend fun verifyEmailLink(email: String, link: String): Result<User>
    suspend fun getUserProfile(userId: String): Result<User?>
    suspend fun saveUserProfile(userId: String, displayName: String, campus: String, major: String): Result<User>
    suspend fun signOut()
    fun isEmailValid(email: String): Boolean
}

fun isCunyEmail(email: String): Boolean {
    val cunyRegex = Regex("^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\\.)*cuny\\.edu$", RegexOption.IGNORE_CASE)
    return email.trim().matches(cunyRegex)
}
