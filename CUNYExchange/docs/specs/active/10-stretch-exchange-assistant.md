# Spec: Stretch — Exchange Assistant (AI)

## Applicable Skills
- `mobile-android-design`: Jetpack Compose `ModalBottomSheet`, custom floating action button positioning, chip prompts, bubble chat UI.
- `owasp-security`: OWASP Top 10 for LLMs, prompt injection safeguards, sensitive data redaction, zero credential persistence in client APK.
- `better-accessibility`: Accessible bottom sheet modal announcements, minimum 48dp touch targets on FAB and chips.
- `codebase-design`: Clean decoupled `AssistantRepository` with deterministic offline fallback (`OfflineAssistantEngine`).
- `tdd`: Testing contextual prompt generation, offline response categorization, and FAB visibility matrix.

## Objective
Implement the AI-powered student marketplace copilot: the **Exchange Assistant**.
Accessible via a floating action button (sparkle icon) on all authenticated screens, the assistant
opens an interactive bottom sheet modal. It is **context-aware**: when opened from a listing detail
screen, it automatically ingests the active item's details (title, price, condition, campus) and
offers relevant starter prompt chips (e.g. *"Is $85 fair for this?"*, *"Safe meetup spot at Baruch?"*).
It includes an offline fallback knowledge base for core marketplace safety, pricing guidelines,
and listing drafting so it functions reliably even without an external LLM connection.

> **Status: `[stretch]` — implement only if time allows. Do NOT mark this complete in the
> submission README unless it is functioning end-to-end.**

## Scope
- Package: `com.cuny.exchange.ui.assistant`
- Modifies (creates):
  - `ui/assistant/ExchangeAssistantFab.kt` — floating sparkle button with dynamic positioning
  - `ui/assistant/AssistantBottomSheet.kt` — interactive chat modal bottom sheet
  - `ui/assistant/AssistantViewModel.kt`
  - `ui/assistant/OfflineAssistantEngine.kt` — deterministic rule-based response engine
  - `data/repository/AssistantRepository.kt` — optional cloud LLM API client (Gemini / Claude SDK)
- Off-limits: all core transaction flows (specs `03`–`08`).

## Non-Goals
- No automated purchasing or price bargaining bots (assistant provides advice only; humans negotiate).
- No personal data or credentials collection (explicit safety prompt forbids asking for passwords, MFA codes, or financial info).
- No direct third-party API key embedding in `BuildConfig` or source code.

## Invariants
- The assistant FAB MUST float on all post-login screens EXCEPT `SignInScreen`, `CheckEmailScreen`, and active `ChatScreen`. [wireframe invariant]
- The assistant MUST be explicitly labeled as AI, with a safety footer: *"AI assistant for advice only. Always inspect items in person."* [OWASP LLM Top 10]
- The assistant MUST NEVER request, process, or store passwords, MFA codes, or payment cards. [security invariant]
- When opened from `ListingDetailScreen`, the active listing's title, price, condition, and campus MUST be injected into the assistant's context. [context awareness invariant]
- When external network or proxy fails, `OfflineAssistantEngine` MUST provide immediate responses without crashing or hanging. [availability invariant]

## Requirements
1. THE SYSTEM SHALL render a floating action button with a sparkle icon (`Icons.Default.AutoAwesome`):
   - Background: CUNY Blue (`#0033A1`), tint: White
   - Position: bottom-right of viewport
   - Offset: positioned 86dp above bottom of screen (clearing bottom navigation), or 158dp on `ListingDetailScreen` (clearing the sticky action bar).
2. WHEN the user is on `SignInScreen`, `CheckEmailScreen`, or active `ChatScreen`, THE SYSTEM SHALL hide the assistant FAB.
3. WHEN the student taps the sparkle FAB, THE SYSTEM SHALL open a `ModalBottomSheet`:
   - Header: "Exchange Assistant" with sparkle icon and a "Clear chat" action
   - Subtitle: "Campus marketplace guidance & safety tips"
   - Context Pill (when on listing): displaying active listing details (e.g. *"TI-84 Plus CE · $85 · Baruch"*).
4. WHEN no conversation history exists, THE SYSTEM SHALL display starter prompt chips:
   - On Listing Detail:
     - *"Is $[Price] fair for this?"*
     - *"Safe meetup spot at [Campus]?"*
     - *"What should I ask the seller?"*
   - On Home / Search / Profile:
     - *"What’s a fair price for a TI-84?"*
     - *"Safe meetup spots at Brooklyn College"*
     - *"Write a listing for my desk lamp"*
     - *"How does verification work?"*
5. WHEN the student taps any prompt chip, THE SYSTEM SHALL immediately submit that prompt as a user message and trigger assistant processing.
6. THE SYSTEM SHALL display conversation bubbles inside the sheet:
   - User messages aligned right in CUNY Blue light (`#E6EEFA`)
   - Assistant replies aligned left in neutral grey (`#F1F5F9`)
   - Typing state: animated 3-dot pulse when awaiting response.
7. WHEN an external LLM proxy endpoint is configured, THE SYSTEM SHALL dispatch the prompt with system prompt context. THE SYSTEM MUST reject or redact sensitive credentials prior to dispatch.
8. WHEN no external LLM proxy is reachable or network fails, THE SYSTEM SHALL route the prompt to `OfflineAssistantEngine`, which generates deterministic expert responses:
   - **Pricing:** *"Rule of thumb: used textbooks and calculators in good shape sell for about 40–60% of retail on campus. Compare two or three similar listings here, then price just under the middle if you want a quick sale."*
   - **Safe Meetup:** *"Pick a busy, staffed spot during open hours — the library lobby by the security desk or the student center front desk are the usual choices. Go in daylight, tell a friend, and test the item before paying in person."*
   - **Listing Drafting:** *"Try: '[Item Name] — works perfectly, light scuff on the base. $[Price], pickup at the [Campus] library lobby between classes.' Lead with item & condition, add one honest flaw, then price and pickup."*
   - **Verification:** *"Verification is two steps: sign in with your .cuny.edu email, then tap the magic link sent to your inbox. That is how everyone here is confirmed as an active CUNY student."*
9. THE SYSTEM SHALL provide a text input field with Send button at the bottom of the sheet, allowing free-form student queries.

## Failure Modes & Edge Cases
1. **Network Disconnected During Cloud Prompt:** WHEN an external LLM call fails or times out (5s threshold), THE SYSTEM SHALL automatically fall back to `OfflineAssistantEngine` and return a local response.
2. **Credential Probing Prompt:** WHEN a user types "What is my password" or "Can I have your token", the assistant SHALL reply: "I do not handle passwords, tokens, or private credentials. Never share these in chat."
3. **Empty Free-Form Input:** WHEN draft text is blank, the Send button SHALL be disabled.

## Acceptance Criteria
- [ ] Sparkle FAB is visible on Home, Search, Create Listing, and Profile screens.
- [ ] Sparkle FAB is hidden on SignIn, CheckEmail, and Chat screens.
- [ ] Tapping FAB opens `ModalBottomSheet` with context pill when on `ListingDetailScreen`.
- [ ] Tapping a starter prompt chip populates message and generates immediate assistant response.
- [ ] Offline engine correctly answers pricing, safe meetup, listing drafting, and verification queries without network.

## Design

### `ui/assistant/OfflineAssistantEngine.kt`
```kotlin
package com.cuny.exchange.ui.assistant

object OfflineAssistantEngine {
    fun reply(query: String, listingContext: String?): String {
        val q = query.lowercase()
        return when {
            q.contains("password") || q.contains("token") || q.contains("secret") || q.contains("credential") || q.contains("key") ->
                "I do not handle passwords, tokens, or private credentials. Never share these in chat."

            q.contains("price") || q.contains("fair") || q.contains("worth") || q.contains("$") ->
                if (listingContext != null) {
                    "Rule of thumb: used items in good shape sell for 40-60% of retail. For '$listingContext', compare it to a few similar listings to see if the price is fair."
                } else {
                    "Rule of thumb: used textbooks and calculators in good shape sell for about 40–60% of retail on campus, less once a newer edition is out. Compare two or three similar listings here, then price just under the middle if you want a quick sale."
                }

            q.contains("safe") || q.contains("meet") || q.contains("where") || q.contains("spot") || q.contains("pickup") ->
                "Pick a busy, staffed spot during open hours — the library lobby by the security desk or the student center front desk are the usual choices. Go in daylight, tell a friend, and test the item before paying in person."

            q.contains("ask") || q.contains("question") || q.contains("seller") || q.contains("buyer") ->
                "Good questions to ask before meeting: is it still available, why are they selling, any damage not in the photos, and can you test it on the spot. Agree on price and a campus safe zone in the chat so everything is on record."

            q.contains("list") || q.contains("write") || q.contains("draft") || q.contains("title") || q.contains("sell") ->
                if (listingContext != null) {
                    "Try: '$listingContext — [describe condition & any flaws]. Pickup on campus between classes.' Lead with item & condition, note any flaws honestly, then specify pickup."
                } else {
                    "Try: '[Item Name] — [describe condition & any flaws]. $[Price], pickup on campus between classes.' Lead with item & condition, add one honest flaw, then price and pickup."
                }

            q.contains("verif") || q.contains("login") || q.contains("link") || q.contains("email") ->
                "Verification is two steps: sign in with your .cuny.edu email, then tap the magic link sent to your inbox. That is how everyone here is confirmed as an active CUNY student — never forward that link to anyone."

            else ->
                "I can help with fair prices, writing a listing, safe on-campus meetup spots, or how verification works. What are you trying to do?"
        }
    }
}
```

## Current State
- `[verified]` `ExchangeAssistant` is a greenfield stretch addition.
- `[verified]` Matches wireframe interactive prototype bottom sheet layout and offline fallback rule dictionary.

## Tests
- `assistant_fab_hidden_on_auth_screens (R2)` — FAB is not rendered on `SignInScreen` or `CheckEmailScreen`.
- `tap_prompt_chip_submits_message (R5)` — clicking "Is $85 fair for this?" appends user message and displays assistant response.
- `offline_engine_provides_pricing_advice (R8)` — queries containing "fair" or "worth" receive pricing rule-of-thumb response.
- `sheet_dismisses_on_scrim_tap (R3)` — tapping outside sheet closes modal.

## Constraints
- Dependencies: `06-listing-detail` must merge first.
- Downstream: n/a (`[stretch]`).
- Oracle Contract: Set-level Oracle 5 in `README.md`.

## Context
- Compose ModalBottomSheet: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#ModalBottomSheet
- OWASP Top 10 for LLMs: https://owasp.org/www-project-top-10-for-large-language-model-applications/
