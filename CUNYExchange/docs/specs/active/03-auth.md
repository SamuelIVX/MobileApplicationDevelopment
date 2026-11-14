# Spec: CUNY Authentication

## Applicable Skills
- `owasp-security`: OWASP Top 10 A07 (Identification and Authentication Failures), passwordless magic link verification, institutional domain gating.
- `mobile-android-design`: Jetpack Compose form states, accessibility labels, deep link handling, focus and error states.
- `tdd`: Explicit regex boundary testing, state flow testing with fake auth repositories, countdown timer verification.
- `codebase-design`: `AuthRepository` interface seam isolating Firebase Auth from ViewModels.

## Objective
Implement the 2-step student authentication experience defined in the audited wireframe.
Step 1 collects and validates a student's CUNY email (`@*.cuny.edu`) and dispatches a secure sign-in magic link.
Step 2 directs the student to check their inbox and tap the link to complete authentication via an Android deep link handler.
Behind the UI, an `AuthRepository` manages the verification handshake with Firebase Auth,
guaranteeing that unverified users or non-CUNY email addresses cannot access the marketplace.

## Scope
- Package: `com.cuny.exchange.ui.screens.auth` & `com.cuny.exchange.data.repository`
- Modifies (creates):
  - `data/repository/AuthRepository.kt` (interface) & `data/repository/FirebaseAuthRepository.kt`
  - `ui/screens/auth/SignInScreen.kt`
  - `ui/screens/auth/CheckEmailScreen.kt`
  - `ui/screens/auth/AuthViewModel.kt`
- Off-limits: `01-project-scaffold` (theme/gradle); `02-data-model` (schema/rules); all post-login screens (specs `04`–`08`).

## Non-Goals
- No password-based authentication in v1 — passwordless email verification ensures that identity is tied directly to an active CUNY Office365 inbox.
- No public consumer social sign-in (e.g. standard Google/Apple account sign-in) — student verification requires a verified institutional `.cuny.edu` tenant.
- No biometric authentication (Face Unlock / Fingerprint) in this phase.

## Invariants
- The email address MUST match the strict CUNY domain regex: `^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\.)*cuny\.edu$` prior to triggering verification. [domain & security invariant / Oracle 1]
- Any email address outside the CUNY domain MUST be rejected with an inline error message: "Must end in .cuny.edu". [wireframe requirement]
- Upon successful verification, if a document is absent at `/users/{uid}`, the system MUST require an onboarding step collecting `displayName`, `major`, and a valid `campus` (checked against `/campuses`) before creating the initial `/users/{uid}` record with `isVerified = true`, `rating = 5.0`, and `soldCount = 0` prior to permitting entry to `HomeScreen`. [domain & security invariant / Oracle 5]
- The magic link cooldown timer MUST enforce at least 60 seconds between resend requests to prevent inbox flooding. [rate-limiting invariant]

## Requirements
1. WHEN the user opens the application without an active session, THE SYSTEM SHALL display `SignInScreen` with:
   - Header: "Sign in with your CUNY email"
   - Subtitle: "Buy, sell and swap with verified students across all 25 campuses."
   - Email text field with placeholder `name@login.cuny.edu`
   - Helper/validation text: "Must end in .cuny.edu"
   - Primary button: "Send sign-in link"
   - Trust footer: "Only active CUNY students can join. Your email is used once to confirm your status."
2. WHEN the user enters an email that does NOT match the CUNY domain regex and attempts submission, THE SYSTEM SHALL mark the field invalid, display "Must end in .cuny.edu", and NOT dispatch a sign-in link.
3. WHEN the user enters a valid CUNY email and taps "Send sign-in link", THE SYSTEM SHALL:
   - Call `AuthRepository.sendSignInLink(email)`
   - Show a loading spinner inside the button and disable user input
   - On success, navigate to `CheckEmailScreen` passing the submitted email as navigation state.
4. WHEN navigated to `CheckEmailScreen`, THE SYSTEM SHALL display:
   - A "Back" navigation control returning to `SignInScreen`
   - An illustration icon and title: "Check your email"
   - Description: "We sent a magic sign-in link to your CUNY email."
   - The email address submitted in Step 1
   - A "Resend Link" button (disabled with a 60-second countdown cooldown).
5. WHEN the student taps the magic link in their email client, THE SYSTEM SHALL intercept the Firebase Hosting App Link (`https://cuny-exchange-demo.firebaseapp.com/__/auth/links` or custom app domain), recover the email from local storage (or prompt the student if opened on another device), and call `AuthRepository.verifyEmailLink(email, link)`.
6. WHEN verification succeeds, THE SYSTEM SHALL check if `/users/{uid}` exists. If the profile exists, it SHALL route directly to `HomeScreen` (Tab 1); if absent, it SHALL route to `OnboardingScreen` to collect `displayName`, `major`, and `campus` before creating the user document and routing to `HomeScreen`. In both paths, the authentication backstack SHALL be cleared so back press does not return to sign in.
7. WHEN verification fails (invalid or expired link), THE SYSTEM SHALL display an error message "Invalid or expired link. Please try again" and return to `SignInScreen`.
8. WHEN the student taps "Sign Out" from `ProfileScreen`, THE SYSTEM SHALL call `AuthRepository.signOut()` and navigate back to `SignInScreen`.

## Failure Modes & Edge Cases
1. **Network Timeout on Send:** WHEN a network failure occurs during `sendSignInLink()`, THE SYSTEM SHALL dismiss the loading spinner and display a snackbar error: "Network error. Please check your connection and retry."
2. **Expired / Consumed Link:** WHEN a user clicks an already consumed or expired magic link, `verifyEmailLink()` SHALL return a failure `Result` and display "This sign-in link has expired or has already been used. Please request a new one."
3. **Cross-Device Link Click:** WHEN a user opens the magic link on a phone where the initial email was not cached in `SharedPreferences`, THE SYSTEM SHALL present a dialog requesting the student re-enter their `.cuny.edu` email to complete the handshake.
4. **Resend Spam Prevention:** WHEN the resend button is clicked repeatedly, THE SYSTEM SHALL enforce the 60-second timer and ignore duplicate click events.

## Acceptance Criteria
- [ ] Entering `student@gmail.com` displays error "Must end in .cuny.edu" and disables submit.
- [ ] Entering `john.doe@login.cuny.edu` enables "Send sign-in link".
- [ ] Successfully dispatching link navigates to `CheckEmailScreen` showing the destination email.
- [ ] "Resend Link" is disabled for 60 seconds with live countdown indicator (`"Resend Link in 59s"`).
- [ ] Mock deep link with valid credentials completes handshake and directs user to `HomeScreen`.
- [ ] Launching app with an existing valid Firebase Auth session routes directly to `HomeScreen`.

## Design

### `data/repository/AuthRepository.kt`
```kotlin
package com.cuny.exchange.data.repository

import com.cuny.exchange.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<User?>
    suspend fun sendSignInLink(email: String): Result<Unit>
    suspend fun verifyEmailLink(email: String, link: String): Result<User>
    suspend fun signOut()
    fun isEmailValid(email: String): Boolean
}
```

### Domain Regex Validator
```kotlin
fun isCunyEmail(email: String): Boolean {
    val cunyRegex = Regex("^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\\.)*cuny\\.edu$", RegexOption.IGNORE_CASE)
    return email.trim().matches(cunyRegex)
}
```

## Current State
- `[verified]` Auth screens and logic are greenfield.
- `[verified]` Firebase Auth email link settings require package name and SHA-256 fingerprint configured in Firebase console.
- `[assumed]` Firebase dynamic links or App Links are configured for `com.cuny.exchange`.

## Tests
- `cuny_regex_accepts_valid_login_cuny_edu (R2)` — `isCunyEmail("marta.smith56@login.cuny.edu")` returns `true`.
- `cuny_regex_accepts_valid_campus_cuny_edu (R2)` — `isCunyEmail("samuel@csi.cuny.edu")` returns `true`.
- `cuny_regex_rejects_gmail (R2)` — `isCunyEmail("student@gmail.com")` returns `false`.
- `cuny_regex_rejects_subdomain_spoof (R2)` — `isCunyEmail("attacker@cuny.edu.attacker.com")` returns `false`. Mutant kill: ensure regex has trailing `$` end anchor.
- `cuny_regex_rejects_trailing_char (R2)` — `isCunyEmail("student@cuny.edux")` returns `false`.
- `resend_button_disabled_during_cooldown (R4)` — resend link button is disabled for 60 seconds after dispatch.
- `verify_email_link_success (R6)` — `AuthRepository.verifyEmailLink` success correctly navigates to `HomeScreen`.
- `sign_out_clears_session (R8)` — calling `signOut()` clears the local user state and redirects to auth.

## Constraints
- Dependencies: `01-project-scaffold` and `02-data-model` must merge first.
- Downstream: All post-login screens (`04`–`10`) depend on `AuthRepository.currentUserFlow`.
- Oracle Contract: Set-level Oracle 1 (CUNY Email Regex) in `README.md`.

## Context
- Firebase Passwordless Authentication: https://firebase.google.com/docs/auth/android/email-link-auth
- OWASP A07: Authentication Failures: https://owasp.org/Top10/A07_2021-Identification_and_Authentication_Failures/
