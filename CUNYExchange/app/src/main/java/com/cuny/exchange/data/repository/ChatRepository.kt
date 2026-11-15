package com.cuny.exchange.data.repository

import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.Message
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getThreadsFlow(userId: String): Flow<List<Thread>>
    fun getThreadFlow(threadId: String): Flow<Thread?>
    fun getMessagesFlow(threadId: String): Flow<List<Message>>
    suspend fun sendMessage(
        threadId: String,
        text: String,
        onEventualFailure: (Throwable) -> Unit = {}
    ): Result<Unit>
    suspend fun markThreadRead(threadId: String, userId: String): Result<Unit>
    suspend fun getOrCreateThread(listing: Listing, buyer: User): Result<String>
}
