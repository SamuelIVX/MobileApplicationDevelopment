# Spec: Submission Package & Demonstration

## Applicable Skills
- `mobile-android-design`: Application packaging, release build configuration, demonstration flow choreography.
- `owasp-security`: Repository secrets verification, credential exclusion scan (`google-services.json`, keystores).
- `codebase-design`: Documentation cleanliness, competency mapping, setup instructions.

## Objective
Package the CUNYExchange Android application for academic submission and live class presentation
in Mobile Application Development. This includes finalizing the repository `README.md`, assembling
a repeatable live demonstration script (`DEMO-SCRIPT.md`) covering the end-to-end user journey across
all 7 core screens, generating an installable release/debug APK, and verifying that the project compiles
and runs cleanly from a fresh git clone without leaking private configuration keys.

## Scope
- Package: `com.cuny.exchange`
- Modifies (creates):
  - `README.md` (Android project root) — final student submission documentation
  - `DEMO-SCRIPT.md` — live presentation script and speaker notes
- Off-limits: feature source code (frozen at spec `08`, or `09`/`10` if stretch features were built).

## Non-Goals
- No Google Play Store production publishing (APK sideload or Android Studio emulator deploy).
- No new feature development during submission phase.

## Invariants
- `google-services.json`, local keystores, and credentials MUST NOT be committed to git. [OWASP A02 / secrets hygiene]
- The submission README MUST accurately reflect which features were completed (mark `[x]` ONLY for working features; `[stretch]` only if demonstrable). [academic integrity]
- The application MUST build cleanly with `./gradlew assembleDebug` on an external machine. [build determinism]

## Requirements
1. THE SYSTEM SHALL provide a comprehensive root `README.md` in the project repository containing:
   - **Project Overview:** problem statement, target audience (CUNY students), and value proposition
   - **Team Members & Contributions:** student names, roles, and division of labor
   - **Tech Stack:** Kotlin, Jetpack Compose, Material 3, Firebase Auth, Cloud Firestore, Firebase Storage, Coil
   - **Architecture:** Clean Architecture diagram (UI -> ViewModel -> Repository -> Firebase)
   - **Setup Instructions:** step-by-step guide from `git clone` to running in Android Studio
   - **Course Competencies Checklist:** mapping each implemented feature to mobile development concepts
   - **Feature Checklist:** exhaustive list of completed core and stretch features.
2. THE SYSTEM SHALL provide a structured presentation script (`DEMO-SCRIPT.md`) detailing the exact sequence for the in-class live demo:
   - **Scene 1: Institutional Sign-in** — enter `student@login.cuny.edu`, showcase invalid domain rejection, switch to email client to tap the magic link, and return to the app.
   - **Scene 2: Campus-First Home Feed** — highlight the Brooklyn College safe zone banner, browse campus-specific listings, filter by "Calculators", and save an item.
   - **Scene 3: Publish a Listing** — navigate to Tab 3 (`Sell`), select 2 textbook photos, enter title and price, select "Like New", publish, and watch it appear live at the top of Home.
   - **Scene 4: Listing Details & Seller Card** — open the listing, examine photo carousel, highlight the Gold Verified badge and safe meetup instructions.
   - **Scene 5: Search & Discovery** — search for "TI-84", toggle "Under $100", and cycle campuses.
   - **Scene 6: My Listings Management & Sign Out** — switch to Tab 5 (`Profile`), review sales metrics, mark an item as "Sold", and sign out.
   - **Stretch (Optional):** demonstrate real-time buyer-seller chat or the AI Exchange Assistant.
3. THE SYSTEM SHALL verify that `./gradlew assembleDebug` generates an APK in `app/build/outputs/apk/debug/app-debug.apk`.
4. THE SYSTEM SHALL confirm git status shows no tracked, staged, or historical private credentials:
   ```bash
   git check-ignore CUNYExchange/app/google-services.json  # Exit code 0
   git ls-files | grep -E "(keystore|jks|google-services\.json)" # Tracked filename check: must return empty
   git log --name-only --format= | grep -E "\.(jks|keystore)$" # Historical keystore check: must return empty
   git grep -i -E "(AIza[-0-9A-Za-z_]{35}|client_secret|BEGIN (RSA |EC )?PRIVATE KEY)" -- ':!*.md' # Working tree check: must return empty
   git grep --cached -i -E "(AIza[-0-9A-Za-z_]{35}|client_secret|BEGIN (RSA |EC )?PRIVATE KEY)" -- ':!*.md' # Staged index check: must return empty
   git log -p -- ':!*.md' | grep -i -E "(AIza[-0-9A-Za-z_]{35}|client_secret|BEGIN (RSA |EC )?PRIVATE KEY)" # History check: must return empty
   ```

## Failure Modes & Edge Cases
1. **Fresh Clone Missing `google-services.json`:** Setup documentation SHALL provide clear instructions on copying `google-services.json.example` and adding project credentials before building.
2. **Classroom Wi-Fi Outage During Demo:** `DEMO-SCRIPT.md` SHALL include an offline fallback section explaining how Firestore local persistence enables browsing cached listings, and offline assistant queries if the stretch assistant is implemented.
3. **Emulator Architecture Mismatch:** Documentation SHALL specify recommended Android Virtual Device (AVD) configurations (Pixel 8 / API 34, `arm64-v8a` for Apple Silicon, `x86_64` for Intel).

## Acceptance Criteria
- [ ] Root `README.md` documents setup steps from clean clone to running app.
- [ ] `DEMO-SCRIPT.md` provides chronological walkthrough of all 7 core wireframe screens.
- [ ] `./gradlew assembleDebug` completes with returncode 0 and outputs installable APK.
- [ ] Git scan confirms zero committed secrets or private credentials.

## Current State
- `[verified]` All preceding implementation specs (01–08, and stretch 09–10) are defined.
- `[verified]` Coursework rubric requires demonstration of Material Design, Navigation, Firestore, and Auth.

## Tests
- `clean_gradle_build (R3)` — `./gradlew assembleDebug` exits 0 without compilation errors.
- `secrets_not_committed (R4)` — git history confirms zero tracked private keys or keystores.
- `demo_script_covers_all_screens (R2)` — walkthrough script verifies the 7 core screens from the audited wireframe.

## Constraints
- Dependencies: all core implementation specs (`01`–`08`) must be complete.
- Downstream: final delivery package for grading.
- Oracle Contract: Set-level Oracle 1 through 5 in `README.md`.

## Context
- Android Application Signing: https://developer.android.com/studio/publish/app-signing
- Markdown Presentation Guide: https://www.markdownguide.org/basic-syntax/
