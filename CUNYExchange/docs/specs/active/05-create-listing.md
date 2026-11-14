# Spec: Create Listing Form

## Applicable Skills
- `mobile-android-design`: Jetpack Compose form architecture, Photo Picker contract integration, chip selectors, responsive scrolling.
- `owasp-security`: File upload security, client-side size limits (<=1MB), image format validation, authenticated user injection (`sellerId`).
- `better-accessibility`: Accessible form field labeling, error announcements, minimum 48dp touch targets on photo removal buttons.
- `tdd`: Testing form validation state matrix, image compression boundaries, and error rollback.

## Objective
Implement the "Sell" screen (`CreateListingScreen`, Tab 3 in bottom navigation).
Students can create and publish an item for sale by uploading up to 5 photos, specifying a title,
price, condition, campus, category, and description. Before upload, images are compressed
client-side to preserve Firebase free-tier storage. The form enforces strict validation rules
(disabling the Publish button until all required fields are valid). Upon publishing, the document
is written to Firestore and the user is redirected directly to the Home feed where their new listing
appears immediately at the top.

## Scope
- Package: `com.cuny.exchange.ui.screens.create` & `com.cuny.exchange.data.repository`
- Modifies (creates):
  - `ui/screens/create/CreateListingScreen.kt`
  - `ui/screens/create/CreateListingViewModel.kt`
  - `data/repository/StorageRepository.kt` — image compression & Firebase Storage upload
  - `ui/components/PhotoPickerGrid.kt` — 5-photo upload thumbnail grid composable
- Modifies (existing):
  - `data/repository/ListingRepository.kt` — adds `createListing(listing)`
- Off-limits: `03-auth` (authentication); `04-home-feed` (feed rendering); `06-listing-detail` (detail view).

## Non-Goals
- No draft saving across app restarts (v1 resets the form upon successful publish).
- No video or document uploads — JPEG/PNG photos only.
- No shipping or delivery fee calculation (in-person on-campus pickup only).

## Invariants
- A listing MUST contain between 0 and 5 photos maximum (`photoUris.size in 0..5`). [Oracle 3]
- `price` MUST be a valid, finite non-negative number (`price.isFinite() && price >= 0.0`), with at most 2 decimal places. [Oracle 2]
- The "Publish" button MUST be disabled if `title.isBlank()` OR `price` is invalid, infinite, or negative. [form validation invariant]
- Storage upload MUST be atomic: if any photo upload fails during batch processing, `StorageRepository.uploadPhotos` MUST clean up any already-uploaded photos in that batch before rethrowing. [storage integrity]
- Listing publication MUST populate required Firestore fields (`id`, `sellerId`, `sellerName`, `sellerEmail`, `sellerMajor`, `sellerRating`, `safeZone`) from the authenticated student profile and campus lookup before writing to `/listings`. [Firestore security rules compliance]
- All photos MUST be compressed client-side to <= 1MB and 1200x1200px max dimensions prior to uploading. [Oracle 3 / storage quota protection]
- The `sellerId` assigned to the listing MUST come directly from `FirebaseAuth.currentUser.uid` — never a client-supplied form field. [OWASP A01 / Oracle 4]
- Tapping "Publish" MUST immediately disable the button to prevent duplicate submissions. [concurrency / idempotency invariant]

## Requirements
1. THE SYSTEM SHALL display a photo upload section with an "Add photos (up to 5)" button and thumbnail previews.
2. WHEN the student taps "Add photos", THE SYSTEM SHALL launch the Android Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)`).
3. WHEN photos are selected, THE SYSTEM SHALL display them in a horizontal scroll row with individual "Remove" (✕) overlay buttons.
4. WHEN the current photo count reaches 5, THE SYSTEM SHALL hide or disable the "Add photos" button.
5. THE SYSTEM SHALL compress selected images to JPEG format at 80% quality and scale dimensions to a maximum of 1200x1200px, guaranteeing the resulting file is <= 1 MB before upload.
6. THE SYSTEM SHALL provide the following form inputs:
   - **Title:** single-line text field, required, max 100 chars
   - **Price:** decimal text field with leading `$`, required, must parse to a valid float/double >= 0.0
   - **Condition:** single-choice chip group: `New`, `Like New`, `Good`, `Fair` (defaults to `Good`)
   - **Campus:** dropdown selector populated from CUNY campus list, defaulting to the student's registered campus
   - **Category:** dropdown selector: `Textbooks`, `Calculators`, `Electronics`, `Clothing`, `Furniture`, `Other` (defaults to `Textbooks`)
   - **Description:** multi-line text area, optional, max 1000 chars.
7. WHEN `title.isBlank()` OR `price` is invalid/negative, THE SYSTEM SHALL disable the "Publish" button.
8. WHEN all required fields are valid, THE SYSTEM SHALL enable the "Publish" button with CUNY Blue ground (`#0033A1`).
9. WHEN the student taps "Publish", THE SYSTEM SHALL:
   - Immediately disable the button and show a progress modal ("Uploading photos & publishing...")
   - Generate a new `Listing` document reference ID in Firestore
   - Upload compressed photos to Firebase Storage under `/listings/{listingId}/{index}.jpg`
   - Retrieve photo download URLs
   - Retrieve the campus default safe zone text for the selected campus
   - Write the new `Listing` document to Firestore with `sellerId = auth.currentUser.uid`, `status = "active"`, and server timestamp.
10. WHEN the Firestore write succeeds, THE SYSTEM SHALL reset the form fields, display a success toast ("Listing published!"), and navigate immediately to `HomeScreen` (Tab 1), where the new item appears on top.
11. WHEN an upload or network error occurs, THE SYSTEM SHALL dismiss the progress modal, preserve all entered form text, clean up any partially uploaded storage blobs, and display an error banner with a "Retry" button.

## Failure Modes & Edge Cases
1. **Network Disruption During Upload:** WHEN photo upload fails halfway through (e.g. photo 3 fails after photos 1 and 2 succeed), THE SYSTEM SHALL abort the transaction, delete orphaned photos 1 and 2 from Storage, and notify the user without creating a corrupt listing.
2. **Invalid Price Characters:** WHEN user inputs non-numeric characters (e.g. `12.3.4` or `abc`), THE SYSTEM SHALL mark the field invalid, display "Enter a valid price", and disable Publish.
3. **Out-of-Memory on Large Photos:** WHEN user picks five 50MP camera photos, the image pipeline SHALL decode bitmap bounds first (`inJustDecodeBounds = true`), subsample the decode, and scale safely to avoid heap OOM errors.
4. **Duplicate Submission Spam:** WHEN user taps "Publish" rapidly, the button SHALL disable synchronously on the first tap, discarding subsequent taps until the coroutine finishes.

## Acceptance Criteria
- [ ] Entering blank title disables the Publish button.
- [ ] Entering negative price (`-5`) or invalid format (`12..0`) disables Publish.
- [ ] Entering valid title and price (`20` or `0.00`) enables Publish.
- [ ] Photo Picker allows selecting up to 5 photos; selecting a 6th is rejected or blocked.
- [ ] Photos uploaded to Firebase Storage are verified to be <= 1MB each and <= 1200px max dimension.
- [ ] Published listing has `sellerId` matching the logged-in user and immediately appears on `HomeScreen`.

## Design

### `ui/screens/create/CreateListingViewModel.kt`
```kotlin
package com.cuny.exchange.ui.screens.create

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Condition
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.StorageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CreateListingUiState(
    val title: String = "",
    val priceText: String = "",
    val condition: String = Condition.GOOD.label,
    val campus: String = "",
    val category: String = Category.TEXTBOOKS.label,
    val description: String = "",
    val photoUris: List<Uri> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val parsedPrice: Double? get() {
        val trimmed = priceText.trim()
        val parts = trimmed.split(".")
        if (parts.size > 2) return null
        if (parts.size == 2 && parts[1].length > 2) return null
        return trimmed.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }
    val isPublishEnabled: Boolean
        get() = title.trim().isNotEmpty() &&
                parsedPrice != null &&
                parsedPrice!!.isFinite() &&
                parsedPrice!! >= 0.0 &&
                campus.isNotEmpty() &&
                photoUris.size <= 5
}

class CreateListingViewModel(
    private val listingRepository: ListingRepository,
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateListingUiState())
    val uiState: StateFlow<CreateListingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.filterNotNull().first().let { user ->
                _uiState.update { it.copy(campus = user.campus) }
            }
        }
    }

    fun onTitleChanged(title: String) {
        if (title.length <= 100) _uiState.update { it.copy(title = title) }
    }

    fun onPriceChanged(priceText: String) {
        _uiState.update { it.copy(priceText = priceText) }
    }

    fun onConditionSelected(condition: String) {
        _uiState.update { it.copy(condition = condition) }
    }

    fun onPhotosSelected(uris: List<Uri>) {
        _uiState.update { current ->
            val merged = (current.photoUris + uris).take(5)
            current.copy(photoUris = merged)
        }
    }

    fun removePhoto(index: Int) {
        _uiState.update { current ->
            val updated = current.photoUris.toMutableList().apply { removeAt(index) }
            current.copy(photoUris = updated)
        }
    }

    fun publishListing(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.isPublishEnabled || state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            var uploadedUrls: List<String> = emptyList()
            try {
                uploadedUrls = storageRepository.uploadPhotos(state.photoUris)
                val newListing = Listing(
                    title = state.title.trim(),
                    price = state.parsedPrice!!,
                    condition = state.condition,
                    category = state.category,
                    campus = state.campus,
                    description = state.description.trim(),
                    photoUrls = uploadedUrls,
                    status = "active"
                )
                listingRepository.createListing(newListing)
                _uiState.value = CreateListingUiState(campus = state.campus)
                onSuccess()
            } catch (e: Exception) {
                if (uploadedUrls.isNotEmpty()) {
                    runCatching { storageRepository.deletePhotos(uploadedUrls) }
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to publish listing") }
            }
        }
    }
}
```

## Current State
- `[verified]` `CreateListingScreen` is greenfield.
- `[verified]` Matches wireframe form layout, character limits, and disable conditions.

## Tests
- `publish_button_disabled_with_empty_title (R7)` — `title = ""` results in `isPublishEnabled == false`.
- `publish_button_disabled_with_whitespace_title (R7)` — `title = "   "` results in `isPublishEnabled == false`.
- `publish_button_disabled_with_negative_price (R7)` — `price = "-5"` results in `isPublishEnabled == false`. Mutant check: `< 0.0` vs `<= 0.0` (ensure `0.0` is enabled).
- `publish_button_enabled_with_zero_price (R7)` — `price = "0"` and non-empty title enables button (Free listing).
- `photo_count_capped_at_five (R4)` — adding 6 URIs results in exactly 5 kept. Mutant check: `take(5)`.
- `publish_sets_seller_id_from_auth (R9)` — created listing inherits UID from active auth session.
- `publish_redirects_to_home (R10)` — successful creation executes `onSuccess()` navigation callback.

## Constraints
- Dependencies: `02-data-model` and `03-auth` must merge first.
- Downstream: `04-home-feed` displays listings created by this spec.
- Oracle Contract: Set-level Oracle 2 (Price Rules) and Oracle 3 (Photo Limits) in `README.md`.

## Context
- Android Photo Picker API: https://developer.android.com/training/data-storage/shared/photopicker
- Firebase Storage Upload: https://firebase.google.com/docs/storage/android/upload-files
