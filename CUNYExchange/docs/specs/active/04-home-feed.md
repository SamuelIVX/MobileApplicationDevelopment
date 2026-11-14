# Spec: Home Feed & Safe Zones

## Applicable Skills
- `mobile-android-design`: Jetpack Compose `LazyVerticalGrid`, Material 3 cards, state hoisting, responsive grid columns.
- `better-accessibility`: Accessible touch targets (minimum 48dp on heart icon), dynamic content descriptions for save toggles, WCAG contrast.
- `codebase-design`: `ListingRepository` interface streaming `Flow<List<Listing>>`, unidirectional data flow (UDF) via `HomeUiState`.
- `tdd`: Testing feed partitioning and category filtering with deterministic fake repositories.

## Objective
Implement the main authenticated landing screen: `HomeScreen` (Tab 1 in bottom navigation).
The feed adopts a **campus-first architecture** reflecting the audited wireframe: displaying the
student's primary campus listings at the top, accompanied by an active on-campus Safe Meetup Zone
banner, followed by an "Across CUNY" section for the remaining 24 campuses. Students can filter
listings using horizontal category chips (`All`, `Textbooks`, `Calculators`, `Electronics`,
`Clothing`, `Furniture`, `Other`) and bookmark/save items with instant feedback.

## Scope
- Package: `com.cuny.exchange.ui.screens.home` & `com.cuny.exchange.ui.components`
- Modifies (creates):
  - `ui/screens/home/HomeScreen.kt`
  - `ui/screens/home/HomeViewModel.kt`
  - `ui/components/SafeZoneBanner.kt` — campus safe zone alert component
  - `ui/components/CategoryChips.kt` — horizontal pill filter list
  - `ui/components/ListingCard.kt` — individual item card with image, price, condition, seller rating
- Modifies (existing):
  - `data/repository/ListingRepository.kt` — declares listing stream and bookmark methods
- Off-limits: `03-auth` (authentication); `05-create-listing` (publishing form); `06-listing-detail` (drill-in details).

## Non-Goals
- No full-text search bar execution on this screen (search keyword input lives in Tab 2, `07-search-and-filter`).
- No infinite scroll pagination required for coursework scope — Firestore collection stream (limit 50 per partition) provides real-time responsiveness without paging library complexity.

## Invariants
- Listings MUST be partitioned into two distinct sections:
  1. *"At [Student's Campus]"* (e.g. Brooklyn College)
  2. *"Across CUNY"* (other 24 campuses). [wireframe invariant]
- Selecting a category chip MUST immediately filter both sections.
- Listing prices MUST be formatted with a leading dollar sign (`$`) using tabular figures (`tnum`) to prevent UI layout jitter. [Oracle 2]
- Tapping a listing card MUST navigate to `ListingDetailScreen` passing `listing.id`.
- Tapping the heart icon MUST toggle the bookmark state locally and persist it to the user's saved list.
- All interactive icons (heart button, chip selectors) MUST meet the minimum 48x48dp touch target standard. [better-accessibility / mobile-android-design]

## Requirements
1. WHEN the home feed is displayed, THE SYSTEM SHALL render `SafeZoneBanner` at the top showing:
   - "Your campus: [Campus Name]"
   - Highlighted safe zone text: e.g. "Safe zones today: Library lobby (8am–10pm) · Student Center desk"
   - Information shield icon.
2. WHEN the category chip row is rendered, THE SYSTEM SHALL display horizontally scrollable pills:
   `All`, `Textbooks`, `Calculators`, `Electronics`, `Clothing`, `Furniture`, `Other`.
3. WHEN a category chip is tapped, THE SYSTEM SHALL:
   - Highlight the chip with CUNY Blue ground (`#E6EEFA`) and primary border (`#0033A1`)
   - Filter the listing stream to display only items matching the selected category.
4. WHEN `All` is selected, THE SYSTEM SHALL display listings across all categories.
5. WHEN listings are retrieved, THE SYSTEM SHALL display them in a two-column responsive grid on phones (`GridCells.Fixed(2)` with 12dp spacing).
6. Each `ListingCard` SHALL display:
   - Primary item photo (using Coil with crossfade transition and loading placeholder)
   - Price formatted prominently (e.g. `$85` or `Free` if `price == 0.0`)
   - Item condition tag (`New`, `Like New`, `Good`, `Fair`) as a pill chip
   - Item title truncated to max 2 lines with ellipsis
   - Campus badge (e.g. `Baruch`, `Hunter`, `Brooklyn`)
   - Seller name and rating (e.g. `Aria M. · 4.9 ★`)
   - Interactive heart save button with accessible content description (`"Save [title]"` or `"Unsave [title]"`).
7. WHEN a listing has `status == "sold"`, THE SYSTEM SHALL render a semi-transparent "SOLD" overlay banner across the card image.
8. THE SYSTEM SHALL listen to Firestore collection updates via real-time SnapshotListener so newly published listings appear at the top of the feed without manual refresh.
9. WHEN no listings exist for the active filter, THE SYSTEM SHALL display an empty state illustration with message: "No listings in this category yet. Be the first to sell!" and a button navigating to Tab 3 (`Sell`).

## Failure Modes & Edge Cases
1. **Network Disconnected:** WHEN the device is offline, THE SYSTEM SHALL serve cached listings from Firestore offline persistence and display a subtle offline banner.
2. **Image Loading Failure:** WHEN an image URL is 404 or fails to decode, Coil SHALL render a branded neutral placeholder box with an item category icon.
3. **Empty Campus Section:** WHEN a student's campus has 0 active listings, THE SYSTEM SHALL display a message: "No active listings at [Campus] right now" and immediately show the "Across CUNY" section below.
4. **Student Campus Unset:** WHEN user campus is null or unverified, THE SYSTEM SHALL default to displaying the CUNY-wide feed without partitioning errors.

## Acceptance Criteria
- [ ] Safe zone banner renders at top of Home feed displaying the authenticated user's campus.
- [ ] Tapping "Calculators" category chip filters feed to show only calculator items.
- [ ] Items with `campus == user.campus` appear in the top section before items from other campuses.
- [ ] Tapping heart save icon toggles between outline and filled CUNY Blue (`#0033A1`).
- [ ] Tapping any listing card opens `ListingDetailScreen(listingId)`.
- [ ] Heart button has a minimum 48dp touch target and dynamic accessibility label.

## Design

### `ui/screens/home/HomeViewModel.kt`
```kotlin
package com.cuny.exchange.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.User
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ListingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentUser: User? = null,
    val selectedCategory: Category = Category.ALL,
    val campusListings: List<Listing> = emptyList(),
    val cunyListings: List<Listing> = emptyList(),
    val savedListingIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val listingRepository: ListingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(Category.ALL)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            authRepository.currentUserFlow
                .filterNotNull()
                .flatMapLatest { user ->
                    _selectedCategory.flatMapLatest { category ->
                        combine(
                            listingRepository.getListingsFlow(category),
                            listingRepository.getSavedListingIdsFlow(user.id)
                        ) { allListings, savedIds ->
                            val local = allListings.filter { it.campus == user.campus }
                            val acrossCuny = allListings.filter { it.campus != user.campus }
                            HomeUiState(
                                currentUser = user,
                                selectedCategory = category,
                                campusListings = local,
                                cunyListings = acrossCuny,
                                savedListingIds = savedIds,
                                isLoading = false
                            )
                        }
                    }
                }
                .collect { state -> _uiState.value = state }
        }
    }

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
    }

    fun toggleSave(listingId: String) {
        viewModelScope.launch {
            listingRepository.toggleBookmark(listingId)
        }
    }
}
```

### `ui/components/ListingCard.kt`
```kotlin
@Composable
fun ListingCard(
    listing: Listing,
    isSaved: Boolean,
    onSaveToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFFE2E8F0))
            ) {
                if (listing.photoUrls.isNotEmpty()) {
                    AsyncImage(
                        model = listing.photoUrls.first(),
                        contentDescription = "Photo of ${listing.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Campus Badge top-left
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                ) {
                    Text(
                        text = listing.campus.replace(" College", ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Heart Save Button top-right (min 48dp touch target)
                IconButton(
                    onClick = onSaveToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isSaved) "Unsave ${listing.title}" else "Save ${listing.title}",
                        tint = if (isSaved) CunyBlue else Color.White
                    )
                }

                // SOLD overlay
                if (listing.status == "sold") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SOLD",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (listing.price == 0.0) "Free" else if (listing.price % 1.0 == 0.0) "$${String.format("%.0f", listing.price)}" else "$${String.format("%.2f", listing.price)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = listing.condition,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${listing.sellerName} · ${listing.sellerRating} ★",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

## Current State
- `[verified]` `HomeScreen` is greenfield.
- `[verified]` Wireframe layout specifies two feed sections ("At Brooklyn College" + "Across CUNY") and category chips.

## Tests
- `home_feed_filters_by_category (R3)` — selecting "Electronics" chip renders only items where `category == "Electronics"`.
- `home_feed_partitions_campus_correctly (R5)` — items with `campus == user.campus` appear under the primary section. Mutant check: ensure `==` vs `!=` partitioning condition is tested thoroughly.
- `tap_card_navigates_to_detail (R6)` — tapping a listing card dispatches navigation with the correct `listing.id`.
- `toggle_save_updates_ui_state (R6)` — clicking heart icon flips `isSaved` state and triggers repository update.
- `free_listing_displays_free (R6)` — listing with `price = 0.0` renders "Free" instead of "$0".

## Constraints
- Dependencies: `02-data-model` and `03-auth` must merge first.
- Downstream: `06-listing-detail` drill-in is launched from this screen.
- Oracle Contract: Set-level Oracle 2 (Price formatting) and Design Tokens in `README.md`.

## Context
- Jetpack Compose LazyVerticalGrid: https://developer.android.com/develop/ui/compose/lists#grids
- Coil Image Loading: https://coil-kt.github.io/coil/compose/
