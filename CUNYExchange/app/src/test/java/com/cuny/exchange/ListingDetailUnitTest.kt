package com.cuny.exchange

import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Condition
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.ui.screens.detail.ListingDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
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

private class FakeDetailListingRepository : ListingRepository {
    val listingsMap = mutableMapOf<String, MutableStateFlow<Listing?>>()
    val bookmarksMap = mutableMapOf<String, MutableStateFlow<Boolean>>()
    var deletedListingId: String? = null
    var shouldFailBookmark = false
    var shouldFailStatusUpdate = false
    var shouldFailDelete = false

    fun seedListing(listing: Listing) {
        listingsMap[listing.id] = MutableStateFlow(listing)
    }

    override fun getListingsFlow(category: Category): Flow<List<Listing>> =
        MutableStateFlow(listingsMap.values.mapNotNull { it.value })

    override fun getSavedListingIdsFlow(userId: String): Flow<Set<String>> =
        MutableStateFlow(bookmarksMap.filter { it.value.value }.keys)

    override fun getListingById(listingId: String): Flow<Listing?> {
        return listingsMap.getOrPut(listingId) { MutableStateFlow(null) }
    }

    override fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean> {
        return bookmarksMap.getOrPut(listingId) { MutableStateFlow(false) }
    }

    override suspend fun toggleBookmark(listingId: String): Result<Unit> {
        if (shouldFailBookmark) {
            return Result.failure(RuntimeException("Network error updating bookmark"))
        }
        val flow = bookmarksMap.getOrPut(listingId) { MutableStateFlow(false) }
        flow.update { !it }
        return Result.success(Unit)
    }

    override suspend fun updateListingStatus(listingId: String, status: String): Result<Unit> {
        if (shouldFailStatusUpdate) {
            return Result.failure(RuntimeException("Firestore status update failed"))
        }
        listingsMap[listingId]?.update { it?.copy(status = status) }
        return Result.success(Unit)
    }

    override suspend fun createListing(
        listing: Listing,
        onWriteStarted: (() -> Unit)?
    ): Result<String> {
        onWriteStarted?.invoke()
        seedListing(listing)
        return Result.success(listing.id)
    }

    override suspend fun deleteListing(listingId: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.failure(RuntimeException("Firestore delete failed"))
        }
        deletedListingId = listingId
        listingsMap[listingId]?.update { null }
        return Result.success(Unit)
    }
}

private class FakeDetailAuthRepository(
    initialUser: User? = User(
        id = "buyer-uid",
        displayName = "Jordan K.",
        campus = "Baruch College",
        major = "Finance",
        isVerified = true
    )
) : AuthRepository {
    private val _currentUserFlow = MutableStateFlow<User?>(initialUser)
    override val currentUserFlow = _currentUserFlow.asStateFlow()
    override val currentUserId: String? get() = _currentUserFlow.value?.id
    val userProfiles = mutableMapOf<String, User>()

    fun emitUser(user: User?) {
        _currentUserFlow.value = user
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyEmailLink(email: String, link: String): Result<User> =
        Result.success(_currentUserFlow.value ?: User(id = "buyer-uid"))
    override suspend fun getUserProfile(userId: String): Result<User?> =
        Result.success(userProfiles[userId] ?: _currentUserFlow.value?.takeIf { it.id == userId })
    override suspend fun saveUserProfile(
        userId: String,
        displayName: String,
        campus: String,
        major: String
    ): Result<User> = Result.success(_currentUserFlow.value ?: User(id = userId))
    override suspend fun signOut() {
        _currentUserFlow.value = null
    }
    override fun isEmailValid(email: String): Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListingDetailUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var listingRepo: FakeDetailListingRepository
    private lateinit var authRepo: FakeDetailAuthRepository

    private val sampleListing = Listing(
        id = "test-listing-101",
        sellerId = "seller-uid-999",
        sellerEmail = "seller@baruchmail.cuny.edu",
        sellerName = "Aria M.",
        sellerMajor = "Finance",
        sellerRating = 4.9,
        title = "Calculus Early Transcendentals 8th Ed",
        price = 45.0,
        condition = Condition.LIKE_NEW.label,
        category = Category.TEXTBOOKS.label,
        campus = "Baruch College",
        safeZone = "Newman Library lobby (security desk)",
        description = "Barely used textbook for MTH 2003 with zero highlighting.",
        photoUrls = listOf("https://storage.cuny.edu/p1.jpg", "https://storage.cuny.edu/p2.jpg"),
        status = "active"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listingRepo = FakeDetailListingRepository()
        authRepo = FakeDetailAuthRepository()
        listingRepo.seedListing(sampleListing)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun detail_renders_seller_info_and_item_data() = runTest(testDispatcher) {
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.listing)

        val item = state.listing!!
        assertEquals("Calculus Early Transcendentals 8th Ed", item.title)
        assertEquals(45.0, item.price, 0.001)
        assertEquals("Aria M.", item.sellerName)
        assertEquals("Finance", item.sellerMajor)
        assertEquals(4.9, item.sellerRating, 0.001)
        assertEquals(2, item.photoUrls.size)
    }

    @Test
    fun detail_shows_safe_zone() = runTest(testDispatcher) {
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        val item = viewModel.uiState.value.listing
        assertNotNull(item)
        assertEquals("Newman Library lobby (security desk)", item!!.safeZone)
    }

    @Test
    fun message_seller_hidden_for_owner() = runTest(testDispatcher) {
        // Log in as the owner of the listing
        authRepo.emitUser(User(id = "seller-uid-999", displayName = "Aria M."))

        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.listing)
        assertTrue("When sellerId matches current user, isOwner must be true", state.isOwner)
    }

    @Test
    fun message_seller_visible_for_buyer() = runTest(testDispatcher) {
        // Log in as buyer
        authRepo.emitUser(User(id = "buyer-uid-123", displayName = "Jordan K."))

        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.listing)
        assertFalse("When sellerId does not match current user, isOwner must be false", state.isOwner)
    }

    @Test
    fun owner_sees_mark_sold_and_delete() = runTest(testDispatcher) {
        authRepo.emitUser(User(id = "seller-uid-999", displayName = "Aria M."))

        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isOwner)

        // Mark as sold
        viewModel.markAsSold()
        testScheduler.runCurrent()

        assertEquals("sold", viewModel.uiState.value.listing?.status)

        // Mark as active
        viewModel.markAsActive()
        testScheduler.runCurrent()

        assertEquals("active", viewModel.uiState.value.listing?.status)
    }

    @Test
    fun owner_can_delete_listing() = runTest(testDispatcher) {
        authRepo.emitUser(User(id = "seller-uid-999", displayName = "Aria M."))

        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        var onDeletedCalled = false
        viewModel.deleteListing(onDeleted = { onDeletedCalled = true })
        testScheduler.runCurrent()

        assertTrue(onDeletedCalled)
        assertTrue(viewModel.uiState.value.isDeleted)
        assertEquals("test-listing-101", listingRepo.deletedListingId)
    }

    @Test
    fun toggle_save_updates_bookmark_state() = runTest(testDispatcher) {
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isSaved)

        viewModel.toggleSave()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.toggleSave()
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun listing_not_found_shows_error() = runTest(testDispatcher) {
        val viewModel = ListingDetailViewModel("non-existent-id", listingRepo, authRepo)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.listing)
        assertEquals("This listing is no longer available", state.errorMessage)
    }

    @Test
    fun seller_profile_populates_actual_sold_count() = runTest(testDispatcher) {
        authRepo.userProfiles["seller-uid-999"] = User(
            id = "seller-uid-999",
            displayName = "Aria M.",
            campus = "Baruch College",
            major = "Finance",
            rating = 4.9,
            soldCount = 12,
            isVerified = true
        )

        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.sellerProfile)
        assertEquals(12, state.sellerProfile?.soldCount)
    }

    @Test
    fun toggle_save_handles_repository_failure() = runTest(testDispatcher) {
        listingRepo.shouldFailBookmark = true
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.toggleSave()
        testScheduler.runCurrent()

        assertEquals("Network error updating bookmark", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun mark_as_sold_handles_repository_failure() = runTest(testDispatcher) {
        listingRepo.shouldFailStatusUpdate = true
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.markAsSold()
        testScheduler.runCurrent()

        assertEquals("Firestore status update failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun mark_as_active_handles_repository_failure() = runTest(testDispatcher) {
        listingRepo.shouldFailStatusUpdate = true
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.markAsActive()
        testScheduler.runCurrent()

        assertEquals("Firestore status update failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun delete_listing_handles_repository_failure() = runTest(testDispatcher) {
        listingRepo.shouldFailDelete = true
        val viewModel = ListingDetailViewModel("test-listing-101", listingRepo, authRepo)
        testScheduler.runCurrent()

        var onDeletedCalled = false
        viewModel.deleteListing(onDeleted = { onDeletedCalled = true })
        testScheduler.runCurrent()

        assertFalse(onDeletedCalled)
        assertFalse(viewModel.uiState.value.isDeleted)
        assertEquals("Firestore delete failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun retry_reloads_details_after_error() = runTest(testDispatcher) {
        // Start with missing listing
        val viewModel = ListingDetailViewModel("reloaded-id", listingRepo, authRepo)
        testScheduler.runCurrent()

        assertEquals("This listing is no longer available", viewModel.uiState.value.errorMessage)

        // Now seed the listing and call retry
        listingRepo.seedListing(sampleListing.copy(id = "reloaded-id"))
        viewModel.retry()
        testScheduler.runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.listing)
        assertEquals("reloaded-id", viewModel.uiState.value.listing?.id)
    }
}
