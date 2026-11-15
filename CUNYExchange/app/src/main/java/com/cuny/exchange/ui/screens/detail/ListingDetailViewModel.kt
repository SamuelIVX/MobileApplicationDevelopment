package com.cuny.exchange.ui.screens.detail

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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListingDetailUiState(
    val listing: Listing? = null,
    val currentUser: User? = null,
    val sellerProfile: User? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isDeleted: Boolean = false
) {
    val isOwner: Boolean get() = listing != null && currentUser != null && listing.sellerId == currentUser.id
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListingDetailViewModel(
    val listingId: String,
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListingDetailUiState())
    val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var cachedSellerId: String? = null
    private var cachedSellerProfile: User? = null

    init {
        loadListingDetails()
    }

    fun retry() {
        loadListingDetails()
    }

    fun loadListingDetails() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                authRepository.currentUserFlow
                    .distinctUntilChanged()
                    .flatMapLatest { user ->
                        val userId = user?.id.orEmpty()
                        combine(
                            listingRepository.getListingById(listingId)
                                .catch { error ->
                                    if (error is CancellationException) throw error
                                    emit(null)
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            errorMessage = error.localizedMessage ?: "Failed to load listing"
                                        )
                                    }
                                },
                            listingRepository.isBookmarkedFlow(userId, listingId)
                                .catch { emit(false) }
                        ) { listing, isSaved ->
                            val wasDeleted = _uiState.value.isDeleted
                            if (listing == null) {
                                ListingDetailUiState(
                                    listing = null,
                                    currentUser = user,
                                    sellerProfile = null,
                                    isSaved = false,
                                    isLoading = false,
                                    errorMessage = if (wasDeleted) null else (_uiState.value.errorMessage ?: "This listing is no longer available"),
                                    isDeleted = wasDeleted
                                )
                            } else {
                                val seller = when {
                                    listing.sellerId == user?.id -> user
                                    listing.sellerId == cachedSellerId && cachedSellerProfile != null -> cachedSellerProfile
                                    else -> {
                                        val fetched = authRepository.getUserProfile(listing.sellerId).getOrNull()
                                        if (fetched != null) {
                                            cachedSellerId = listing.sellerId
                                            cachedSellerProfile = fetched
                                        }
                                        fetched ?: cachedSellerProfile
                                    }
                                }
                                ListingDetailUiState(
                                    listing = listing,
                                    currentUser = user,
                                    sellerProfile = seller,
                                    isSaved = isSaved,
                                    isLoading = false,
                                    errorMessage = null,
                                    isDeleted = wasDeleted
                                )
                            }
                        }
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Network error. Please try again."
                            )
                        }
                    }
                    .collect { state ->
                        _uiState.value = state
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load listing"
                    )
                }
            }
        }
    }

    fun toggleSave() {
        viewModelScope.launch {
            val result = listingRepository.toggleBookmark(listingId)
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to update saved item") }
            }
        }
    }

    fun markAsSold() {
        viewModelScope.launch {
            val result = listingRepository.updateListingStatus(listingId, "sold")
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to mark listing as sold") }
            }
        }
    }

    fun markAsActive() {
        viewModelScope.launch {
            val result = listingRepository.updateListingStatus(listingId, "active")
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to mark listing as active") }
            }
        }
    }

    fun deleteListing(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = listingRepository.deleteListing(listingId)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeleted = true) }
                    onDeleted()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to delete listing") }
                }
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class ListingDetailViewModelFactory(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ListingDetailViewModel::class.java)) {
            return ListingDetailViewModel(listingId, listingRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
