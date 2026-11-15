package com.cuny.exchange

import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.Message
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ChatRepository
import com.cuny.exchange.ui.screens.chat.ChatViewModel
import com.cuny.exchange.ui.screens.chat.InboxViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeChatAuthRepository(
    initialUser: User? = null
) : AuthRepository {
    private val _currentUserFlow = MutableStateFlow(initialUser)
    override val currentUserFlow: Flow<User?> = _currentUserFlow.asStateFlow()
    override val currentUserId: String? get() = _currentUserFlow.value?.id

    override suspend fun sendSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyEmailLink(email: String, link: String): Result<User> =
        Result.success(_currentUserFlow.value ?: User(id = "user-1", displayName = "Test"))
    override suspend fun getUserProfile(userId: String): Result<User?> = Result.success(_currentUserFlow.value)
    override suspend fun saveUserProfile(userId: String, displayName: String, campus: String, major: String): Result<User> =
        Result.success(User(id = userId, displayName = displayName))
    override suspend fun signOut() {
        _currentUserFlow.value = null
    }
    fun setUser(user: User?) {
        _currentUserFlow.value = user
    }
    override fun isEmailValid(email: String): Boolean = true
}

private class FakeChatRepository(
    initialThreads: List<Thread> = emptyList(),
    initialMessages: Map<String, List<Message>> = emptyMap()
) : ChatRepository {
    val threadsFlow = MutableStateFlow(initialThreads)
    val messagesByThread = MutableStateFlow(initialMessages)

    var shouldFailSend: Boolean = false
    var shouldThrowOnThreads: Boolean = false
    var markedReadCalls = mutableListOf<Pair<String, String>>()
    var lastOnEventualFailure: ((Throwable) -> Unit)? = null

    override fun getThreadsFlow(userId: String): Flow<List<Thread>> {
        if (shouldThrowOnThreads) {
            return kotlinx.coroutines.flow.flow { throw RuntimeException("Firestore listener error") }
        }
        return threadsFlow.map { list ->
            list.filter { it.buyerId == userId || it.sellerId == userId }
                .sortedByDescending { it.lastUpdated.seconds }
        }
    }

    override fun getThreadFlow(threadId: String): Flow<Thread?> {
        return threadsFlow.map { list -> list.firstOrNull { it.id == threadId } }
    }

    override fun getMessagesFlow(threadId: String): Flow<List<Message>> {
        return messagesByThread.map { map ->
            (map[threadId] ?: emptyList()).sortedBy { it.createdAt.seconds }
        }
    }

    override suspend fun sendMessage(
        threadId: String,
        text: String,
        onEventualFailure: (Throwable) -> Unit
    ): Result<Unit> {
        lastOnEventualFailure = onEventualFailure
        if (shouldFailSend) {
            return Result.failure(RuntimeException("Network error dispatching message"))
        }

        val thread = threadsFlow.value.firstOrNull { it.id == threadId }
            ?: return Result.failure(IllegalArgumentException("Thread not found"))

        val newMessage = Message(
            id = "msg-${System.currentTimeMillis()}",
            senderId = thread.buyerId,
            senderName = thread.buyerName,
            text = text,
            createdAt = Timestamp.now()
        )

        messagesByThread.update { current ->
            val existing = current[threadId] ?: emptyList()
            current + (threadId to (existing + newMessage))
        }

        threadsFlow.update { current ->
            current.map { t ->
                if (t.id == threadId) {
                    t.copy(
                        lastMessage = text,
                        lastUpdated = Timestamp.now(),
                        unreadUserIds = (t.unreadUserIds + t.sellerId).distinct()
                    )
                } else t
            }
        }

        return Result.success(Unit)
    }

    override suspend fun markThreadRead(threadId: String, userId: String): Result<Unit> {
        markedReadCalls.add(threadId to userId)
        threadsFlow.update { current ->
            current.map { t ->
                if (t.id == threadId) {
                    t.copy(unreadUserIds = t.unreadUserIds - userId)
                } else t
            }
        }
        return Result.success(Unit)
    }

    override suspend fun getOrCreateThread(listing: Listing, buyer: User): Result<String> {
        val existing = threadsFlow.value.firstOrNull { it.listingId == listing.id && it.buyerId == buyer.id }
        if (existing != null) return Result.success(existing.id)

        val newId = "thread-${System.currentTimeMillis()}"
        val newThread = Thread(
            id = newId,
            listingId = listing.id,
            listingTitle = listing.title,
            buyerId = buyer.id,
            sellerId = listing.sellerId,
            buyerName = buyer.displayName,
            sellerName = listing.sellerName,
            buyerCampus = buyer.campus,
            sellerCampus = listing.campus,
            lastMessage = "",
            lastUpdated = Timestamp.now(),
            unreadUserIds = emptyList()
        )
        threadsFlow.update { it + newThread }
        return Result.success(newId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxChatUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeChatAuthRepository
    private lateinit var chatRepository: FakeChatRepository

    private val testBuyer = User(
        id = "buyer-1",
        displayName = "Marcus K.",
        campus = "Brooklyn College",
        major = "Biology"
    )

    private val thread1 = Thread(
        id = "thread-1",
        listingId = "item-101",
        listingTitle = "Organic Chemistry Textbook",
        buyerId = "buyer-1",
        sellerId = "seller-1",
        buyerName = "Marcus K.",
        sellerName = "Alice S.",
        buyerCampus = "Brooklyn College",
        sellerCampus = "Baruch College",
        lastMessage = "Is this still available?",
        lastUpdated = Timestamp(1000, 0),
        unreadUserIds = listOf("buyer-1") // Unread for buyer
    )

    private val thread2 = Thread(
        id = "thread-2",
        listingId = "item-102",
        listingTitle = "Graphing Calculator",
        buyerId = "buyer-1",
        sellerId = "seller-2",
        buyerName = "Marcus K.",
        sellerName = "Bob M.",
        buyerCampus = "Brooklyn College",
        sellerCampus = "Hunter College",
        lastMessage = "Can we meet at the safe zone?",
        lastUpdated = Timestamp(2000, 0), // Newer!
        unreadUserIds = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeChatAuthRepository(testBuyer)
        chatRepository = FakeChatRepository(listOf(thread1, thread2))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun inbox_displays_threads_ordered_by_time() = runTest(testDispatcher) {
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()

        val threads = viewModel.uiState.value.threads
        assertEquals(2, threads.size)
        // thread-2 has timestamp 2000, thread-1 has timestamp 1000 -> thread-2 should be first!
        assertEquals("thread-2", threads[0].id)
        assertEquals("thread-1", threads[1].id)
    }

    @Test
    fun unread_badge_reflects_actual_unreads() = runTest(testDispatcher) {
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()

        // Only thread-1 has "buyer-1" in unreadUserIds
        assertEquals(1, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun mark_thread_read_removes_user_from_unread_list() = runTest(testDispatcher) {
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.unreadCount)

        viewModel.markThreadRead("thread-1")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertFalse(viewModel.uiState.value.threads.first { it.id == "thread-1" }.unreadUserIds.contains("buyer-1"))
    }

    @Test
    fun empty_inbox_displays_zero_state() = runTest(testDispatcher) {
        val emptyRepo = FakeChatRepository(emptyList())
        val viewModel = InboxViewModel(emptyRepo, authRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.threads.isEmpty())
        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun load_threads_error_and_retry_recovers() = runTest(testDispatcher) {
        chatRepository.shouldThrowOnThreads = true
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoadError)
        assertNotNull(viewModel.uiState.value.errorMessage)

        chatRepository.shouldThrowOnThreads = false
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadError)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.threads.size)
    }

    @Test
    fun thread_error_then_sign_out_clears_state() = runTest(testDispatcher) {
        chatRepository.shouldThrowOnThreads = true
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoadError)

        authRepository.signOut()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.threads.isEmpty())
        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertFalse(viewModel.uiState.value.isLoadError)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun chat_streams_messages_and_populates_counterpart_info() = runTest(testDispatcher) {
        val message = Message(
            id = "msg-1",
            senderId = "seller-1",
            senderName = "Alice S.",
            text = "Yes, it is available! Let me know when you are free.",
            createdAt = Timestamp(1500, 0)
        )
        chatRepository.messagesByThread.value = mapOf("thread-1" to listOf(message))

        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        advanceUntilIdle()

        val state = chatViewModel.uiState.value
        assertEquals("Alice S.", state.counterpartName)
        assertEquals("Baruch College", state.counterpartCampus)
        assertEquals(1, state.messages.size)
        assertEquals("Yes, it is available! Let me know when you are free.", state.messages[0].text)
    }

    @Test
    fun sending_message_clears_draft_and_updates_thread() = runTest(testDispatcher) {
        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        advanceUntilIdle()

        chatViewModel.onDraftChanged("Can you meet at Baruch library tomorrow at 2pm?")
        assertEquals("Can you meet at Baruch library tomorrow at 2pm?", chatViewModel.uiState.value.draftText)

        chatViewModel.sendMessage()
        advanceUntilIdle()

        // Draft should be cleared
        assertEquals("", chatViewModel.uiState.value.draftText)
        assertFalse(chatViewModel.uiState.value.isSending)

        // Message should be appended to messages
        val messages = chatViewModel.uiState.value.messages
        assertEquals(1, messages.size)
        assertEquals("Can you meet at Baruch library tomorrow at 2pm?", messages[0].text)

        // Parent thread should update lastMessage
        val updatedThread = chatRepository.threadsFlow.value.first { it.id == "thread-1" }
        assertEquals("Can you meet at Baruch library tomorrow at 2pm?", updatedThread.lastMessage)
    }

    @Test
    fun send_disabled_for_blank_text() = runTest(testDispatcher) {
        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        advanceUntilIdle()

        // Whitespace only
        chatViewModel.onDraftChanged("    ")
        chatViewModel.sendMessage()
        advanceUntilIdle()

        // No message sent
        assertEquals(0, chatViewModel.uiState.value.messages.size)
    }

    @Test
    fun message_length_limit_1000_chars() = runTest(testDispatcher) {
        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        advanceUntilIdle()

        val exact1000 = "a".repeat(1000)
        chatViewModel.onDraftChanged(exact1000)
        assertEquals(1000, chatViewModel.uiState.value.draftText.length)

        // Attempting to exceed 1000 chars is rejected
        val over1000 = "b".repeat(1005)
        chatViewModel.onDraftChanged(over1000)
        // Should remain at 1000
        assertEquals(1000, chatViewModel.uiState.value.draftText.length)
    }

    @Test
    fun send_message_failure_restores_draft() = runTest(testDispatcher) {
        chatRepository.shouldFailSend = true
        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        advanceUntilIdle()

        chatViewModel.onDraftChanged("Test message")
        chatViewModel.sendMessage()
        advanceUntilIdle()

        // Failed send restores draft and sets error
        assertEquals("Test message", chatViewModel.uiState.value.draftText)
        assertNotNull(chatViewModel.uiState.value.errorMessage)
        assertFalse(chatViewModel.uiState.value.isSending)
    }

    @Test
    fun opening_chat_auto_marks_thread_as_read() = runTest(testDispatcher) {
        // thread-1 has unreadUserIds containing buyer-1
        assertTrue(chatRepository.threadsFlow.value.first { it.id == "thread-1" }.unreadUserIds.contains("buyer-1"))

        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        chatViewModel.onScreenVisible()
        advanceUntilIdle()

        // thread-1 should have markedReadCalls recorded
        assertTrue(chatRepository.markedReadCalls.any { it.first == "thread-1" && it.second == "buyer-1" })
        assertFalse(chatRepository.threadsFlow.value.first { it.id == "thread-1" }.unreadUserIds.contains("buyer-1"))
    }

    @Test
    fun sign_out_resets_inbox_threads() = runTest(testDispatcher) {
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.threads.size)

        authRepository.signOut()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.threads.isEmpty())
        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun account_switching_without_null_resets_previous_threads() = runTest(testDispatcher) {
        val viewModel = InboxViewModel(chatRepository, authRepository)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.threads.size)

        // Switch to a new account with no threads
        val buyer2 = User(id = "buyer-2", displayName = "Chloe D.")
        authRepository.setUser(buyer2)
        advanceUntilIdle()

        // Should not retain buyer-1's threads
        assertTrue(viewModel.uiState.value.threads.isEmpty())
        assertEquals("buyer-2", viewModel.uiState.value.currentUserId)
    }

    @Test
    fun eventual_send_rejection_restores_draft() = runTest(testDispatcher) {
        val chatViewModel = ChatViewModel("thread-1", chatRepository, authRepository)
        advanceUntilIdle()

        chatViewModel.onDraftChanged("Queued offline message")
        chatViewModel.sendMessage()
        advanceUntilIdle()

        // Optimistically cleared
        assertEquals("", chatViewModel.uiState.value.draftText)
        assertFalse(chatViewModel.uiState.value.isSending)

        // Eventual failure reported
        chatRepository.lastOnEventualFailure?.invoke(RuntimeException("Offline write rejected"))
        advanceUntilIdle()

        // Restores draft and sets error
        assertEquals("Queued offline message", chatViewModel.uiState.value.draftText)
        assertNotNull(chatViewModel.uiState.value.errorMessage)
        assertTrue(chatViewModel.uiState.value.errorMessage!!.contains("Offline write rejected"))
    }
}
