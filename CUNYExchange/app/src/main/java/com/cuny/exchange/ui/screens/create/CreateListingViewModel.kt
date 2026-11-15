package com.cuny.exchange.ui.screens.create

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Condition
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreateListingUiState(
    val title: String = "",
    val priceText: String = "",
    val condition: String = Condition.GOOD.label,
    val campus: String = "",
    val category: String = Category.TEXTBOOKS.label,
    val description: String = "",
    val photoUris: List<Uri> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val parsedPrice: Double? get() {
        val trimmed = priceText.trim().removePrefix("$").trim()
        if (!trimmed.matches(Regex("^\\d+(\\.\\d{1,2})?$"))) return null
        val parsed = trimmed.toDoubleOrNull() ?: return null
        return if (parsed.isFinite() && parsed >= 0.0) parsed else null
    }

    val isPublishEnabled: Boolean
        get() = title.trim().isNotEmpty() &&
                parsedPrice != null &&
                parsedPrice!!.isFinite() &&
                parsedPrice!! >= 0.0 &&
                campus.trim().isNotEmpty() &&
                photoUris.size <= 5 &&
                !isLoading
}

class CreateListingViewModel(
    private val listingRepository: ListingRepository,
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateListingUiState())
    val uiState: StateFlow<CreateListingUiState> = _uiState.asStateFlow()

    private val _publishSuccessEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val publishSuccessEvents: SharedFlow<Unit> = _publishSuccessEvents.asSharedFlow()

    private var publishJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                if (user == null) {
                    publishJob?.cancel()
                    _uiState.value = CreateListingUiState()
                } else {
                    _uiState.update { current ->
                        if (current.campus.isBlank()) current.copy(campus = user.campus) else current
                    }
                }
            }
        }
    }

    fun onTitleChanged(title: String) {
        if (title.length <= 100) {
            _uiState.update { it.copy(title = title, errorMessage = null) }
        }
    }

    fun onPriceChanged(priceText: String) {
        _uiState.update { it.copy(priceText = priceText, errorMessage = null) }
    }

    fun onConditionSelected(condition: String) {
        _uiState.update { it.copy(condition = condition) }
    }

    fun onCampusSelected(campus: String) {
        _uiState.update { it.copy(campus = campus) }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onDescriptionChanged(description: String) {
        if (description.length <= 1000) {
            _uiState.update { it.copy(description = description, errorMessage = null) }
        }
    }

    fun onPhotosSelected(uris: List<Uri>) {
        _uiState.update { current ->
            val merged = (current.photoUris + uris).take(5)
            current.copy(photoUris = merged, errorMessage = null)
        }
    }

    fun removePhoto(index: Int) {
        _uiState.update { current ->
            if (index in current.photoUris.indices) {
                val updated = current.photoUris.toMutableList().apply { removeAt(index) }
                current.copy(photoUris = updated)
            } else {
                current
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun publishListing(onSuccess: (() -> Unit)? = null) {
        val state = _uiState.value
        if (!state.isPublishEnabled || state.isLoading) return

        val currentUserId = authRepository.currentUserId
        if (currentUserId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "User not authenticated") }
            return
        }

        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            var uploadedUrls: List<String> = emptyList()
            var isListingCommitted = false
            var hasListingWriteStarted = false

            try {
                uploadedUrls = storageRepository.uploadPhotos(state.photoUris)

                if (authRepository.currentUserId != currentUserId) {
                    throw IllegalStateException("Authentication session changed during upload")
                }

                val newListing = Listing(
                    title = state.title.trim(),
                    price = state.parsedPrice!!,
                    condition = state.condition,
                    category = state.category,
                    campus = state.campus.trim(),
                    description = state.description.trim(),
                    photoUrls = uploadedUrls,
                    status = "active"
                )

                val result = listingRepository.createListing(
                    listing = newListing,
                    onWriteStarted = { hasListingWriteStarted = true }
                )
                result.getOrThrow()
                isListingCommitted = true
                _uiState.value = CreateListingUiState(campus = state.campus)
                _publishSuccessEvents.tryEmit(Unit)
            } catch (e: CancellationException) {
                if (uploadedUrls.isNotEmpty() && !hasListingWriteStarted) {
                    withContext(NonCancellable) {
                        runCatching { storageRepository.deletePhotos(uploadedUrls) }
                    }
                }
                throw e
            } catch (e: Exception) {
                if (uploadedUrls.isNotEmpty() && !isListingCommitted) {
                    withContext(NonCancellable) {
                        runCatching { storageRepository.deletePhotos(uploadedUrls) }
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to publish listing"
                    )
                }
                return@launch
            }

            // Invoked outside the persistence rollback block
            try {
                onSuccess?.invoke()
            } catch (_: Exception) {
                // UI navigation failure does not invalidate committed listing
            }
        }
    }
}

class CreateListingViewModelFactory(
    private val listingRepository: ListingRepository,
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateListingViewModel::class.java)) {
            return CreateListingViewModel(listingRepository, storageRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
