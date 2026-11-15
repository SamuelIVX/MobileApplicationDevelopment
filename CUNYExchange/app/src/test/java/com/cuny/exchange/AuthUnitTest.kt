package com.cuny.exchange

import com.cuny.exchange.data.model.Campus
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.isCunyEmail
import com.cuny.exchange.ui.screens.auth.AuthState
import com.cuny.exchange.ui.screens.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Regex Unit Tests ──────────────────────────────────────────────

    @Test
    fun cuny_regex_accepts_valid_login_cuny_edu() {
        assertTrue(isCunyEmail("marta.smith56@login.cuny.edu"))
    }

    @Test
    fun cuny_regex_accepts_valid_campus_cuny_edu() {
        assertTrue(isCunyEmail("samuel@csi.cuny.edu"))
        assertTrue(isCunyEmail("alex@baruch.cuny.edu"))
        assertTrue(isCunyEmail("jane.doe@hunter.cuny.edu"))
        assertTrue(isCunyEmail("student@citytech.cuny.edu"))
    }

    @Test
    fun cuny_regex_accepts_root_cuny_edu() {
        assertTrue(isCunyEmail("staff@cuny.edu"))
    }

    @Test
    fun cuny_regex_caseInsensitive() {
        assertTrue(isCunyEmail("STUDENT@HUNTER.CUNY.EDU"))
        assertTrue(isCunyEmail("Jane.Doe@Login.Cuny.Edu"))
    }

    @Test
    fun cuny_regex_rejects_gmail_and_other_providers() {
        assertFalse(isCunyEmail("student@gmail.com"))
        assertFalse(isCunyEmail("student@yahoo.com"))
        assertFalse(isCunyEmail("student@nyu.edu"))
        assertFalse(isCunyEmail("student@columbia.edu"))
    }

    @Test
    fun cuny_regex_rejects_subdomain_spoof() {
        assertFalse(isCunyEmail("attacker@cuny.edu.attacker.com"))
        assertFalse(isCunyEmail("victim@cuny.edu.phishing.org"))
        assertFalse(isCunyEmail("student@evilcuny.edu"))
        assertFalse(isCunyEmail("student@notcuny.edu"))
        assertFalse(isCunyEmail("a@evil.com@cuny.edu"))
    }

    @Test
    fun cuny_regex_trims_surrounding_whitespace() {
        assertTrue(isCunyEmail("  student@csi.cuny.edu  "))
        assertTrue(isCunyEmail("\tsamuel@baruch.cuny.edu\n"))
    }

    @Test
    fun cuny_regex_rejects_trailing_char() {
        assertFalse(isCunyEmail("student@cuny.edux"))
        assertFalse(isCunyEmail("student@csi.cuny.education"))
    }

    @Test
    fun cuny_regex_rejects_malformed_inputs() {
        assertFalse(isCunyEmail(""))
        assertFalse(isCunyEmail("   "))
        assertFalse(isCunyEmail("notanemail"))
        assertFalse(isCunyEmail("@cuny.edu"))
        assertFalse(isCunyEmail("student@"))
    }

    // ── ViewModel & State Tests with Fake Repository ──────────────────

    @Test
    fun signIn_validationAndSubmissionFlow() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        // Initial state
        assertFalse(viewModel.signInUiState.value.isEmailValid)
        assertFalse(viewModel.signInUiState.value.isLinkSent)

        // Invalid email input
        viewModel.onEmailChanged("student@gmail.com")
        assertFalse(viewModel.signInUiState.value.isEmailValid)
        assertEquals("Must end in .cuny.edu", viewModel.signInUiState.value.emailError)

        // Valid CUNY email input
        viewModel.onEmailChanged("student@baruch.cuny.edu")
        assertTrue(viewModel.signInUiState.value.isEmailValid)
        assertNull(viewModel.signInUiState.value.emailError)

        // Submit link
        viewModel.sendSignInLink()
        testScheduler.runCurrent()

        assertTrue(viewModel.signInUiState.value.isLinkSent)
        assertEquals("student@baruch.cuny.edu", viewModel.checkEmailUiState.value.email)
        assertEquals(60, viewModel.checkEmailUiState.value.cooldownSeconds)
    }

    @Test
    fun resend_button_disabled_during_cooldown() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        testScheduler.runCurrent()

        viewModel.onEmailChanged("student@csi.cuny.edu")
        viewModel.sendSignInLink()
        testScheduler.runCurrent()

        assertEquals(60, viewModel.checkEmailUiState.value.cooldownSeconds)

        // Advance 10 seconds
        advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals(50, viewModel.checkEmailUiState.value.cooldownSeconds)

        // Resend while cooldown active should be ignored
        val initialCalls = fakeRepo.sendLinkCallCount
        viewModel.resendSignInLink()
        testScheduler.runCurrent()
        assertEquals(initialCalls, fakeRepo.sendLinkCallCount)

        // Advance remaining 50 seconds to complete cooldown
        advanceTimeBy(50_000)
        testScheduler.runCurrent()
        assertEquals(0, viewModel.checkEmailUiState.value.cooldownSeconds)

        // Now resend should work and reset timer
        viewModel.resendSignInLink()
        testScheduler.runCurrent()
        assertEquals(initialCalls + 1, fakeRepo.sendLinkCallCount)
        assertEquals(60, viewModel.checkEmailUiState.value.cooldownSeconds)
    }

    @Test
    fun verify_email_link_routes_to_onboarding_when_profile_incomplete() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.verifyEmailLink("student@qc.cuny.edu", "https://cuny-exchange-demo.firebaseapp.com/__/auth/links?apiKey=xyz")
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.NeedsOnboarding)
        val needsOnboarding = viewModel.authState.value as AuthState.NeedsOnboarding
        assertEquals("user123", needsOnboarding.uid)
    }

    @Test
    fun onboarding_submission_completes_and_authenticates() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.verifyEmailLink("alex@baruch.cuny.edu", "https://cuny-exchange-demo.firebaseapp.com/__/auth/links?apiKey=xyz")
        advanceUntilIdle()
        assertTrue(viewModel.authState.value is AuthState.NeedsOnboarding)

        viewModel.onDisplayNameChanged("Alex Morgan")
        viewModel.onMajorChanged("Information Systems")
        viewModel.onCampusSelected("Baruch College")

        assertTrue(viewModel.onboardingUiState.value.canSubmit)

        viewModel.completeOnboarding("user123")
        advanceUntilIdle()

        assertTrue(viewModel.onboardingUiState.value.isComplete)
        assertTrue(viewModel.authState.value is AuthState.Authenticated)
        val authenticated = viewModel.authState.value as AuthState.Authenticated
        assertEquals("Alex Morgan", authenticated.user.displayName)
        assertEquals("Baruch College", authenticated.user.campus)
        assertEquals("Information Systems", authenticated.user.major)
        assertTrue(authenticated.user.isVerified)
    }

    @Test
    fun sign_out_clears_session() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        // Sign in user
        fakeRepo.emitUser(
            User(
                id = "user1",
                displayName = "Sam",
                campus = "Brooklyn College",
                major = "CS",
                isVerified = true
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Authenticated)

        viewModel.signOut()
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun cross_device_deep_link_prompts_for_email_and_completes_verification() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        val mockLink = "https://cuny-exchange-demo.firebaseapp.com/__/auth/links?apiKey=123"

        // Simulated cross-device: cachedEmail is null
        viewModel.onDeepLinkReceived(mockLink, cachedEmail = null)
        assertTrue(viewModel.signInUiState.value.showCrossDeviceEmailPrompt)
        assertEquals(mockLink, viewModel.signInUiState.value.pendingDeepLink)

        // Invalid email entered
        viewModel.onCrossDeviceEmailChanged("attacker@gmail.com")
        viewModel.submitCrossDeviceEmail()
        assertTrue(viewModel.signInUiState.value.showCrossDeviceEmailPrompt)
        assertEquals("Must end in .cuny.edu", viewModel.signInUiState.value.dialogError)

        // Valid CUNY email entered
        viewModel.onCrossDeviceEmailChanged("student@hunter.cuny.edu")
        viewModel.submitCrossDeviceEmail()
        advanceUntilIdle()

        assertFalse(viewModel.signInUiState.value.showCrossDeviceEmailPrompt)
        assertTrue(viewModel.authState.value is AuthState.NeedsOnboarding)
    }

    @Test
    fun deep_link_deduplication_prevents_multiple_verifications() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        val mockLink = "https://cuny-exchange-demo.firebaseapp.com/__/auth/links?apiKey=repeat"
        viewModel.onDeepLinkReceived(mockLink, cachedEmail = "student@baruch.cuny.edu")
        advanceUntilIdle()
        assertEquals(1, fakeRepo.verifyLinkCallCount)

        // Second call with same link should be ignored by deduplication
        viewModel.onDeepLinkReceived(mockLink, cachedEmail = "student@baruch.cuny.edu")
        advanceUntilIdle()
        assertEquals(1, fakeRepo.verifyLinkCallCount)
    }

    @Test
    fun deep_link_retry_after_failure_succeeds() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        val mockLink = "https://cuny-exchange-demo.firebaseapp.com/__/auth/links?apiKey=fail_first"

        // First attempt fails
        fakeRepo.shouldFail = true
        viewModel.onDeepLinkReceived(mockLink, cachedEmail = "student@baruch.cuny.edu")
        advanceUntilIdle()
        assertEquals(1, fakeRepo.verifyLinkCallCount)
        assertNotNull(viewModel.signInUiState.value.errorMessage)

        // Retry of same link succeeds and is not blocked by deduplication
        fakeRepo.shouldFail = false
        viewModel.onDeepLinkReceived(mockLink, cachedEmail = "student@baruch.cuny.edu")
        advanceUntilIdle()
        assertEquals(2, fakeRepo.verifyLinkCallCount)
        assertTrue(viewModel.authState.value is AuthState.NeedsOnboarding)
    }

    @Test
    fun onboarding_viewmodel_propagates_existing_user_stats() = runTest {
        val fakeRepo = FakeAuthRepository()
        // Pre-populate existing user with custom stats
        fakeRepo.emitUser(
            User(
                id = "user123",
                displayName = "Old Name",
                rating = 4.8,
                soldCount = 14,
                isVerified = true
            )
        )
        val viewModel = AuthViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.onDisplayNameChanged("Updated Name")
        viewModel.onMajorChanged("Finance")
        viewModel.onCampusSelected("Baruch College")

        viewModel.completeOnboarding("user123")
        advanceUntilIdle()

        val authenticated = viewModel.authState.value as AuthState.Authenticated
        assertEquals("Updated Name", authenticated.user.displayName)
        assertEquals(4.8, authenticated.user.rating, 0.001)
        assertEquals(14, authenticated.user.soldCount)
        assertEquals("Baruch College", authenticated.user.campus)
    }
}

private class FakeAuthRepository : AuthRepository {
    private val _currentUserFlow = MutableStateFlow<User?>(null)
    override val currentUserFlow: Flow<User?> = _currentUserFlow
    override val currentUserId: String? get() = _currentUserFlow.value?.id

    var sendLinkCallCount = 0
    var verifyLinkCallCount = 0
    var shouldFail = false

    fun emitUser(user: User?) {
        _currentUserFlow.value = user
    }

    override suspend fun sendSignInLink(email: String): Result<Unit> {
        sendLinkCallCount++
        return if (shouldFail) Result.failure(RuntimeException("Network error")) else Result.success(Unit)
    }

    override suspend fun verifyEmailLink(email: String, link: String): Result<User> {
        verifyLinkCallCount++
        return if (shouldFail) {
            Result.failure(RuntimeException("Invalid link"))
        } else {
            // Return uncompleted user needing onboarding
            val user = User(id = "user123", isVerified = true)
            _currentUserFlow.value = user
            Result.success(user)
        }
    }

    override suspend fun getUserProfile(userId: String): Result<User?> {
        return if (shouldFail) {
            Result.failure(RuntimeException("Database unreachable"))
        } else {
            Result.success(_currentUserFlow.value)
        }
    }

    override suspend fun saveUserProfile(
        userId: String,
        displayName: String,
        campus: String,
        major: String
    ): Result<User> {
        val existing = _currentUserFlow.value
        val updated = User(
            id = userId,
            displayName = displayName,
            campus = campus,
            major = major,
            rating = existing?.rating ?: 5.0,
            soldCount = existing?.soldCount ?: 0,
            isVerified = true
        )
        _currentUserFlow.value = updated
        return Result.success(updated)
    }

    override suspend fun signOut() {
        _currentUserFlow.value = null
    }

    override fun isEmailValid(email: String): Boolean = isCunyEmail(email)
}
