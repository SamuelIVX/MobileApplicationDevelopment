package com.cuny.exchange.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentUser: User? = null,
    val selectedCategory: Category = Category.ALL,
    val campusListings: List<Listing> = emptyList(),
    val cunyListings: List<Listing> = emptyList(),
    val savedListingIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(Category.ALL)
    val selectedCategory: StateFlow<Category> = _selectedCategory.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            authRepository.currentUserFlow
                .flatMapLatest { user ->
                    if (user == null) {
                        flowOf(
                            HomeUiState(
                                currentUser = null,
                                selectedCategory = Category.ALL,
                                campusListings = emptyList(),
                                cunyListings = emptyList(),
                                savedListingIds = emptySet(),
                                isLoading = false,
                                errorMessage = null
                            )
                        )
                    } else {
                        _selectedCategory.flatMapLatest { category ->
                            combine(
                                listingRepository.getListingsFlow(category),
                                listingRepository.getSavedListingIdsFlow(user.id)
                            ) { allListings, savedIds ->
                                val userCampus = user.campus.trim()
                                val local = if (userCampus.isNotEmpty()) {
                                    allListings.filter { it.campus.equals(userCampus, ignoreCase = true) }
                                } else {
                                    emptyList()
                                }
                                val acrossCuny = if (userCampus.isNotEmpty()) {
                                    allListings.filter { !it.campus.equals(userCampus, ignoreCase = true) }
                                } else {
                                    allListings
                                }

                                HomeUiState(
                                    currentUser = user,
                                    selectedCategory = category,
                                    campusListings = local,
                                    cunyListings = acrossCuny,
                                    savedListingIds = savedIds,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }.catch { e ->
                                emit(
                                    HomeUiState(
                                        currentUser = user,
                                        selectedCategory = category,
                                        campusListings = emptyList(),
                                        cunyListings = emptyList(),
                                        savedListingIds = emptySet(),
                                        isLoading = false,
                                        errorMessage = e.message ?: "Failed to load feed"
                                    )
                                )
                            }
                        }
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
    }

    fun toggleSave(listingId: String, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = listingRepository.toggleBookmark(listingId)
            result.onFailure { error ->
                val message = error.localizedMessage ?: "Failed to update bookmark"
                _uiState.update {
                    it.copy(errorMessage = message)
                }
                onError?.invoke(message)
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadHomeData()
    }
}

class HomeViewModelFactory(
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(listingRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
