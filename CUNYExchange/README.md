# CUNYExchange

> **A trust-first peer-to-peer student marketplace for all 25 City University of New York (CUNY) campuses.**

Verified CUNY students buy, sell, and swap textbooks, calculators, electronics, clothing, and dorm furniture exclusively with active university peers. Authentication strictly requires an institutional `@*.cuny.edu` email verified via a passwordless magic link, ensuring every account represents an authentic enrolled student. The user experience is campus-aware (prioritizing your campus before all of CUNY), transparent (displaying student ratings and completed sales), and safety-conscious (mandating monitored on-campus safe meetup zones on every listing).

Coursework project for **Mobile Application Development**.  
Stack: **Kotlin + Jetpack Compose + Material 3 + Firebase (Auth, Firestore, Storage) + Room**.

---

## Tech Stack & Libraries

| Category | Technology | Usage in CUNYExchange |
|---|---|---|
| **Language** | Kotlin 2.0+ / Java 17 | Core language with strict null safety and functional extensions. |
| **UI Framework** | Jetpack Compose | Modern declarative UI with single-activity navigation. |
| **Design System** | Material 3 (`androidx.compose.material3`) | Custom theme tokens, dynamic color schemes, TopAppBars, Scaffolds. |
| **State & Concurrency** | Kotlin Coroutines & StateFlow | Unidirectional data flow, reactive state emissions, and lifecycle awareness. |
| **Authentication** | Firebase Authentication | Passwordless email link (`sendSignInLinkToEmail` + `isSignInWithEmailLink`). |
| **Cloud Database** | Cloud Firestore | Real-time snapshots, composite queries, server-side security rules, and batch writes. |
| **Cloud Storage** | Firebase Cloud Storage | Listing photo assets scaled and compressed on-device before upload. |
| **Local Persistence** | SharedPreferences & Room | Search history, saved listing bookmarks, and local cache. |
| **Image Loading** | Coil Compose (`io.coil-kt:coil-compose`) | Asynchronous image loading with placeholder fallbacks and memory caching. |
| **Navigation** | Navigation Compose | File-based type-safe routes, deep-link handling, and backstack management. |
| **Testing** | JUnit 4 + Coroutines Test | Unit tests using `runTest`, `StandardTestDispatcher`, and fake repositories. |

---

## Clean Architecture

The application strictly follows Clean Architecture and Android's Unidirectional Data Flow (UDF) pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│                     UI Layer (Jetpack Compose)                  │
│   Screens · Components · Navigation Graph · Material 3 Theme   │
└───────────────────────────────▲─────────────────────────────────┘
                                │ StateFlow (UI State)
                                │ Events / Callbacks (User Intent)
┌───────────────────────────────▼─────────────────────────────────┐
│                    ViewModel Layer (AndroidX)                   │
│   AuthVM · HomeVM · CreateListingVM · DetailVM · ProfileVM ... │
└───────────────────────────────▲─────────────────────────────────┘
                                │ suspend fun / Flow<T>
                                │ Result<T> Monad
┌───────────────────────────────▼─────────────────────────────────┐
│                    Repository / Domain Layer                    │
│   AuthRepository · ListingRepository · ChatRepository ...       │
└───────────────────────────────▲─────────────────────────────────┘
                                │
       ┌────────────────────────┴────────────────────────┐
       │                                                 │
┌──────▼──────────────────────────┐    ┌─────────────────▼─────────┐
│       Remote Data Source        │    │    Local Data Source      │
│   Firebase Auth · Firestore     │    │   SharedPreferences       │
│   Firebase Cloud Storage        │    │   Room Database           │
└─────────────────────────────────┘    └───────────────────────────┘
```

---

## Course Competencies Checklist

| Competency | Implementation in CUNYExchange | Source File(s) |
|---|---|---|
| **Declarative UI (Compose)** | Complete UI built with Jetpack Compose composables, custom layouts, and Material 3 design tokens. | `ui/screens/*`, `ui/components/*`, `ui/theme/*` |
| **Navigation & Deep Linking** | Single-activity `NavHost` managing 8+ destinations with argument decoding and email deep linking. | `ui/navigation/AppNavigation.kt`, `MainActivity.kt` |
| **MVVM & Reactive State** | ViewModels exposing immutable `StateFlow<UiState>` collected with lifecycle-aware Compose helpers. | `*ViewModel.kt` across all screens |
| **Firebase Auth & Security** | Passwordless magic-link sign in restricted to `@*.cuny.edu` regex, plus onboarding for new users. | `data/repository/FirebaseAuthRepository.kt`, `ui/screens/auth/*` |
| **Cloud Firestore & Rules** | Real-time streams (`callbackFlow`), composite filtering, atomic batch updates, and zero-trust security rules. | `data/repository/FirestoreListingRepository.kt`, `firestore.rules` |
| **Cloud Storage & Media** | Camera/gallery photo picker (`ActivityResultContracts.GetMultipleContents`), bitmap resizing (<=1200px), JPEG compression (<=1MB). | `data/repository/FirebaseStorageRepository.kt`, `ui/screens/create/*` |
| **Local Data Persistence** | Bookmarked listings saved to SharedPreferences and local search history. | `data/repository/SharedPreferencesPreferencesRepository.kt` |
| **Asynchronous Coroutines** | Structured concurrency with `viewModelScope`, exponential backoff retry loops, cancellation safety. | `*ViewModel.kt`, `*Repository.kt` |
| **Accessibility (a11y)** | 48dp minimum touch targets on all interactive components, full semantic content descriptions, high-contrast colors. | Verified across all components |
| **Quality & Automated Testing**| 100+ comprehensive unit tests verifying repository logic, viewmodel state machines, and edge cases. | `app/src/test/java/com/cuny/exchange/*Test.kt` |

---

## Feature Checklist

### Core Features (Completed)
- [x] **Spec 01: Project Scaffold & Theme** — Kotlin DSL, Material 3 design tokens, CUNY Blue (`#0033A1`), Verified Gold (`#FFB71B`), secrets hygiene.
- [x] **Spec 02: Data Model & Security Rules** — Firestore entities (`User`, `Listing`, `Thread`, `Message`, `Campus`), strict server-side rules denying cross-user mutations.
- [x] **Spec 03: Authentication & Onboarding** — Passwordless institutional email magic link, CUNY domain validation regex, cross-device link prompt, student profile onboarding.
- [x] **Spec 04: Campus-First Home Feed** — Filtered campus listings first, designated safe zone banner, horizontal category chips, bookmark toggle.
- [x] **Spec 05: Create Listing & Photo Upload** — Multi-photo picker (up to 5), on-device compression, form validation (title, price >= 0, condition, campus, safe zone).
- [x] **Spec 06: Listing Detail & Status Management** — Multi-photo carousel, seller profile card with gold verified badge, safe meetup location callout, mark as sold / relist actions.
- [x] **Spec 07: Search & Filter Experience** — Keyword search with instant filtering, campus selector dropdown, "Under $100" toggle, category chips, empty zero-state.
- [x] **Spec 08: Student Profile & My Listings** — Student identity card, seller rating, completed sales metrics, My Listings management with delete confirmation dialog, bounded sign out.

### Stretch Features
- [x] **Spec 09: Stretch — In-App Messaging & Real-Time Chat** — Real-time Firestore buyer↔seller chat, auto-scrolling message list, unread message badge count, sticky composer with keyboard insets, offline rollback recovery.
- [ ] **Spec 10: Stretch — Exchange Assistant** — *(Optional stretch omitted to focus on zero-defect core and in-app chat experience)*.
- [x] **Spec 11: Submission Package & Verification** — Submission documentation, competencies mapping, secrets audit, and clean Gradle build.

---

## Setup & Build Instructions

### Prerequisites
- **Android Studio:** Ladybug (2024.2.1+) or newer
- **JDK:** Java 17 or Java 21
- **Android SDK:** Platform 35 (compileSdk 35, minSdk 26)
- **Device / Emulator:** Pixel 8 or Pixel 7 running API 34+ (recommended AVD: `arm64-v8a` for Apple Silicon, `x86_64` for Intel)

### Step-by-Step Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/SamuelIVX/MobileApplicationDevelopment.git
   cd MobileApplicationDevelopment/CUNYExchange
   ```

2. **Configure Firebase credentials:**
   - **For building, assembling, and running unit tests:** Copy the included template to satisfy the Google Services Gradle plugin:
     ```bash
     cp app/google-services.json.example app/google-services.json
     ```
   - **For live runtime & cloud authentication:** Download your active `google-services.json` from the Firebase Console (*Project Settings → General → Your apps → SDK setup and configuration*) and place it at `app/google-services.json`.

3. **Build the Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The compiled APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Run Unit Tests:**
   ```bash
   ./gradlew test --rerun-tasks
   ```
   All test suites across Auth, Listings, Search, Profile, and Chat will execute.

5. **Install on Connected Device / Emulator:**
   ```bash
   ./gradlew installDebug
   ```

---

## Security & Secrets Hygiene Verification

To guarantee zero credential leakage, the repository enforces strict secrets exclusion:
```bash
# Verify google-services.json is ignored
git check-ignore app/google-services.json

# Verify no tracked keystore or credentials
git ls-files | grep -E "(keystore|jks|google-services\.json$)"

# Verify no historical keystores committed
git log --name-only --format= | grep -E "\.(jks|keystore)$"

# Verify no live API secret keys in working tree
git grep -i -E "(AIza[-0-9A-Za-z_]{35}|client_secret|BEGIN (RSA |EC )?PRIVATE KEY)" -- ':!*.md'
```


