package com.cuny.exchange

import android.net.Uri
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Condition
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.StorageRepository
import com.cuny.exchange.ui.screens.create.CreateListingUiState
import com.cuny.exchange.ui.screens.create.CreateListingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeCreateListingRepository : ListingRepository {
    var createdListings = mutableListOf<Listing>()
    var shouldFailCreate = false

    override fun getListingsFlow(category: Category): Flow<List<Listing>> = MutableStateFlow(emptyList())
    override fun getSavedListingIdsFlow(userId: String): Flow<Set<String>> = MutableStateFlow(emptySet())
    override fun getListingById(listingId: String): Flow<Listing?> = MutableStateFlow(null)
    override fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun toggleBookmark(listingId: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateListingStatus(listingId: String, status: String): Result<Unit> = Result.success(Unit)

    override suspend fun createListing(listing: Listing): Result<String> {
        if (shouldFailCreate) {
            return Result.failure(RuntimeException("Firestore write error"))
        }
        createdListings.add(listing)
        return Result.success("new-listing-id")
    }
}

private class FakeStorageRepository : StorageRepository {
    var uploadedUrls = mutableListOf<String>()
    var deletedUrls = mutableListOf<String>()
    var shouldFailUpload = false
    var onUploadHook: (() -> Unit)? = null

    override suspend fun uploadPhotos(uris: List<Uri>): List<String> {
        onUploadHook?.invoke()
        if (shouldFailUpload) {
            throw RuntimeException("Network error during photo upload")
        }
        val urls = uris.mapIndexed { idx, _ -> "https://storage.firebase.com/photo_$idx.jpg" }
        uploadedUrls.addAll(urls)
        return urls
    }

    override suspend fun deletePhotos(urls: List<String>): Result<Unit> {
        deletedUrls.addAll(urls)
        return Result.success(Unit)
    }
}

private class FakeCreateAuthRepository(
    initialUser: User? = User(
        id = "seller-uid-123",
        displayName = "Taylor S.",
        campus = "Baruch College",
        major = "Finance",
        isVerified = true
    )
) : AuthRepository {
    private val _currentUserFlow = MutableStateFlow<User?>(initialUser)
    override val currentUserFlow = _currentUserFlow.asStateFlow()
    override val currentUserId: String? get() = _currentUserFlow.value?.id

    fun emitUser(user: User?) {
        _currentUserFlow.value = user
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyEmailLink(email: String, link: String): Result<User> =
        Result.success(_currentUserFlow.value ?: User(id = "seller-uid-123"))
    override suspend fun getUserProfile(userId: String): Result<User?> = Result.success(_currentUserFlow.value)
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
class CreateListingUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var listingRepo: FakeCreateListingRepository
    private lateinit var storageRepo: FakeStorageRepository
    private lateinit var authRepo: FakeCreateAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listingRepo = FakeCreateListingRepository()
        storageRepo = FakeStorageRepository()
        authRepo = FakeCreateAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Form Validation & Disabled State Tests ---

    @Test
    fun publish_button_disabled_with_empty_title() {
        val state = CreateListingUiState(
            title = "",
            priceText = "25.00",
            campus = "Baruch College"
        )
        assertFalse("Publish must be disabled when title is empty", state.isPublishEnabled)
    }

    @Test
    fun publish_button_disabled_with_whitespace_title() {
        val state = CreateListingUiState(
            title = "     ",
            priceText = "25.00",
            campus = "Baruch College"
        )
        assertFalse("Publish must be disabled when title is purely whitespace", state.isPublishEnabled)
    }

    @Test
    fun publish_button_disabled_with_negative_price() {
        val state = CreateListingUiState(
            title = "Economics Textbook",
            priceText = "-5",
            campus = "Baruch College"
        )
        assertNull("Negative price must parse to null", state.parsedPrice)
        assertFalse("Publish must be disabled with negative price", state.isPublishEnabled)
    }

    @Test
    fun publish_button_enabled_with_zero_price() {
        val state = CreateListingUiState(
            title = "Free Study Notes",
            priceText = "0",
            campus = "Baruch College"
        )
        assertEquals(0.0, state.parsedPrice!!, 0.001)
        assertTrue("Publish must be enabled for 0 price (Free)", state.isPublishEnabled)
    }

    @Test
    fun publish_button_enabled_with_two_decimal_places() {
        val state = CreateListingUiState(
            title = "Organic Chem Notes",
            priceText = "$14.50",
            campus = "Hunter College"
        )
        assertEquals(14.50, state.parsedPrice!!, 0.001)
        assertTrue("Publish must be enabled with valid 2-decimal price", state.isPublishEnabled)
    }

    @Test
    fun price_parsing_rejects_more_than_two_decimals() {
        val state = CreateListingUiState(
            title = "Item",
            priceText = "14.555",
            campus = "Hunter College"
        )
        assertNull("Price with 3+ decimals must be rejected", state.parsedPrice)
        assertFalse(state.isPublishEnabled)
    }

    @Test
    fun price_parsing_rejects_non_numeric_characters_and_exponent_notation() {
        val state1 = CreateListingUiState(title = "Item", priceText = "12.3.4", campus = "Baruch College")
        assertNull(state1.parsedPrice)

        val state2 = CreateListingUiState(title = "Item", priceText = "abc", campus = "Baruch College")
        assertNull(state2.parsedPrice)

        // Exponent notation must be rejected
        val state3 = CreateListingUiState(title = "Item", priceText = "1e-3", campus = "Baruch College")
        assertNull("Exponent notation 1e-3 must be rejected", state3.parsedPrice)

        val state4 = CreateListingUiState(title = "Item", priceText = "1e5", campus = "Baruch College")
        assertNull("Exponent notation 1e5 must be rejected", state4.parsedPrice)
    }

    @Test
    fun sign_out_clears_draft_state_preventing_cross_user_leak() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.onTitleChanged("User A Draft")
        viewModel.onPriceChanged("50.00")
        viewModel.onPhotosSelected(listOf(Uri.EMPTY))
        assertEquals("User A Draft", viewModel.uiState.value.title)
        assertEquals(1, viewModel.uiState.value.photoUris.size)

        // Account A signs out
        authRepo.emitUser(null)
        testScheduler.runCurrent()

        // Draft state is completely wiped
        assertEquals("", viewModel.uiState.value.title)
        assertEquals("", viewModel.uiState.value.priceText)
        assertEquals(0, viewModel.uiState.value.photoUris.size)

        // Account B signs in
        authRepo.emitUser(
            User(
                id = "user-b",
                displayName = "Jordan B.",
                campus = "Hunter College",
                major = "Biology"
            )
        )
        testScheduler.runCurrent()

        // Account B sees their own default campus and clean draft
        assertEquals("Hunter College", viewModel.uiState.value.campus)
        assertEquals("", viewModel.uiState.value.title)
        assertEquals(0, viewModel.uiState.value.photoUris.size)
    }

    // --- Photo Selection & Limits Tests ---

    @Test
    fun photo_count_capped_at_five() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        // Select 6 URIs using Uri.EMPTY: only 5 should be accepted
        viewModel.onPhotosSelected(listOf(Uri.EMPTY, Uri.EMPTY, Uri.EMPTY, Uri.EMPTY, Uri.EMPTY, Uri.EMPTY))
        assertEquals(5, viewModel.uiState.value.photoUris.size)

        // Remove photo at index 2
        viewModel.removePhoto(2)
        assertEquals(4, viewModel.uiState.value.photoUris.size)
    }

    // --- Title & Description Length Boundary Tests ---

    @Test
    fun title_and_description_length_limits() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        // Title <= 100 chars
        val exact100 = "a".repeat(100)
        viewModel.onTitleChanged(exact100)
        assertEquals(100, viewModel.uiState.value.title.length)

        viewModel.onTitleChanged("a".repeat(101))
        assertEquals(100, viewModel.uiState.value.title.length) // Rejected change

        // Description <= 1000 chars
        val exact1000 = "d".repeat(1000)
        viewModel.onDescriptionChanged(exact1000)
        assertEquals(1000, viewModel.uiState.value.description.length)

        viewModel.onDescriptionChanged("d".repeat(1001))
        assertEquals(1000, viewModel.uiState.value.description.length) // Rejected change
    }

    // --- Publishing Flow Tests ---

    @Test
    fun publish_sets_seller_campus_default_and_publishes_listing() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        // Default campus populated from auth user
        assertEquals("Baruch College", viewModel.uiState.value.campus)

        viewModel.onTitleChanged("Calculus Vol 1")
        viewModel.onPriceChanged("35.00")
        viewModel.onConditionSelected(Condition.LIKE_NEW.label)
        viewModel.onCategorySelected(Category.TEXTBOOKS.label)
        viewModel.onDescriptionChanged("Like new condition with minimal highlighting.")

        var navigationInvoked = false
        viewModel.publishListing(onSuccess = { navigationInvoked = true })
        testScheduler.runCurrent()

        assertTrue("onSuccess navigation callback must be called", navigationInvoked)
        assertEquals(1, listingRepo.createdListings.size)

        val created = listingRepo.createdListings.first()
        assertEquals("Calculus Vol 1", created.title)
        assertEquals(35.0, created.price, 0.001)
        assertEquals(Condition.LIKE_NEW.label, created.condition)
        assertEquals(Category.TEXTBOOKS.label, created.category)
        assertEquals("Baruch College", created.campus)
        assertEquals("Like new condition with minimal highlighting.", created.description)
        assertEquals("active", created.status)
    }

    @Test
    fun publish_cleans_up_storage_photos_on_firestore_failure() = runTest(testDispatcher) {
        listingRepo.shouldFailCreate = true
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.onPhotosSelected(listOf(Uri.EMPTY))
        viewModel.onTitleChanged("Physics Notes")
        viewModel.onPriceChanged("10.00")

        var navigationInvoked = false
        viewModel.publishListing(onSuccess = { navigationInvoked = true })
        testScheduler.runCurrent()

        assertFalse("onSuccess must not be called on failure", navigationInvoked)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Firestore write error", viewModel.uiState.value.errorMessage)

        // Uploaded photos were cleaned up via deletePhotos
        assertTrue("Storage photos must be deleted on listing failure", storageRepo.deletedUrls.isNotEmpty())
    }

    @Test
    fun session_change_during_upload_aborts_publication() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.onPhotosSelected(listOf(Uri.EMPTY))
        viewModel.onTitleChanged("Draft Under User A")
        viewModel.onPriceChanged("20.00")

        // Switch user session mid-upload
        storageRepo.onUploadHook = {
            authRepo.emitUser(User(id = "different-user-uid", displayName = "User B", campus = "Queens College"))
        }

        viewModel.publishListing(onSuccess = {})
        testScheduler.runCurrent()

        // Listing was not created under different user
        assertEquals(0, listingRepo.createdListings.size)
    }

    @Test
    fun committed_listing_does_not_delete_photos_if_navigation_callback_throws() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.onPhotosSelected(listOf(Uri.EMPTY))
        viewModel.onTitleChanged("Committed Item")
        viewModel.onPriceChanged("15.00")

        try {
            viewModel.publishListing(onSuccess = { throw RuntimeException("Navigation error") })
            testScheduler.runCurrent()
        } catch (_: Exception) {}

        // Listing was created in Firestore
        assertEquals(1, listingRepo.createdListings.size)
        // Photos were NOT deleted because listing is committed
        assertEquals(0, storageRepo.deletedUrls.size)
    }

    @Test
    fun user_signout_resets_form_and_cleans_draft() = runTest(testDispatcher) {
        val viewModel = CreateListingViewModel(listingRepo, storageRepo, authRepo)
        testScheduler.runCurrent()

        viewModel.onTitleChanged("Draft item")
        viewModel.onPriceChanged("50.00")
        viewModel.onDescriptionChanged("Some description")

        // User signs out
        authRepo.signOut()
        testScheduler.runCurrent()

        // UI state was fully reset
        assertEquals("", viewModel.uiState.value.title)
        assertEquals("", viewModel.uiState.value.priceText)
        assertEquals("", viewModel.uiState.value.description)
        assertFalse(viewModel.uiState.value.isPublishEnabled)
    }
}
