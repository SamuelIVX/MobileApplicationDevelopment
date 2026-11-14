# Spec: Profile & My Listings

## Applicable Skills
- `mobile-android-design`: Jetpack Compose Material 3 profile card, metrics summary rows, swipe/action item lists, confirmation dialogs.
- `owasp-security`: OWASP Top 10 A01 (Broken Access Control), server-enforced owner-only listing mutation and deletion.
- `better-accessibility`: Accessible button targets (min 48dp on status and trash icons), accessible dialog announcements, WCAG contrast.
- `tdd`: Testing student identity state, owner listing filtering, status toggle transitions, and sign-out teardown.

## Objective
Implement the student profile and listing management screen: `ProfileScreen` (Tab 5 in bottom
navigation). The screen displays the student's institutional identity card (full name, campus, major,
**Gold Verified Student Badge `#FFB71B`**, overall rating, and sales count), provides an active
management portal for the student's posted listings ("My Listings" — with options to mark as sold
or delete), and houses the account Sign Out control.

## Scope
- Package: `com.cuny.exchange.ui.screens.profile`
- Modifies (creates):
  - `ui/screens/profile/ProfileScreen.kt`
  - `ui/screens/profile/ProfileViewModel.kt`
  - `ui/components/MyListingItem.kt` — manageable listing row with sold/delete actions
  - `ui/components/ProfileHeader.kt` — student identity banner with stats
- Modifies (existing):
  - `data/repository/ListingRepository.kt` — adds `getMyListingsFlow()`, `updateListingStatus(id, status)`, `deleteListing(id)`
- Off-limits: `03-auth` (auth logic); `05-create-listing` (creation form).

## Non-Goals
- No profile editing in v1 (name and campus are locked to the verified CUNY account).
- No payout account or banking configuration (peer-to-peer cash/Venmo in safe zones).
- No account deletion flow in this phase (managed via administrative request).

## Invariants
- A student MUST only be able to view, mark as sold, or delete listings where `sellerId == currentUser.uid`. [OWASP A01 / Oracle 4]
- Deleting a listing MUST update its status to `deletion-pending` to trigger backend storage cleanup. [storage hygiene invariant]
- Tapping "Sign Out" MUST clear the local Firebase Auth session and redirect the user back to `SignInScreen` clearing all backstacks. [session hygiene invariant]
- The Gold Verified Student Badge (`#FFB71B`) MUST be displayed on the student's profile card. [Oracle 5 / design invariant]

## Requirements
1. THE SYSTEM SHALL render `ProfileHeader` displaying:
   - Student initials avatar container (48x48dp)
   - Student display name (e.g. "Marcus K.")
   - Institutional affiliation: "[Campus] · [Major]" (e.g. "Brooklyn College · Biology major")
   - **Gold Verified Student Badge (`#FFB71B`)** labeled "Verified Student"
   - Metrics summary row:
     - Seller rating (e.g. "5.0 ★")
     - Items sold count (e.g. "7 sold")
     - Active listings count (e.g. "3 active").
2. THE SYSTEM SHALL query and display all listings created by the logged-in student (`sellerId == currentUser.uid`).
3. Each item row in My Listings SHALL display:
   - Thumbnail photo
   - Title and price formatted prominently
   - Status badge ("ACTIVE" in green, "SOLD" in muted grey, or "DELETING..." in amber)
   - Quick action controls (minimum 48dp touch targets):
     - **Toggle Status:** "Mark as Sold" if active; "Relist / Mark as Active" if sold (disabled if deletion-pending)
     - **Delete:** trash icon button (disabled if deletion-pending).
4. WHEN the student taps "Mark as Sold", THE SYSTEM SHALL update the listing document in Firestore (`status = "sold"`), reflecting immediately in the UI.
5. WHEN the student taps "Delete", THE SYSTEM SHALL display a native confirmation dialog:
   - Title: "Delete listing?"
   - Message: "Are you sure you want to permanently delete this listing? This cannot be undone."
   - Confirm button: "Delete" (in danger red `#D64045`)
   - Dismiss button: "Cancel".
6. WHEN deletion is confirmed, THE SYSTEM SHALL call `ListingRepository.deleteListing(listingId)` which sets `status = "deletion-pending"`, schedules photo cleanup, and removes the document from public queries.
7. WHEN the student has zero posted listings, THE SYSTEM SHALL display an empty state:
   - "You haven't listed any items for sale yet."
   - A button: "Sell an item" navigating to Tab 3 (`Sell`).
8. THE SYSTEM SHALL render an outlined "Sign Out" button at the bottom of the screen.
9. WHEN the student taps "Sign Out", THE SYSTEM SHALL call `AuthRepository.signOut()` and navigate back to `SignInScreen`, clearing all navigation backstacks.

## Failure Modes & Edge Cases
1. **Deletion Offline:** WHEN user confirms deletion without an internet connection, THE SYSTEM SHALL inform the user via Snackbar: "Network required to delete listing. Please check connection."
2. **Rapid Multi-Click on Status Toggle:** Status toggle actions SHALL debounce and disable the toggle button while the update coroutine is executing.
3. **Sign Out While Action In-Flight:** WHEN student taps "Sign Out" while a status update is in-flight, THE SYSTEM SHALL wait for or cancel the update safely before invalidating the auth token.

## Acceptance Criteria
- [ ] Profile header renders student name, campus, major, and Gold Verified badge (`#FFB71B`).
- [ ] My Listings shows only items owned by the authenticated student.
- [ ] Tapping "Mark as Sold" updates listing status badge to "SOLD".
- [ ] Tapping "Relist" flips status badge back to "ACTIVE".
- [ ] Tapping trash icon opens confirmation dialog with "Delete" and "Cancel" actions.
- [ ] Tapping "Sign Out" clears session and returns to `SignInScreen`.

## Design

### `ui/screens/profile/ProfileViewModel.kt`
```kotlin
package com.cuny.exchange.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val myListings: List<Listing> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialogForId: String? = null,
    val pendingMutationListingIds: Set<String> = emptySet(),
    val errorMessage: String? = null
) {
    val activeCount: Int get() = myListings.count { it.status == "active" }
    val soldCount: Int get() = myListings.count { it.status == "sold" }
}

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val listingRepository: ListingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private val activeJobs = mutableListOf<kotlinx.coroutines.Job>()
    private var isSigningOut = false

    private fun loadProfile() {
        viewModelScope.launch {
            authRepository.currentUserFlow
                .filterNotNull()
                .flatMapLatest { user ->
                    listingRepository.getMyListingsFlow(user.id).map { listings ->
                        user to listings
                    }
                }
                .collect { (user, listings) ->
                    _uiState.update {
                        it.copy(user = user, myListings = listings, isLoading = false)
                    }
                }
        }
    }

    fun toggleListingStatus(listing: Listing) {
        if (isSigningOut || _uiState.value.pendingMutationListingIds.contains(listing.id)) return
        val newStatus = if (listing.status == "sold") "active" else "sold"
        _uiState.update { it.copy(pendingMutationListingIds = it.pendingMutationListingIds + listing.id) }
        val job = viewModelScope.launch {
            try {
                val result = listingRepository.updateListingStatus(listing.id, newStatus)
                if (result.isFailure) {
                    _uiState.update { it.copy(errorMessage = "Failed to update listing status") }
                }
            } finally {
                _uiState.update { it.copy(pendingMutationListingIds = it.pendingMutationListingIds - listing.id) }
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    fun requestDelete(listingId: String) {
        _uiState.update { it.copy(showDeleteDialogForId = listingId) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialogForId = null) }
    }

    fun confirmDelete() {
        val listingId = _uiState.value.showDeleteDialogForId ?: return
        if (isSigningOut || _uiState.value.pendingMutationListingIds.contains(listingId)) return
        _uiState.update { it.copy(showDeleteDialogForId = null, pendingMutationListingIds = it.pendingMutationListingIds + listingId) }
        val job = viewModelScope.launch {
            try {
                val result = listingRepository.deleteListing(listingId)
                if (result.isFailure) {
                    _uiState.update { it.copy(errorMessage = "Failed to delete listing. Please check connection.") }
                }
            } finally {
                _uiState.update { it.copy(pendingMutationListingIds = it.pendingMutationListingIds - listingId) }
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        if (isSigningOut) return
        isSigningOut = true
        viewModelScope.launch {
            activeJobs.toList().forEach { it.join() }
            authRepository.signOut()
            onSignedOut()
        }
    }
}
```

## Current State
- `[verified]` `ProfileScreen` is greenfield.
- `[verified]` Matches the audited wireframe profile specification.

## Tests
- `profile_renders_student_identity (R1)` — displays student name, campus, major, and gold verified badge.
- `my_listings_displays_only_owned_items (R2)` — querying `getMyListings()` strictly filters by `sellerId == currentUser.uid`. Mutant check: ensure `sellerId == user.id` cannot be bypassed.
- `toggle_status_updates_listing (R4)` — toggling from active to sold updates `status = "sold"`.
- `delete_prompts_confirmation (R5)` — clicking delete opens confirmation alert dialog.
- `sign_out_redirects_to_auth (R9)` — tapping sign out invokes `AuthRepository.signOut()` and exits authenticated shell.

## Constraints
- Dependencies: `02-data-model` and `03-auth` must merge first.
- Downstream: `11-submission` references profile verification and listings management.
- Oracle Contract: Set-level Oracle 4 (Security Rules) and Oracle 5 (Rating Calculation) in `README.md`.

## Context
- Jetpack Compose AlertDialog: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#AlertDialog
