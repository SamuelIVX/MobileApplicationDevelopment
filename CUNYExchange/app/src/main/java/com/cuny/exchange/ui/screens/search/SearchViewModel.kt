package com.cuny.exchange.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.PreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedCampus: String = "All campuses",
    val under100Only: Boolean = false,
    val selectedCategory: String = "All",
    val results: List<Listing> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isSearchActive: Boolean
        get() = query.isNotBlank() || selectedCampus != "All campuses" || under100Only || selectedCategory != "All"

    val resultsCountLabel: String
        get() {
            val count = results.size
            val itemWord = if (count == 1) "result" else "results"
            val queryPart = if (query.isNotBlank()) " for \"${query.trim()}\"" else ""
            val campusPart = if (selectedCampus == "All campuses") " across CUNY" else " at $selectedCampus"
            return "$count $itemWord$queryPart$campusPart"
        }
}

class SearchViewModel(
    private val repository: ListingRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                preferencesRepository.recentSearchesFlow.collect { recent ->
                    _uiState.update { it.copy(recentSearches = recent) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore listener or storage read errors
            }
        }

        viewModelScope.launch {
            try {
                repository.getActiveCategoryCountsFlow().collect { counts ->
                    _uiState.update { it.copy(categoryCounts = counts) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore listener or network errors for category counts
            }
        }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        executeSearch(debounce = true)
    }

    fun onCampusSelected(campus: String) {
        _uiState.update { it.copy(selectedCampus = campus) }
        executeSearch(debounce = false)
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        executeSearch(debounce = false)
    }

    fun toggleUnder100() {
        _uiState.update { it.copy(under100Only = !it.under100Only) }
        executeSearch(debounce = false)
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                query = "",
                selectedCampus = "All campuses",
                under100Only = false,
                selectedCategory = "All",
                errorMessage = null
            )
        }
        executeSearch(debounce = false)
    }

    fun retry() {
        executeSearch(debounce = false)
    }

    private fun executeSearch(debounce: Boolean) {
        searchJob?.cancel()
        val state = _uiState.value

        if (!state.isSearchActive) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, errorMessage = null) }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val currentState = _uiState.value
                val items = repository.searchListings(
                    query = currentState.query.trim(),
                    campus = currentState.selectedCampus,
                    under100 = currentState.under100Only,
                    category = currentState.selectedCategory
                )

                if (isActive) {
                    val queryTrimmed = currentState.query.trim()
                    if (queryTrimmed.isNotBlank()) {
                        val currentRecent = currentState.recentSearches
                        val updatedRecent = (listOf(queryTrimmed) + currentRecent.filter { it != queryTrimmed }).take(5)
                        preferencesRepository.saveRecentSearches(updatedRecent)
                    }

                    _uiState.update {
                        it.copy(results = items, isLoading = false, errorMessage = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    _uiState.update {
                        it.copy(
                            results = emptyList(),
                            isLoading = false,
                            errorMessage = e.localizedMessage ?: "Search failed. Tap to retry."
                        )
                    }
                }
            }
        }
    }
}

class SearchViewModelFactory(
    private val repository: ListingRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(repository, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
