# Spec: Listing Detail

## Applicable Skills
- `mobile-android-design`: Jetpack Compose `HorizontalPager` photo carousel, Material 3 card layouts, sticky bottom action bar, theme tokens.
- `better-accessibility`: Accessible pager indicators, content descriptions on photos and action buttons, WCAG contrast on gold badge and safe zone card.
- `codebase-design`: `ListingRepository` detail lookup, thread deduplication / creation seam.
- `tdd`: Testing detail state transitions, owner vs buyer button actions, and fallback states.

## Objective
Implement the drill-in `ListingDetailScreen` displaying comprehensive information for a selected
listing. The screen features a multi-photo carousel, item condition and price badges, a seller
trust card featuring the **Gold Verified Student Badge (`#FFB71B`)** and seller rating, a prominent
campus Safe Meetup Zone callout, the complete item description, and a persistent bottom action bar
with a heart bookmark toggle and a primary **"Message Seller"** button that navigates directly into
the chat experience.

## Scope
- Package: `com.cuny.exchange.ui.screens.detail`
- Modifies (creates):
  - `ui/screens/detail/ListingDetailScreen.kt`
  - `ui/screens/detail/ListingDetailViewModel.kt`
  - `ui/components/PhotoCarousel.kt` — HorizontalPager photo carousel with indicator dots
  - `ui/components/SellerCard.kt` — seller profile card with gold verified badge and rating
  - `ui/components/SafeZoneCallout.kt` — highlighted campus safe meetup instructions box
- Modifies (existing):
  - `data/repository/ListingRepository.kt` — adds `getListingById(id)`
- Off-limits: `04-home-feed` (feed list); `07-search-and-filter` (search); `09-stretch-inbox-chat` (chat screen implementation).

## Non-Goals
- No payment integration — transactions settle peer-to-peer in person at safe zones.
- No public comment section (communication occurs privately via buyer-seller threads).
- No image zoom/pan gestures in this phase.

## Invariants
- The Verified Student Badge MUST be rendered in Gold (`#FFB71B`) on the seller card. [Oracle 5 / wireframe design invariant]
- The Safe Meetup Zone box MUST be rendered on every listing detail screen. [safety invariant]
- If `listing.sellerId == currentUser.uid` (the user is viewing their own listing), the system MUST hide the "Message Seller" button and instead display "Mark as Sold" and "Delete Listing" actions. [domain invariant]
- Tapping "Message Seller" MUST navigate to `ChatScreen` with the active thread ID when in-app messaging is active, or trigger an institutional email intent (`mailto:`) to the verified seller as a fallback when chat is omitted. [contact invariant]
- Carousel indicator dots MUST be hidden when the listing contains fewer than 2 photos. [UI hygiene invariant]

## Requirements
1. WHEN `ListingDetailScreen` is opened with a `listingId`, THE SYSTEM SHALL query Firestore via `ListingRepository.getListingById(listingId)` and display a loading skeleton until resolved.
2. THE SYSTEM SHALL render a top app bar with:
   - A Back button returning to the previous screen (Home, Search, or Profile)
   - A Share button invoking Android system share sheet with listing details
   - An interactive heart/save icon toggling bookmark state.
3. WHEN a listing contains two or more photos, THE SYSTEM SHALL display a swipeable `HorizontalPager` with indicator dots at the bottom.
4. WHEN a listing contains exactly one photo, THE SYSTEM SHALL display that photo without pager dots.
5. WHEN a listing contains zero photos, THE SYSTEM SHALL display a branded placeholder container with a category icon.
6. THE SYSTEM SHALL display item details:
   - Price formatted prominently (e.g. `$85` or `Free` if `0.0`)
   - Title in headline typography
   - Condition badge (`New`, `Like New`, `Good`, `Fair`) as a pill chip
   - Category and Campus badges
   - Description with full text wrap.
7. THE SYSTEM SHALL render `SellerCard` displaying:
   - Seller avatar / initial icon
   - Seller display name (e.g. "Aria M.")
   - Major (e.g. "Finance major")
   - Overall seller rating with star icon (e.g. "4.9 ★")
   - Completed items sold count (e.g. "12 sold")
   - **Gold Verified Student Badge (`#FFB71B`)** labeled "Verified Student".
8. THE SYSTEM SHALL render a highlighted container (`SafeZoneCallout`) with a security shield icon:
   - Title: "Safe meetup zone"
   - Instructions: e.g. *"Meet inside the Baruch Newman Library lobby (security desk) to inspect and swap."*
   - Safety rule: *"Always meet in daylight. Inspect before paying. No advance deposits."*
9. THE SYSTEM SHALL render a sticky bottom action bar:
   - When viewing another student's listing (`sellerId != currentUser.uid`):
     - Secondary button: Heart / Save toggle (minimum 48dp touch target)
     - Primary button: "Message Seller" in CUNY Blue (`#0033A1`).
   - When viewing own listing (`sellerId == currentUser.uid`):
     - Primary button: "Mark as Sold" (or "Mark as Active" if already sold)
     - Secondary button: "Delete Listing" (styled with danger red outline `#D64045`).
10. WHEN the student taps "Message Seller", THE SYSTEM SHALL atomically check for or create a single thread for the `(listingId, buyerId)` pair and navigate to `ChatScreen` with that thread's ID when stretch chat is enabled, OR launch a system `mailto:` intent pre-addressed to the seller's verified `.cuny.edu` email (`listing.sellerEmail`) when in-app chat is disabled.

## Failure Modes & Edge Cases
1. **Listing Not Found (Deleted):** WHEN `getListingById()` returns null or document does not exist, THE SYSTEM SHALL display an error state: "This listing is no longer available" with a button to return to Home.
2. **Network Interruption:** WHEN Firestore query times out or fails, THE SYSTEM SHALL display a retry screen with an actionable "Try Again" button.
3. **Seller Views Own Item:** WHEN the logged-in user is the seller, THE SYSTEM SHALL strictly hide "Message Seller" to prevent self-messaging deadlocks.
4. **Item Sold While Viewing:** WHEN a listing is marked sold by the seller while a prospective buyer is viewing the screen, real-time snapshot updates SHALL immediately disable "Message Seller" and render a "SOLD" banner.

## Acceptance Criteria
- [ ] Detail screen displays item title, price, condition, category, description, and campus.
- [ ] Multi-photo listings display swipeable pager with dots; single photo listings show no dots.
- [ ] Safe meetup zone callout box renders with campus specific instructions.
- [ ] Seller card displays Gold Verified Student badge (`#FFB71B`) and rating.
- [ ] Viewing own listing hides "Message Seller" and shows "Mark as Sold" / "Delete Listing".
- [ ] Tapping "Message Seller" creates or opens thread and routes to `ChatScreen`.

## Design

### `ui/screens/detail/ListingDetailViewModel.kt`
```kotlin
package com.cuny.exchange.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ListingDetailUiState(
    val listing: Listing? = null,
    val currentUser: User? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isOwner: Boolean get() = listing != null && currentUser != null && listing.sellerId == currentUser.id
}

class ListingDetailViewModel(
    private val listingId: String,
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListingDetailUiState())
    val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

    init {
        loadListingDetails()
    }

    private fun loadListingDetails() {
        viewModelScope.launch {
            authRepository.currentUserFlow.filterNotNull().flatMapLatest { user ->
                combine(
                    listingRepository.getListingById(listingId),
                    listingRepository.isBookmarkedFlow(user.id, listingId)
                ) { listing, isSaved ->
                    if (listing == null) {
                        ListingDetailUiState(isLoading = false, errorMessage = "Listing not found")
                    } else {
                        ListingDetailUiState(
                            listing = listing,
                            currentUser = user,
                            isSaved = isSaved,
                            isLoading = false
                        )
                    }
                }
            }.collect { state -> _uiState.value = state }
        }
    }

    fun toggleSave() {
        viewModelScope.launch {
            listingRepository.toggleBookmark(listingId)
        }
    }

    fun markAsSold() {
        viewModelScope.launch {
            listingRepository.updateListingStatus(listingId, "sold")
        }
    }

    fun markAsActive() {
        viewModelScope.launch {
            listingRepository.updateListingStatus(listingId, "active")
        }
    }
}
```

### `ui/components/SafeZoneCallout.kt`
```kotlin
@Composable
fun SafeZoneCallout(
    safeZoneText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Safe Meetup Zone Shield Icon",
                tint = CunyBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Safe meetup zone",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF0A0A0A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = safeZoneText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Always meet in daylight. Inspect item before paying. No advance deposits.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
```

## Current State
- `[verified]` `ListingDetailScreen` is greenfield.
- `[verified]` Wireframe mandates Gold Verified badge and safe meetup zone container on all listing drill-in screens.

## Tests
- `detail_renders_seller_info (R7)` — verify seller display name, major, rating, and verified badge render.
- `detail_shows_safe_zone (R8)` — verify the campus safe meetup zone box is displayed.
- `message_seller_hidden_for_owner (R9)` — when `listing.sellerId == currentUser.uid`, "Message Seller" is not rendered. Mutant check: `==` vs `!=`.
- `owner_sees_mark_sold_and_delete (R9)` — owner views listing and sees status action buttons.
- `pager_dots_hidden_for_single_photo (R4)` — listing with 1 photo does not render pager dots.
- `message_seller_creates_or_opens_thread (R10)` — tapping "Message Seller" routes to `ChatScreen` with thread ID.

## Constraints
- Dependencies: `04-home-feed` and `05-create-listing` must merge first.
- Downstream: `09-stretch-inbox-chat` is launched from "Message Seller".
- Oracle Contract: Set-level Oracle 4 (Security Rules) and Oracle 5 (Rating) in `README.md`.

## Context
- Jetpack Compose HorizontalPager: https://developer.android.com/develop/ui/compose/layouts/pager
