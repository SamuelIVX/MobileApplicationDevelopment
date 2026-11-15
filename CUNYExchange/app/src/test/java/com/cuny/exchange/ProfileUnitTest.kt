package com.cuny.exchange

import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.ui.screens.profile.ProfileUiState
import com.cuny.exchange.ui.screens.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
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

private class FakeProfileAuthRepository(
    initialUser: User? = null
) : AuthRepository {
    private val _currentUserFlow = MutableStateFlow(initialUser)
    override val currentUserFlow: Flow<User?> = _currentUserFlow.asStateFlow()
    override val currentUserId: String? get() = _currentUserFlow.value?.id

    var signOutCalled: Boolean = false

    fun emitUser(user: User?) {
        _currentUserFlow.value = user
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyEmailLink(email: String, link: String): Result<User> =
        Result.success(_currentUserFlow.value ?: User(id = "user-1", displayName = "Test User"))
    override suspend fun getUserProfile(userId: String): Result<User?> = Result.success(_currentUserFlow.value)
    override suspend fun saveUserProfile(userId: String, displayName: String, campus: String, major: String): Result<User> {
        val updated = _currentUserFlow.value?.copy(displayName = displayName, campus = campus, major = major)
            ?: User(id = userId, displayName = displayName, campus = campus, major = major)
        _currentUserFlow.value = updated
        return Result.success(updated)
    }

    override suspend fun signOut() {
        signOutCalled = true
        _currentUserFlow.value = null
    }

    override fun isEmailValid(email: String): Boolean = true
}

private class FakeProfileListingRepository(
    initialListings: List<Listing> = emptyList()
) : ListingRepository {
    val listingsFlow = MutableStateFlow(initialListings)
    var shouldFailUpdateStatus: Boolean = false
    var shouldFailDelete: Boolean = false
    var shouldThrowOnListings: Boolean = false
    var deletedListingId: String? = null

    override fun getListingsFlow(category: Category): Flow<List<Listing>> = listingsFlow

    override fun getSavedListingIdsFlow(userId: String): Flow<Set<String>> = flowOf(emptySet())

    override fun getListingById(listingId: String): Flow<Listing?> =
        listingsFlow.map { list -> list.firstOrNull { it.id == listingId } }

    override fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean> = flowOf(false)

    override suspend fun toggleBookmark(listingId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateListingStatus(listingId: String, status: String): Result<Unit> {
        if (shouldFailUpdateStatus) {
            return Result.failure(RuntimeException("Firestore update failed"))
        }
        listingsFlow.update { current ->
            current.map { if (it.id == listingId) it.copy(status = status) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun createListing(listing: Listing, onWriteStarted: (() -> Unit)?): Result<String> {
        onWriteStarted?.invoke()
        return Result.success("id")
    }

    override suspend fun deleteListing(listingId: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.failure(RuntimeException("Network error"))
        }
        deletedListingId = listingId
        listingsFlow.update { current ->
            current.filter { it.id != listingId }
        }
        return Result.success(Unit)
    }

    override fun getMyListingsFlow(userId: String): Flow<List<Listing>> {
        if (shouldThrowOnListings) {
            return kotlinx.coroutines.flow.flow { throw RuntimeException("Firestore listener error") }
        }
        return listingsFlow.map { list ->
            list.filter { it.sellerId == userId }
        }
    }

    override suspend fun searchListings(
        query: String,
        campus: String,
        under100: Boolean,
        category: String
    ): List<Listing> = listingsFlow.value
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeProfileAuthRepository
    private lateinit var listingRepository: FakeProfileListingRepository

    private val testUser = User(
        id = "user-123",
        displayName = "Marcus K.",
        campus = "Brooklyn College",
        major = "Biology",
        rating = 4.9,
        soldCount = 7,
        isVerified = true
    )

    private val sampleListings = listOf(
        Listing(
            id = "listing-1",
            title = "Organic Chemistry Textbook",
            description = "Good condition with minor notes",
            price = 45.0,
            category = "Textbooks",
            campus = "Brooklyn College",
            sellerId = "user-123",
            sellerName = "Marcus K.",
            status = "active"
        ),
        Listing(
            id = "listing-2",
            title = "TI-84 Plus CE",
            description = "Color graphing calculator",
            price = 70.0,
            category = "Calculators",
            campus = "Brooklyn College",
            sellerId = "user-123",
            sellerName = "Marcus K.",
            status = "sold"
        ),
        Listing(
            id = "listing-3",
            title = "Other Student's Laptop Stand",
            description = "Ergonomic aluminum stand",
            price = 25.0,
            category = "Electronics",
            campus = "Hunter College",
            sellerId = "user-999", // Different seller!
            sellerName = "Alice",
            status = "active"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeProfileAuthRepository(testUser)
        listingRepository = FakeProfileListingRepository(sampleListings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun profile_renders_student_identity() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.user)
        assertEquals("Marcus K.", state.user?.displayName)
        assertEquals("Brooklyn College", state.user?.campus)
        assertEquals("Biology", state.user?.major)
        assertTrue(state.user?.isVerified == true)
        assertEquals(4.9, state.user?.rating ?: 0.0, 0.01)
    }

    @Test
    fun my_listings_displays_only_owned_items() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        val myListings = viewModel.uiState.value.myListings
        // Only 2 of the 3 listings belong to user-123
        assertEquals(2, myListings.size)
        assertTrue(myListings.all { it.sellerId == "user-123" })
        assertFalse(myListings.any { it.sellerId == "user-999" })
        assertFalse(myListings.any { it.id == "listing-3" })
    }

    @Test
    fun active_and_sold_counts_calculated_correctly() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeCount) // listing-1
        assertEquals(1, state.soldCount)   // listing-2
    }

    @Test
    fun toggle_status_from_active_to_sold() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        val activeListing = viewModel.uiState.value.myListings.first { it.id == "listing-1" }
        assertEquals("active", activeListing.status)

        viewModel.toggleListingStatus(activeListing)
        advanceUntilIdle()

        val updatedListing = viewModel.uiState.value.myListings.first { it.id == "listing-1" }
        assertEquals("sold", updatedListing.status)
        assertEquals(0, viewModel.uiState.value.activeCount)
        assertEquals(2, viewModel.uiState.value.soldCount)
    }

    @Test
    fun toggle_status_from_sold_to_active_relist() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        val soldListing = viewModel.uiState.value.myListings.first { it.id == "listing-2" }
        assertEquals("sold", soldListing.status)

        viewModel.toggleListingStatus(soldListing)
        advanceUntilIdle()

        val updatedListing = viewModel.uiState.value.myListings.first { it.id == "listing-2" }
        assertEquals("active", updatedListing.status)
        assertEquals(2, viewModel.uiState.value.activeCount)
        assertEquals(0, viewModel.uiState.value.soldCount)
    }

    @Test
    fun toggle_status_failure_surfaces_error() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        listingRepository.shouldFailUpdateStatus = true
        val activeListing = viewModel.uiState.value.myListings.first { it.id == "listing-1" }

        viewModel.toggleListingStatus(activeListing)
        advanceUntilIdle()

        assertEquals("Failed to update listing status", viewModel.uiState.value.errorMessage)
        // Status should remain unchanged
        val listing = viewModel.uiState.value.myListings.first { it.id == "listing-1" }
        assertEquals("active", listing.status)
    }

    @Test
    fun delete_prompts_confirmation_and_dismiss_resets() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.showDeleteDialogForId)

        // Request delete
        viewModel.requestDelete("listing-1")
        assertEquals("listing-1", viewModel.uiState.value.showDeleteDialogForId)

        // Dismiss delete
        viewModel.dismissDeleteDialog()
        assertNull(viewModel.uiState.value.showDeleteDialogForId)
    }

    @Test
    fun confirm_delete_permanently_removes_listing() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        viewModel.requestDelete("listing-1")
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals("listing-1", listingRepository.deletedListingId)
        assertNull(viewModel.uiState.value.showDeleteDialogForId)
        assertEquals(1, viewModel.uiState.value.myListings.size)
        assertFalse(viewModel.uiState.value.myListings.any { it.id == "listing-1" })
    }

    @Test
    fun delete_failure_sets_network_error() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        listingRepository.shouldFailDelete = true
        viewModel.requestDelete("listing-1")
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals("Network required to delete listing. Please check connection.", viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.myListings.size)
    }

    @Test
    fun empty_state_when_user_has_no_listings() = runTest(testDispatcher) {
        val emptyRepo = FakeProfileListingRepository(emptyList())
        val viewModel = ProfileViewModel(authRepository, emptyRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.myListings.isEmpty())
        assertEquals(0, state.activeCount)
        assertEquals(0, state.soldCount)
    }

    @Test
    fun sign_out_invokes_auth_repository_and_callback() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        var callbackCalled = false
        viewModel.signOut {
            callbackCalled = true
        }
        advanceUntilIdle()

        assertTrue(authRepository.signOutCalled)
        assertTrue(callbackCalled)
    }

    @Test
    fun profile_load_failure_sets_load_error_and_retry_recovers() = runTest(testDispatcher) {
        listingRepository.shouldThrowOnListings = true
        val viewModel = ProfileViewModel(authRepository, listingRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoadError)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.myListings.isEmpty())

        // Recover on retry
        listingRepository.shouldThrowOnListings = false
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadError)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.myListings.size)
    }
}
