package com.cuny.exchange.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ProfileUiState(
    val user: User? = null,
    val myListings: List<Listing> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadError: Boolean = false,
    val showDeleteDialogForId: String? = null,
    val pendingMutationListingIds: Set<String> = emptySet(),
    val errorMessage: String? = null
) {
    val activeCount: Int get() = myListings.count { it.status == "active" }
    val soldCount: Int get() = myListings.count { it.status == "sold" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val listingRepository: ListingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()
    private var loadProfileJob: Job? = null
    private var isSigningOut = false

    init {
        loadProfile()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, isLoadError = false, errorMessage = null) }
        loadProfile()
    }

    private fun loadProfile() {
        loadProfileJob?.cancel()
        loadProfileJob = viewModelScope.launch {
            try {
                authRepository.currentUserFlow
                    .filterNotNull()
                    .flatMapLatest { user ->
                        listingRepository.getMyListingsFlow(user.id).map { listings ->
                            user to listings
                        }
                    }
                    .collect { (user, listings) ->
                        _uiState.update {
                            it.copy(user = user, myListings = listings, isLoading = false, isLoadError = false)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadError = true,
                        errorMessage = e.localizedMessage ?: "Failed to load profile. Tap to retry."
                    )
                }
            }
        }
    }

    fun toggleListingStatus(listing: Listing) {
        if (isSigningOut || _uiState.value.pendingMutationListingIds.contains(listing.id)) return
        val newStatus = if (listing.status == "sold") "active" else "sold"
        _uiState.update { it.copy(pendingMutationListingIds = it.pendingMutationListingIds + listing.id) }
        val job = viewModelScope.launch {
            try {
                val result = listingRepository.updateListingStatus(listing.id, newStatus)
                if (result.isFailure) {
                    _uiState.update { it.copy(errorMessage = "Failed to update listing status") }
                }
            } finally {
                _uiState.update { it.copy(pendingMutationListingIds = it.pendingMutationListingIds - listing.id) }
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    fun requestDelete(listingId: String) {
        _uiState.update { it.copy(showDeleteDialogForId = listingId) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialogForId = null) }
    }

    fun confirmDelete() {
        val listingId = _uiState.value.showDeleteDialogForId ?: return
        if (isSigningOut || _uiState.value.pendingMutationListingIds.contains(listingId)) return
        _uiState.update {
            it.copy(showDeleteDialogForId = null, pendingMutationListingIds = it.pendingMutationListingIds + listingId)
        }
        val job = viewModelScope.launch {
            try {
                val result = listingRepository.deleteListing(listingId)
                if (result.isFailure) {
                    _uiState.update { it.copy(errorMessage = "Network required to delete listing. Please check connection.") }
                }
            } finally {
                _uiState.update { it.copy(pendingMutationListingIds = it.pendingMutationListingIds - listingId) }
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun signOut(onSignedOut: () -> Unit = {}) {
        if (isSigningOut) return
        isSigningOut = true
        viewModelScope.launch {
            withTimeoutOrNull(2000L) {
                activeJobs.toList().forEach { it.join() }
            }
            authRepository.signOut()
            onSignedOut()
        }
    }
}

class ProfileViewModelFactory(
    private val authRepository: AuthRepository,
    private val listingRepository: ListingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(authRepository, listingRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
