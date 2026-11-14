# Spec: Search & Filtering

## Applicable Skills
- `mobile-android-design`: Jetpack Compose Material 3 SearchBar, filter chips, category discovery grids, responsive keyboard actions.
- `better-accessibility`: Accessible search input, screen reader announcements for search result counts, minimum 48dp touch targets on chips.
- `codebase-design`: Reactive search pipeline with coroutine debounce, job cancellation, repository abstraction.
- `tdd`: Testing query debounce, multi-filter combinations, recent search persistence, and edge-case query parsing.

## Objective
Implement the search and discovery experience: `SearchScreen` (Tab 2 in bottom navigation).
Students can search listings by keyword, filter by specific CUNY campus, toggle quick price
filters ("Under $100"), browse category tiles with live item counts, and tap historical
recent search chips (`TI-84`, `Chem 101`, `Dorm fridge`). The search engine operates dynamically
with client-side debounce and live Firestore querying.

## Scope
- Package: `com.cuny.exchange.ui.screens.search`
- Modifies (creates):
  - `ui/screens/search/SearchScreen.kt`
  - `ui/screens/search/SearchViewModel.kt`
  - `ui/components/SearchBar.kt` — keyword input with clear action
  - `ui/components/RecentSearchesRow.kt` — recent query chips
  - `ui/components/CategoryTileGrid.kt` — 2x3 category discovery tiles
- Modifies (existing):
  - `data/repository/ListingRepository.kt` — adds `searchListings(query, campus, under100, category)`
- Off-limits: `04-home-feed` (feed list); `06-listing-detail` (detail view).

## Non-Goals
- No backend Elasticsearch or Algolia cluster (Firestore prefix queries and client-side filtering are sufficient for coursework scale).
- No search query spelling correction / fuzzy matching.

## Invariants
- Search queries MUST be case-insensitive. [search invariant]
- When `under100` toggle is active, only items with `price < 100.0` SHALL be returned. [Oracle 2 / wireframe invariant]
- When a campus filter is selected (e.g. `Baruch College`), only items matching that exact campus SHALL be returned; when `All campuses` is selected, all CUNY institutions are included. [campus filter invariant]
- Recent search chips MUST update when a search is executed, capped at 5 distinct non-empty queries. [storage invariant]
- Typing in the search bar MUST debounce queries by 300ms before dispatching search requests. [network conservation invariant]

## Requirements
1. THE SYSTEM SHALL display an outlined `SearchBar` at the top of the screen with placeholder "Search textbooks, calculators, clothes…", leading search icon, and a clear (✕) icon when text is non-empty.
2. WHEN the student types in the search bar, THE SYSTEM SHALL debounce the query by 300ms before triggering the search routine.
3. THE SYSTEM SHALL provide a Campus Filter dropdown allowing selection between `All campuses` and individual CUNY colleges.
4. THE SYSTEM SHALL provide a quick filter toggle chip: "Under $100".
   - When active: highlighted in CUNY Blue ground (`#E6EEFA`) with blue border (`#0033A1`)
   - Filters out all listings where `price >= 100.0`.
5. WHEN the search query is blank and all filters are reset, THE SYSTEM SHALL display the discovery layout:
   - Header: "What do you need this semester?"
   - **Recent Searches row:** horizontally scrollable chips containing up to 5 recent queries (e.g. `TI-84`, `Chem 101`, `Dorm fridge`). Tapping a chip populates the query.
   - **Category Tiles (2-column grid):** visual tiles for `Textbooks`, `Calculators`, `Electronics`, `Clothing`, `Furniture`, `Other`, each displaying category name and active listing count. Tapping a tile initiates a search filtered by that category.
6. WHEN a search query or filter is active, THE SYSTEM SHALL display a live results summary label:
   - e.g. "3 results for 'Calculator' across CUNY" or "1 result for 'TI-84' at Baruch College".
7. THE SYSTEM SHALL render matching items in a vertical list using `ListingCard`.
8. WHEN no items match the query and filter combination, THE SYSTEM SHALL display a zero-state:
   - "No listings found matching your search."
   - A button to "Clear all filters".
9. Tapping any result card SHALL navigate to `ListingDetailScreen(listingId)`.

## Failure Modes & Edge Cases
1. **Empty Search Results:** WHEN no listings match the search criteria, THE SYSTEM SHALL display an informative zero-state illustration with an actionable "Clear all filters" button rather than a blank screen.
2. **Rapid Typing / Out-of-Order Responses:** WHEN the student types quickly, in-flight coroutine search jobs SHALL be cancelled (`searchJob?.cancel()`) prior to launching the new search to prevent race conditions.
3. **Special Characters & Whitespace:** WHEN queries contain special characters (quotes, slashes, punctuation) or leading/trailing whitespace, THE SYSTEM SHALL trim whitespace and sanitize strings before querying.
4. **Offline Searching:** WHEN the device is offline, search queries SHALL evaluate against Firestore's local cache and return cached matching results.

## Acceptance Criteria
- [ ] Typing "Calculator" returns listings with "Calculator" in title or description (case-insensitive).
- [ ] Selecting "Baruch College" excludes items from all other campuses.
- [ ] Toggling "Under $100" filters out items with price >= $100.0.
- [ ] Tapping a recent search chip populates the search bar and executes the search.
- [ ] Clicking "Clear all filters" resets query, campus filter, and under $100 toggle to default discovery state.

## Design

### `ui/screens/search/SearchViewModel.kt`
```kotlin
package com.cuny.exchange.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.repository.ListingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedCampus: String = "All campuses",
    val under100Only: Boolean = false,
    val selectedCategory: String = "All",
    val results: List<Listing> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false
) {
    val isSearchActive: Boolean get() = query.isNotBlank() || selectedCampus != "All campuses" || under100Only || selectedCategory != "All"
    val resultsCountLabel: String get() {
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

    init {
        viewModelScope.launch {
            preferencesRepository.recentSearchesFlow.collect { recent ->
                _uiState.update { it.copy(recentSearches = recent) }
            }
        }
    }

    private var searchJob: Job? = null

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
            it.copy(query = "", selectedCampus = "All campuses", under100Only = false, selectedCategory = "All", errorMessage = null)
        }
        executeSearch(debounce = false)
    }

    private fun executeSearch(debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            val state = _uiState.value
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val items = repository.searchListings(
                    query = state.query.trim(),
                    campus = state.selectedCampus,
                    under100 = state.under100Only,
                    category = state.selectedCategory
                )
                if (isActive) {
                    val queryTrimmed = state.query.trim()
                    if (queryTrimmed.isNotBlank()) {
                        val updatedRecent = (listOf(queryTrimmed) + state.recentSearches.filter { it != queryTrimmed }).take(5)
                        preferencesRepository.saveRecentSearches(updatedRecent)
                    }

                    _uiState.update {
                        it.copy(results = items, isLoading = false, errorMessage = null)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    _uiState.update {
                        it.copy(results = emptyList(), isLoading = false, errorMessage = "Search failed. Tap to retry.")
                    }
                }
            }
        }
    }
}
```

## Current State
- `[verified]` `SearchScreen` is greenfield.
- `[verified]` Wireframe specifies keyword debounce, campus filter, and quick "$100" toggle.

## Tests
- `search_filters_by_keyword (R1)` — typing "Calculator" returns items containing "Calculator" in title.
- `search_filters_by_campus (R3)` — selecting "Baruch College" excludes items from Hunter or Brooklyn College.
- `under_100_filter_excludes_expensive_items (R4)` — with `under100 = true`, a $120 item is filtered out. Mutant check: `< 100.0` vs `<= 100.0` (ensure $100.00 is excluded).
- `recent_searches_capped_at_five (R5)` — executing 6 distinct searches keeps exactly the 5 most recent. Mutant check: `.take(5)`.
- `tap_recent_search_populates_query (R5)` — tapping a chip populates `query` and launches search.
- `clear_filters_resets_state (R8)` — tapping "Clear all filters" resets all state variables.

## Constraints
- Dependencies: `02-data-model` and `04-home-feed` must merge first.
- Downstream: `06-listing-detail` drill-in is launched from result cards.
- Oracle Contract: Set-level Oracle 2 (Price Rules) in `README.md`.

## Context
- Jetpack Compose SearchBar: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#SearchBar
