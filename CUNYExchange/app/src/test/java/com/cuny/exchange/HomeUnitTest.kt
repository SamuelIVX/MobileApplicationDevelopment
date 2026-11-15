package com.cuny.exchange

import com.cuny.exchange.data.model.Campus
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.ui.screens.home.HomeViewModel
import com.cuny.exchange.ui.util.PriceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeHomeListingRepository : ListingRepository {
    val listingsFlow = MutableStateFlow<List<Listing>>(emptyList())
    val savedListingIdsByUser = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    var currentUserId: String = "test-user-id"
    var shouldFailToggleBookmark: Boolean = false
    var shouldThrowOnListings: Boolean = false

    override fun getListingsFlow(category: Category): Flow<List<Listing>> {
        if (shouldThrowOnListings) {
            return kotlinx.coroutines.flow.flow {
                throw RuntimeException("Firestore connection error")
            }
        }
        return listingsFlow.map { list ->
            if (category == Category.ALL) {
                list
            } else {
                list.filter { it.category.equals(category.label, ignoreCase = true) }
            }
        }
    }

    override fun getSavedListingIdsFlow(userId: String): Flow<Set<String>> {
        return savedListingIdsByUser.map { it[userId] ?: emptySet() }
    }

    override fun getListingById(listingId: String): Flow<Listing?> {
        return listingsFlow.map { list -> list.firstOrNull { it.id == listingId } }
    }

    override fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean> {
        return getSavedListingIdsFlow(userId).map { it.contains(listingId) }
    }

    override suspend fun toggleBookmark(listingId: String): Result<Unit> {
        if (shouldFailToggleBookmark) {
            return Result.failure(RuntimeException("Network error updating bookmark"))
        }
        savedListingIdsByUser.update { current ->
            val userSaved = current[currentUserId] ?: emptySet()
            val updated = if (userSaved.contains(listingId)) {
                userSaved - listingId
            } else {
                userSaved + listingId
            }
            current + (currentUserId to updated)
        }
        return Result.success(Unit)
    }

    override suspend fun updateListingStatus(listingId: String, status: String): Result<Unit> {
        listingsFlow.update { list ->
            list.map { if (it.id == listingId) it.copy(status = status) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun createListing(
        listing: Listing,
        onWriteStarted: (() -> Unit)?
    ): Result<String> {
        onWriteStarted?.invoke()
        return Result.success("fake-id")
    }

    override suspend fun deleteListing(listingId: String): Result<Unit> {
        listingsFlow.update { list -> list.filter { it.id != listingId } }
        return Result.success(Unit)
    }

    override suspend fun searchListings(
        query: String,
        campus: String,
        under100: Boolean,
        category: String
    ): List<Listing> = listingsFlow.value
}

private class FakeHomeAuthRepository(
    initialUser: User? = User(
        id = "test-user-id",
        displayName = "Aria M.",
        campus = "Brooklyn College",
        major = "Computer Science"
    )
) : AuthRepository {
    private val _currentUserFlow = MutableStateFlow<User?>(initialUser)
    override val currentUserFlow = _currentUserFlow.asStateFlow()
    override val currentUserId: String? get() = _currentUserFlow.value?.id

    fun setUser(user: User?) {
        _currentUserFlow.value = user
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyEmailLink(email: String, link: String): Result<User> =
        Result.success(_currentUserFlow.value ?: User(id = "test-user-id"))
    override suspend fun getUserProfile(userId: String): Result<User?> = Result.success(_currentUserFlow.value)
    override suspend fun saveUserProfile(
        userId: String,
        displayName: String,
        campus: String,
        major: String
    ): Result<User> {
        val updated = (_currentUserFlow.value ?: User(id = userId)).copy(
            displayName = displayName,
            campus = campus,
            major = major
        )
        _currentUserFlow.value = updated
        return Result.success(updated)
    }
    override suspend fun signOut() {
        _currentUserFlow.value = null
    }
    override fun isEmailValid(email: String): Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var listingRepository: FakeHomeListingRepository
    private lateinit var authRepository: FakeHomeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listingRepository = FakeHomeListingRepository()
        authRepository = FakeHomeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Oracle 2: Price Formatter Tests ---

    @Test
    fun free_listing_displays_free() {
        assertEquals("Free", PriceFormatter.formatPrice(0.0))
        assertEquals("Free", PriceFormatter.formatPrice(-5.0))
    }

    @Test
    fun whole_dollars_display_without_decimals() {
        assertEquals("$85", PriceFormatter.formatPrice(85.0))
        assertEquals("$100", PriceFormatter.formatPrice(100.0))
        assertEquals("$1", PriceFormatter.formatPrice(1.0))
    }

    @Test
    fun decimal_dollars_display_two_decimal_places() {
        assertEquals("$14.50", PriceFormatter.formatPrice(14.5))
        assertEquals("$14.99", PriceFormatter.formatPrice(14.99))
        assertEquals("$0.50", PriceFormatter.formatPrice(0.50))
    }

    // --- R5: Feed Partitioning Tests ---

    @Test
    fun home_feed_partitions_campus_correctly() = runTest(testDispatcher) {
        val brooklynItem1 = Listing(id = "item-1", title = "Algorithms Textbook", campus = "Brooklyn College", category = "Textbooks", price = 45.0)
        val brooklynItem2 = Listing(id = "item-2", title = "TI-84 Plus CE", campus = "Brooklyn College", category = "Calculators", price = 80.0)
        val baruchItem = Listing(id = "item-3", title = "Finance 101 Notes", campus = "Baruch College", category = "Textbooks", price = 20.0)
        val hunterItem = Listing(id = "item-4", title = "Chemistry Lab Coat", campus = "Hunter College", category = "Clothing", price = 15.0)

        listingRepository.listingsFlow.value = listOf(brooklynItem1, brooklynItem2, baruchItem, hunterItem)

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse("Feed should not be loading", state.isLoading)
        assertEquals(2, state.campusListings.size)
        assertEquals(listOf("item-1", "item-2"), state.campusListings.map { it.id })
        assertTrue("All items in campusListings must match student campus", state.campusListings.all { it.campus == "Brooklyn College" })

        assertEquals(2, state.cunyListings.size)
        assertEquals(listOf("item-3", "item-4"), state.cunyListings.map { it.id })
        assertTrue("No items in cunyListings should match student campus", state.cunyListings.none { it.campus == "Brooklyn College" })
    }

    // --- R3: Category Filtering Tests ---

    @Test
    fun home_feed_filters_by_category() = runTest(testDispatcher) {
        val textbook = Listing(id = "tb-1", title = "Calculus", category = "Textbooks", campus = "Brooklyn College", price = 30.0)
        val calculator = Listing(id = "calc-1", title = "TI-84", category = "Calculators", campus = "Brooklyn College", price = 75.0)
        val electronics = Listing(id = "elec-1", title = "iPad Air", category = "Electronics", campus = "Hunter College", price = 350.0)

        listingRepository.listingsFlow.value = listOf(textbook, calculator, electronics)

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        // 1. Initial State: Category.ALL
        assertEquals(Category.ALL, viewModel.uiState.value.selectedCategory)
        assertEquals(2, viewModel.uiState.value.campusListings.size)
        assertEquals(1, viewModel.uiState.value.cunyListings.size)

        // 2. Select Calculators
        viewModel.selectCategory(Category.CALCULATORS)
        testScheduler.runCurrent()

        val calcState = viewModel.uiState.value
        assertEquals(Category.CALCULATORS, calcState.selectedCategory)
        assertEquals(1, calcState.campusListings.size)
        assertEquals("calc-1", calcState.campusListings.first().id)
        assertEquals(0, calcState.cunyListings.size)

        // 3. Select Electronics
        viewModel.selectCategory(Category.ELECTRONICS)
        testScheduler.runCurrent()

        val elecState = viewModel.uiState.value
        assertEquals(Category.ELECTRONICS, elecState.selectedCategory)
        assertEquals(0, elecState.campusListings.size)
        assertEquals(1, elecState.cunyListings.size)
        assertEquals("elec-1", elecState.cunyListings.first().id)

        // 4. Return to ALL
        viewModel.selectCategory(Category.ALL)
        testScheduler.runCurrent()
        assertEquals(2, viewModel.uiState.value.campusListings.size)
        assertEquals(1, viewModel.uiState.value.cunyListings.size)
    }

    // --- R6: Bookmark / Save Toggle Tests ---

    @Test
    fun toggle_save_updates_ui_state() = runTest(testDispatcher) {
        val listing = Listing(id = "save-test-1", title = "Physics Textbook", campus = "Brooklyn College", category = "Textbooks", price = 50.0)
        listingRepository.listingsFlow.value = listOf(listing)

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        // Initially not saved
        assertFalse(viewModel.uiState.value.savedListingIds.contains("save-test-1"))

        // Toggle save -> Saved
        viewModel.toggleSave("save-test-1")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.savedListingIds.contains("save-test-1"))

        // Toggle save again -> Unsaved
        viewModel.toggleSave("save-test-1")
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.savedListingIds.contains("save-test-1"))
    }

    // --- Failure Modes & Edge Cases ---

    @Test
    fun student_campus_unset_defaults_to_cuny_wide_feed() = runTest(testDispatcher) {
        authRepository.setUser(User(id = "user-no-campus", displayName = "Sam H.", campus = "", major = "Math"))

        val item1 = Listing(id = "i1", title = "Item 1", campus = "Baruch College")
        val item2 = Listing(id = "i2", title = "Item 2", campus = "Hunter College")
        listingRepository.listingsFlow.value = listOf(item1, item2)

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("Campus listings must be empty when user campus is unset", 0, state.campusListings.size)
        assertEquals("All listings should fall back to CUNY-wide feed", 2, state.cunyListings.size)
    }

    @Test
    fun sign_out_clears_feed_and_retained_state() = runTest(testDispatcher) {
        val item = Listing(id = "item-1", title = "Book", campus = "Brooklyn College")
        listingRepository.listingsFlow.value = listOf(item)

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.uiState.value.campusListings.size)

        // Sign out
        authRepository.setUser(null)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(null, state.currentUser)
        assertEquals(0, state.campusListings.size)
        assertEquals(0, state.cunyListings.size)
        assertEquals(0, state.savedListingIds.size)
    }

    @Test
    fun empty_campus_section_displays_empty_campus_list_and_populates_cuny_section() = runTest(testDispatcher) {
        // User campus is Brooklyn College, but no items exist at Brooklyn College
        val baruchItem = Listing(id = "baruch-1", title = "Baruch Item", campus = "Baruch College")
        listingRepository.listingsFlow.value = listOf(baruchItem)

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(0, state.campusListings.size)
        assertEquals(1, state.cunyListings.size)
        assertEquals("baruch-1", state.cunyListings.first().id)
    }

    @Test
    fun campus_safe_zone_lookup_resolves_all_major_cuny_campuses() {
        val campuses = listOf(
            "Baruch College",
            "Brooklyn College",
            "City Tech (NYCCT)",
            "City College of New York (CCNY)",
            "College of Staten Island (CSI)",
            "Hunter College",
            "John Jay College",
            "Queens College"
        )

        for (campusName in campuses) {
            val campus = Campus.findByName(campusName)
            assertNotNull("Campus should be found for $campusName", campus)
            assertTrue("Safe zone must not be blank for $campusName", campus!!.safeZone.isNotBlank())
        }
    }

    @Test
    fun toggle_save_failure_updates_error_message() = runTest(testDispatcher) {
        val listing = Listing(id = "item-fail", title = "Book", campus = "Brooklyn College")
        listingRepository.listingsFlow.value = listOf(listing)
        listingRepository.shouldFailToggleBookmark = true

        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        viewModel.toggleSave("item-fail")
        testScheduler.runCurrent()

        assertEquals("Network error updating bookmark", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.savedListingIds.contains("item-fail"))

        viewModel.clearErrorMessage()
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun feed_error_keeps_category_observation_active_and_recovers() = runTest(testDispatcher) {
        // Initially error
        listingRepository.shouldThrowOnListings = true
        val viewModel = HomeViewModel(listingRepository, authRepository)
        testScheduler.runCurrent()

        val errorState = viewModel.uiState.value
        assertFalse(errorState.isLoading)
        assertEquals("Firestore connection error", errorState.errorMessage)

        // Switch category after error: flow must remain alive
        listingRepository.shouldThrowOnListings = false
        listingRepository.listingsFlow.value = listOf(
            Listing(id = "recovered-item", title = "TI-84", category = "Calculators", campus = "Brooklyn College")
        )

        viewModel.selectCategory(Category.CALCULATORS)
        testScheduler.runCurrent()

        val recoveredState = viewModel.uiState.value
        assertEquals(Category.CALCULATORS, recoveredState.selectedCategory)
        assertEquals(null, recoveredState.errorMessage)
        assertEquals(1, recoveredState.campusListings.size)
        assertEquals("recovered-item", recoveredState.campusListings.first().id)
    }
}
