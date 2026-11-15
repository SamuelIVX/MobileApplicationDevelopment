package com.cuny.exchange.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.isCunyEmail
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class NeedsOnboarding(val uid: String, val email: String) : AuthState
    data class Authenticated(val user: User) : AuthState
}

data class SignInUiState(
    val email: String = "",
    val isEmailValid: Boolean = false,
    val isSendingLink: Boolean = false,
    val isVerifyingLink: Boolean = false,
    val emailError: String? = null,
    val dialogError: String? = null,
    val errorMessage: String? = null,
    val isLinkSent: Boolean = false,
    val lastDispatchedEmail: String = "",
    val pendingDeepLink: String? = null,
    val showCrossDeviceEmailPrompt: Boolean = false,
    val crossDeviceEmail: String = ""
) {
    val isLoading: Boolean get() = isSendingLink || isVerifyingLink
}

data class CheckEmailUiState(
    val email: String = "",
    val cooldownSeconds: Int = 0,
    val isResending: Boolean = false,
    val errorMessage: String? = null
)

data class OnboardingUiState(
    val displayName: String = "",
    val major: String = "",
    val selectedCampus: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
) {
    val canSubmit: Boolean
        get() = displayName.isNotBlank() &&
                major.isNotBlank() &&
                selectedCampus.isNotBlank() &&
                !isLoading
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _signInUiState = MutableStateFlow(SignInUiState())
    val signInUiState: StateFlow<SignInUiState> = _signInUiState.asStateFlow()

    private val _checkEmailUiState = MutableStateFlow(CheckEmailUiState())
    val checkEmailUiState: StateFlow<CheckEmailUiState> = _checkEmailUiState.asStateFlow()

    private val _onboardingUiState = MutableStateFlow(OnboardingUiState())
    val onboardingUiState: StateFlow<OnboardingUiState> = _onboardingUiState.asStateFlow()

    private var cooldownJob: Job? = null
    private var cooldownDeadlineMs: Long = 0L
    private var onboardingJob: Job? = null
    private var lastHandledDeepLink: String? = null
    private var queuedDeepLink: Pair<String, String?>? = null

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                if (user == null) {
                    _authState.value = AuthState.Unauthenticated
                } else if (!user.isVerified || user.campus.isBlank() || user.displayName.isBlank()) {
                    _authState.value = AuthState.NeedsOnboarding(
                        uid = user.id,
                        email = _signInUiState.value.lastDispatchedEmail.ifBlank { _signInUiState.value.email }
                    )
                } else {
                    _authState.value = AuthState.Authenticated(user)
                }
            }
        }
    }

    fun onEmailChanged(newEmail: String) {
        if (_signInUiState.value.isSendingLink) return
        val trimmed = newEmail.trim()
        val valid = isCunyEmail(trimmed)
        _signInUiState.update {
            it.copy(
                email = newEmail,
                isEmailValid = valid,
                emailError = if (newEmail.isNotBlank() && !valid) "Must end in .cuny.edu" else null
            )
        }
    }

    fun sendSignInLink() {
        val email = _signInUiState.value.email.trim()
        if (!isCunyEmail(email)) {
            _signInUiState.update { it.copy(emailError = "Must end in .cuny.edu") }
            return
        }

        viewModelScope.launch {
            _signInUiState.update { it.copy(isSendingLink = true, errorMessage = null) }
            val result = authRepository.sendSignInLink(email)
            result.fold(
                onSuccess = {
                    _signInUiState.update {
                        it.copy(
                            isSendingLink = false,
                            isLinkSent = true,
                            lastDispatchedEmail = email
                        )
                    }
                    _checkEmailUiState.update { it.copy(email = email) }
                    cooldownDeadlineMs = System.currentTimeMillis() + 60_000L
                    startCooldownTimer(60)
                },
                onFailure = { error ->
                    _signInUiState.update {
                        it.copy(
                            isSendingLink = false,
                            errorMessage = error.localizedMessage ?: "Network error. Please check your connection and retry."
                        )
                    }
                }
            )
        }
    }

    fun onLinkSentNavigated() {
        _signInUiState.update { it.copy(isLinkSent = false) }
    }

    fun resendSignInLink() {
        if (_checkEmailUiState.value.cooldownSeconds > 0) return

        val email = _checkEmailUiState.value.email.ifBlank { _signInUiState.value.lastDispatchedEmail }.trim()
        if (!isCunyEmail(email)) return

        viewModelScope.launch {
            _checkEmailUiState.update { it.copy(isResending = true, errorMessage = null) }
            val result = authRepository.sendSignInLink(email)
            result.fold(
                onSuccess = {
                    _checkEmailUiState.update { it.copy(isResending = false) }
                    cooldownDeadlineMs = System.currentTimeMillis() + 60_000L
                    startCooldownTimer(60)
                },
                onFailure = { error ->
                    _checkEmailUiState.update {
                        it.copy(
                            isResending = false,
                            errorMessage = error.localizedMessage ?: "Failed to resend sign-in link. Please retry."
                        )
                    }
                }
            )
        }
    }

    fun restoreCheckEmail(email: String) {
        val trimmed = email.trim()
        if (!isCunyEmail(trimmed)) return

        cooldownJob?.cancel()
        val remainingSeconds = if (cooldownDeadlineMs > 0L) {
            val diff = cooldownDeadlineMs - System.currentTimeMillis()
            if (diff > 0L) ((diff + 999L) / 1000L).toInt() else 0
        } else {
            _checkEmailUiState.value.cooldownSeconds
        }

        _checkEmailUiState.update {
            it.copy(
                email = trimmed,
                cooldownSeconds = remainingSeconds
            )
        }

        if (remainingSeconds > 0) {
            startCooldownTimer(remainingSeconds)
        }
    }

    fun startCooldownTimer(seconds: Int = 60) {
        cooldownJob?.cancel()
        _checkEmailUiState.update { it.copy(cooldownSeconds = seconds) }
        cooldownJob = viewModelScope.launch {
            var current = seconds
            while (current > 0) {
                delay(1000)
                current--
                _checkEmailUiState.update { it.copy(cooldownSeconds = current) }
            }
        }
    }

    fun onDeepLinkReceived(link: String, cachedEmail: String?) {
        val currentAuth = _authState.value
        if (currentAuth is AuthState.Authenticated || currentAuth is AuthState.NeedsOnboarding) {
            return
        }

        if (_signInUiState.value.isVerifyingLink) {
            queuedDeepLink = Pair(link, cachedEmail)
            return
        }

        if (link == lastHandledDeepLink) return

        if (!cachedEmail.isNullOrBlank() && isCunyEmail(cachedEmail)) {
            verifyEmailLink(cachedEmail, link)
        } else {
            lastHandledDeepLink = null
            _signInUiState.update {
                it.copy(
                    pendingDeepLink = link,
                    showCrossDeviceEmailPrompt = true,
                    crossDeviceEmail = "",
                    dialogError = null
                )
            }
        }
    }

    fun onCrossDeviceEmailChanged(email: String) {
        _signInUiState.update { it.copy(crossDeviceEmail = email, dialogError = null) }
    }

    fun submitCrossDeviceEmail() {
        val link = _signInUiState.value.pendingDeepLink ?: return
        val email = _signInUiState.value.crossDeviceEmail.trim()
        if (!isCunyEmail(email)) {
            _signInUiState.update { it.copy(dialogError = "Must end in .cuny.edu") }
            return
        }

        _signInUiState.update { it.copy(showCrossDeviceEmailPrompt = false, dialogError = null) }
        verifyEmailLink(email, link)
    }

    fun dismissCrossDevicePrompt() {
        lastHandledDeepLink = null
        _signInUiState.update {
            it.copy(
                showCrossDeviceEmailPrompt = false,
                pendingDeepLink = null,
                crossDeviceEmail = "",
                dialogError = null
            )
        }
    }

    fun verifyEmailLink(email: String, link: String) {
        lastHandledDeepLink = link

        viewModelScope.launch {
            _signInUiState.update { it.copy(isVerifyingLink = true, errorMessage = null) }
            _checkEmailUiState.update { it.copy(errorMessage = null) }
            val result = authRepository.verifyEmailLink(email, link)
            result.fold(
                onSuccess = { user ->
                    _signInUiState.update { it.copy(isVerifyingLink = false) }
                    if (user.campus.isBlank() || user.displayName.isBlank()) {
                        _authState.value = AuthState.NeedsOnboarding(uid = user.id, email = email)
                    } else {
                        _authState.value = AuthState.Authenticated(user)
                    }
                    checkQueuedLink()
                },
                onFailure = { error ->
                    lastHandledDeepLink = null
                    val errorMsg = error.localizedMessage
                        ?: "This sign-in link has expired or has already been used. Please request a new one."
                    _signInUiState.update {
                        it.copy(
                            isVerifyingLink = false,
                            errorMessage = errorMsg,
                            pendingDeepLink = link,
                            showCrossDeviceEmailPrompt = true,
                            dialogError = errorMsg
                        )
                    }
                    _checkEmailUiState.update { it.copy(errorMessage = errorMsg) }
                    checkQueuedLink()
                }
            )
        }
    }

    private fun checkQueuedLink() {
        val next = queuedDeepLink
        queuedDeepLink = null
        if (next != null && _authState.value !is AuthState.Authenticated) {
            onDeepLinkReceived(next.first, next.second)
        }
    }

    fun onDisplayNameChanged(name: String) {
        _onboardingUiState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun onMajorChanged(major: String) {
        _onboardingUiState.update { it.copy(major = major, errorMessage = null) }
    }

    fun onCampusSelected(campus: String) {
        _onboardingUiState.update { it.copy(selectedCampus = campus, errorMessage = null) }
    }

    fun onOnboardingNavigated() {
        _onboardingUiState.update { it.copy(isComplete = false) }
    }

    fun completeOnboarding(uid: String) {
        val state = _onboardingUiState.value
        if (!state.canSubmit) {
            _onboardingUiState.update { it.copy(errorMessage = "Please complete all fields.") }
            return
        }

        onboardingJob?.cancel()
        onboardingJob = viewModelScope.launch {
            _onboardingUiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = authRepository.saveUserProfile(
                userId = uid,
                displayName = state.displayName.trim(),
                campus = state.selectedCampus.trim(),
                major = state.major.trim()
            )
            result.fold(
                onSuccess = { savedUser ->
                    if (authRepository.currentUserId == uid) {
                        _onboardingUiState.update { it.copy(isLoading = false, isComplete = true) }
                        _authState.value = AuthState.Authenticated(savedUser)
                    } else {
                        _onboardingUiState.update {
                            it.copy(isLoading = false, errorMessage = "Session changed. Please sign in again.")
                        }
                    }
                },
                onFailure = { error ->
                    _onboardingUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to save profile. Please retry."
                        )
                    }
                }
            )
        }
    }

    fun signOut() {
        onboardingJob?.cancel()
        onboardingJob = null
        viewModelScope.launch {
            authRepository.signOut()
            lastHandledDeepLink = null
            queuedDeepLink = null
            _signInUiState.value = SignInUiState()
            _checkEmailUiState.value = CheckEmailUiState()
            _onboardingUiState.value = OnboardingUiState()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearErrorMessages() {
        _signInUiState.update { it.copy(errorMessage = null) }
        _checkEmailUiState.update { it.copy(errorMessage = null) }
        _onboardingUiState.update { it.copy(errorMessage = null) }
    }
}
