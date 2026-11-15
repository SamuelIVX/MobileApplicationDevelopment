package com.cuny.exchange

import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.PreferencesRepository
import com.cuny.exchange.ui.screens.search.SearchUiState
import com.cuny.exchange.ui.screens.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

private class FakePreferencesRepository(
    initialSearches: List<String> = emptyList()
) : PreferencesRepository {
    private val _recentSearches = MutableStateFlow(initialSearches)
    override val recentSearchesFlow: Flow<List<String>> = _recentSearches.asStateFlow()

    override suspend fun saveRecentSearches(searches: List<String>) {
        _recentSearches.value = searches.take(5)
    }
}

private class FakeSearchListingRepository(
    initialListings: List<Listing> = emptyList()
) : ListingRepository {
    val listingsFlow = MutableStateFlow(initialListings)
    var shouldFailSearch: Boolean = false

    override fun getListingsFlow(category: Category): Flow<List<Listing>> {
        return listingsFlow.map { list ->
            if (category == Category.ALL) list
            else list.filter { it.category.equals(category.label, ignoreCase = true) }
        }
    }

    override fun getSavedListingIdsFlow(userId: String): Flow<Set<String>> = flowOf(emptySet())
    override fun getListingById(listingId: String): Flow<Listing?> = listingsFlow.map { list -> list.firstOrNull { it.id == listingId } }
    override fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean> = flowOf(false)
    override suspend fun toggleBookmark(listingId: String): Result<Unit> = Result.success(Unit)
    override suspend fun createListing(listing: Listing, onWriteStarted: (() -> Unit)?): Result<String> {
        onWriteStarted?.invoke()
        return Result.success("id")
    }
    override suspend fun updateListingStatus(listingId: String, status: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteListing(listingId: String): Result<Unit> = Result.success(Unit)

    override suspend fun searchListings(
        query: String,
        campus: String,
        under100: Boolean,
        category: String
    ): List<Listing> {
        if (shouldFailSearch) throw RuntimeException("Network timeout during search")
        val tokens = query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        return listingsFlow.value.filter { listing ->
            if (listing.status != "active") return@filter false

            val matchesQuery = if (tokens.isEmpty()) {
                true
            } else {
                tokens.any { token ->
                    listing.title.lowercase().contains(token) ||
                        listing.description.lowercase().contains(token) ||
                        listing.category.lowercase().contains(token)
                }
            }
            if (!matchesQuery) return@filter false

            if (campus.isNotBlank() && !campus.equals("All campuses", ignoreCase = true)) {
                if (!listing.campus.equals(campus, ignoreCase = true)) return@filter false
            }

            if (under100 && listing.price >= 100.0) {
                return@filter false
            }

            if (category.isNotBlank() && !category.equals("All", ignoreCase = true)) {
                if (!listing.category.equals(category, ignoreCase = true)) return@filter false
            }

            true
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var listingRepository: FakeSearchListingRepository
    private lateinit var preferencesRepository: FakePreferencesRepository

    private val sampleListings = listOf(
        Listing(
            id = "item-1",
            title = "TI-84 Plus Graphing Calculator",
            description = "Great condition for calculus and stats",
            price = 45.0,
            category = "Electronics",
            campus = "Baruch College",
            sellerId = "seller-1",
            sellerName = "Alice Smith",
            sellerEmail = "alice@baruchmail.cuny.edu",
            status = "active"
        ),
        Listing(
            id = "item-2",
            title = "General Chemistry Textbook 10th Ed",
            description = "Lightly highlighted, useful for Chem 101",
            price = 30.0,
            category = "Textbooks",
            campus = "Hunter College",
            sellerId = "seller-2",
            sellerName = "Bob Jones",
            sellerEmail = "bob@hunter.cuny.edu",
            status = "active"
        ),
        Listing(
            id = "item-3",
            title = "Standing Study Desk",
            description = "Adjustable height ergonomic desk",
            price = 100.0, // boundary case for under $100
            category = "Furniture",
            campus = "Baruch College",
            sellerId = "seller-3",
            sellerName = "Charlie Davis",
            sellerEmail = "charlie@baruchmail.cuny.edu",
            status = "active"
        ),
        Listing(
            id = "item-4",
            title = "Desk Lamp with USB Port",
            description = "LED reading lamp with phone charger",
            price = 15.0,
            category = "Furniture",
            campus = "Brooklyn College",
            sellerId = "seller-4",
            sellerName = "Dana White",
            sellerEmail = "dana@brooklyn.cuny.edu",
            status = "active"
        ),
        Listing(
            id = "item-5",
            title = "iPad Air 4th Gen",
            description = "Rose gold 64GB with Apple Pencil",
            price = 350.0,
            category = "Electronics",
            campus = "Baruch College",
            sellerId = "seller-5",
            sellerName = "Eve Adams",
            sellerEmail = "eve@baruchmail.cuny.edu",
            status = "active"
        ),
        Listing(
            id = "item-6",
            title = "Old Draft Listing",
            description = "Not visible",
            price = 10.0,
            category = "Electronics",
            campus = "Baruch College",
            sellerId = "seller-6",
            sellerName = "Frank",
            sellerEmail = "frank@cuny.edu",
            status = "draft"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listingRepository = FakeSearchListingRepository(sampleListings)
        preferencesRepository = FakePreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun search_filters_by_keyword() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        viewModel.onQueryChanged("Calculator")
        advanceTimeBy(300)
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertEquals(1, results.size)
        assertEquals("item-1", results[0].id)
        assertEquals("TI-84 Plus Graphing Calculator", results[0].title)
    }

    @Test
    fun search_filters_by_campus() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        viewModel.onCampusSelected("Baruch College")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertEquals(3, results.size)
        assertTrue(results.all { it.campus == "Baruch College" })
        assertFalse(results.any { it.campus == "Hunter College" })
    }

    @Test
    fun under_100_filter_excludes_expensive_items_and_100_boundary() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        // Enable under $100 filter
        viewModel.toggleUnder100()
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        // item-1 ($45), item-2 ($30), item-4 ($15) are < 100
        // item-3 ($100.0) must be EXCLUDED because under100 is strictly < 100.0
        // item-5 ($350) is excluded
        assertEquals(3, results.size)
        assertTrue(results.all { it.price < 100.0 })
        assertFalse(results.any { it.price >= 100.0 })
        assertFalse(results.any { it.id == "item-3" })
        assertFalse(results.any { it.id == "item-5" })
    }

    @Test
    fun category_filter_matches_exact_category() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        viewModel.onCategorySelected("Textbooks")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertEquals(1, results.size)
        assertEquals("item-2", results[0].id)
        assertEquals("Textbooks", results[0].category)
    }

    @Test
    fun recent_searches_capped_at_five_and_mru_ordered() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        val searches = listOf("math", "physics", "biology", "chemistry", "psychology", "english")
        for (q in searches) {
            viewModel.onQueryChanged(q)
            advanceTimeBy(300)
            advanceUntilIdle()
        }

        val recent = viewModel.uiState.value.recentSearches
        assertEquals(5, recent.size)
        // Most recent first: english, psychology, chemistry, biology, physics
        assertEquals(listOf("english", "psychology", "chemistry", "biology", "physics"), recent)
        assertFalse(recent.contains("math"))
    }

    @Test
    fun tap_recent_search_populates_query() = runTest(testDispatcher) {
        val repoWithRecent = FakePreferencesRepository(listOf("calculus", "desk"))
        val viewModel = SearchViewModel(listingRepository, repoWithRecent)
        advanceUntilIdle()

        assertEquals(listOf("calculus", "desk"), viewModel.uiState.value.recentSearches)

        // User taps recent search "desk"
        viewModel.onQueryChanged("desk")
        assertEquals("desk", viewModel.uiState.value.query)

        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.results.size)
    }

    @Test
    fun clear_filters_resets_state_to_discovery_mode() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        viewModel.onQueryChanged("calculator")
        viewModel.onCampusSelected("Baruch College")
        viewModel.toggleUnder100()
        viewModel.onCategorySelected("Electronics")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals(1, viewModel.uiState.value.results.size)

        // Clear all filters
        viewModel.clearFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals("All campuses", state.selectedCampus)
        assertFalse(state.under100Only)
        assertEquals("All", state.selectedCategory)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearchActive)
    }

    @Test
    fun debounce_delays_search_until_300ms() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        viewModel.onQueryChanged("calculator")
        advanceTimeBy(100) // Not yet debounced
        assertTrue(viewModel.uiState.value.results.isEmpty())

        advanceTimeBy(200) // Reached 300ms total
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun search_failure_sets_error_state_and_retry_recovers() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(listingRepository, preferencesRepository)
        advanceUntilIdle()

        listingRepository.shouldFailSearch = true
        viewModel.onQueryChanged("calculator")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.results.isEmpty())

        // Resolve network issue and retry
        listingRepository.shouldFailSearch = false
        viewModel.retry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun results_count_label_formatting() {
        val zeroState = SearchUiState(
            query = "",
            selectedCampus = "All campuses",
            results = emptyList()
        )
        assertEquals("0 results across CUNY", zeroState.resultsCountLabel)

        val singleResult = SearchUiState(
            query = "desk",
            selectedCampus = "Baruch College",
            results = listOf(sampleListings[0])
        )
        assertEquals("1 result for \"desk\" at Baruch College", singleResult.resultsCountLabel)

        val multipleResults = SearchUiState(
            query = "book",
            selectedCampus = "All campuses",
            results = listOf(sampleListings[0], sampleListings[1])
        )
        assertEquals("2 results for \"book\" across CUNY", multipleResults.resultsCountLabel)
    }
}
