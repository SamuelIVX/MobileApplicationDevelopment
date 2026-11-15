package com.cuny.exchange.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Message
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val thread: Thread? = null,
    val currentUserId: String = "",
    val counterpartName: String = "",
    val counterpartCampus: String = "",
    val messages: List<Message> = emptyList(),
    val draftText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val threadId: String,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var isScreenVisible = false
    private var isMarkingRead = false
    private var readCheckPending = false

    init {
        viewModelScope.launch {
            try {
                authRepository.currentUserFlow.filterNotNull().collectLatest { user ->
                    _uiState.update { it.copy(currentUserId = user.id) }
                    chatRepository.getThreadFlow(threadId).collect { thread ->
                        if (thread != null) {
                            val isBuyer = user.id == thread.buyerId
                            val counterpartName = if (isBuyer) thread.sellerName else thread.buyerName
                            val counterpartCampus = if (isBuyer) thread.sellerCampus else thread.buyerCampus
                            _uiState.update {
                                it.copy(
                                    thread = thread,
                                    counterpartName = counterpartName.ifBlank { "Student" },
                                    counterpartCampus = counterpartCampus
                                )
                            }
                            if (isScreenVisible && thread.unreadUserIds.contains(user.id)) {
                                markReadAsync(user.id)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to load chat thread") }
            }
        }

        viewModelScope.launch {
            var retryCount = 0
            val maxRetries = 3
            while (retryCount <= maxRetries) {
                try {
                    chatRepository.getMessagesFlow(threadId).collect { msgs ->
                        retryCount = 0
                        _uiState.update { it.copy(messages = msgs) }
                        val current = _uiState.value
                        val currentUserId = current.currentUserId
                        if (isScreenVisible && currentUserId.isNotBlank() && current.thread?.unreadUserIds?.contains(currentUserId) == true) {
                            markReadAsync(currentUserId)
                        }
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    retryCount++
                    _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to stream messages") }
                    if (retryCount <= maxRetries) {
                        delay(1000L * retryCount)
                    }
                }
            }
        }
    }

    private fun markReadAsync(userId: String) {
        if (isMarkingRead) {
            readCheckPending = true
            return
        }
        isMarkingRead = true
        viewModelScope.launch {
            try {
                var attempts = 0
                while (attempts < 2) {
                    val result = chatRepository.markThreadRead(threadId, userId)
                    if (result.isSuccess) break
                    attempts++
                    if (attempts < 2) delay(500L)
                }
            } finally {
                isMarkingRead = false
                if (readCheckPending) {
                    readCheckPending = false
                    val current = _uiState.value
                    if (isScreenVisible && current.thread?.unreadUserIds?.contains(userId) == true) {
                        markReadAsync(userId)
                    }
                }
            }
        }
    }

    fun onScreenVisible() {
        isScreenVisible = true
        val current = _uiState.value
        val currentUserId = current.currentUserId
        if (currentUserId.isNotBlank() && current.thread?.unreadUserIds?.contains(currentUserId) == true) {
            markReadAsync(currentUserId)
        }
    }

    fun onScreenHidden() {
        isScreenVisible = false
    }

    fun onDraftChanged(text: String) {
        if (text.length <= 1000) {
            _uiState.update { it.copy(draftText = text) }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.draftText.trim()
        if (text.isEmpty() || state.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, draftText = "") }
            try {
                val result = chatRepository.sendMessage(threadId, text) { error ->
                    _uiState.update { current ->
                        val restoredDraft = if (current.draftText.isBlank()) text else "$text ${current.draftText}".take(1000)
                        current.copy(
                            draftText = restoredDraft,
                            errorMessage = "Message failed to send: ${error.localizedMessage ?: "Offline send rejected"}"
                        )
                    }
                }
                if (result.isFailure) {
                    _uiState.update { current ->
                        val restoredDraft = if (current.draftText.isBlank()) text else "$text ${current.draftText}".take(1000)
                        current.copy(draftText = restoredDraft, errorMessage = "Failed to send message. Please retry.")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    val restoredDraft = if (current.draftText.isBlank()) text else "$text ${current.draftText}".take(1000)
                    current.copy(draftText = restoredDraft, errorMessage = "Failed to send message: ${e.localizedMessage}")
                }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class ChatViewModelFactory(
    private val threadId: String,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(threadId, chatRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
