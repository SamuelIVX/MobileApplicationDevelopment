package com.cuny.exchange.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val threads: List<Thread> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = true,
    val isLoadError: Boolean = false,
    val errorMessage: String? = null
) {
    val unreadCount: Int get() = threads.count { it.unreadUserIds.contains(currentUserId) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var loadThreadsJob: Job? = null

    init {
        loadThreads()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, isLoadError = false, errorMessage = null) }
        loadThreads()
    }

    private fun loadThreads() {
        loadThreadsJob?.cancel()
        loadThreadsJob = viewModelScope.launch {
            try {
                authRepository.currentUserFlow
                    .flatMapLatest { user ->
                        if (user == null) {
                            _uiState.update { InboxUiState(isLoading = false) }
                            flowOf(null to emptyList<Thread>())
                        } else {
                            val activeUserId = user.id
                            _uiState.update { current ->
                                if (current.currentUserId != activeUserId) {
                                    InboxUiState(currentUserId = activeUserId, isLoading = true)
                                } else {
                                    current.copy(currentUserId = activeUserId)
                                }
                            }
                            try {
                                chatRepository.getThreadsFlow(activeUserId)
                                    .map { threads -> activeUserId to threads }
                                    .catch { e ->
                                        if (e is CancellationException) throw e
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                isLoadError = true,
                                                errorMessage = e.localizedMessage ?: "Failed to load messages. Please check connection."
                                            )
                                        }
                                    }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isLoadError = true,
                                        errorMessage = e.localizedMessage ?: "Failed to load messages. Please check connection."
                                    )
                                }
                                emptyFlow()
                            }
                        }
                    }
                    .collect { (userId, threads) ->
                        if (userId != null && _uiState.value.currentUserId == userId && !_uiState.value.isLoadError) {
                            _uiState.update {
                                it.copy(threads = threads, isLoading = false, isLoadError = false, errorMessage = null)
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadError = true,
                        errorMessage = e.localizedMessage ?: "Failed to load messages. Please check connection."
                    )
                }
            }
        }
    }

    fun markThreadRead(threadId: String) {
        val currentUserId = _uiState.value.currentUserId
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.markThreadRead(threadId, currentUserId)
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class InboxViewModelFactory(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
            return InboxViewModel(chatRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
