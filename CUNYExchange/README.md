# CUNYExchange

A trust-first peer-to-peer student marketplace for all 25 City University of New York (CUNY) campuses. Verified students buy, sell, and swap textbooks, calculators, electronics, clothing, and dorm furniture exclusively with active CUNY peers. Authentication requires a `.cuny.edu` email verified via a secure magic link so every account represents an authentic student. The browsing experience is campus-aware (showing your campus first, then all of CUNY), transparent (displaying seller ratings and items sold), and safety-conscious (highlighting on-campus safe meetup zones on every listing).

Coursework project for **Mobile Application Development** (Fall 2026). Two-person team.  
Stack: **Kotlin + Jetpack Compose + Material 3 + Firebase (Auth, Firestore, Storage)**.

---

## Domain Glossary

| Term | Definition |
|---|---|
| **User** | A student registered with a verified `@*.cuny.edu` email, affiliated with one primary campus, carrying a public seller rating and completed sales count. |
| **Listing** | An item offered for sale: title, price (non-negative decimal), condition, category, campus, safe zone instructions, description, up to 5 photos, seller ID, and status (`active` / `sold` / `deletion-pending`). |
| **Condition** | Item wear level. Required on every listing: `New`, `Like New`, `Good`, or `Fair`. |
| **Category** | Fixed catalog: `Textbooks`, `Calculators`, `Electronics`, `Clothing`, `Furniture`, `Other`. |
| **Campus** | One of the 25 CUNY institutions (e.g., Baruch College, Brooklyn College, City College, Hunter College, Queens College, College of Staten Island). |
| **Safe Zone** | Designated public, monitored on-campus exchange location (e.g., Library security desk, Student Center lobby). Displayed on listing details and home banner. |
| **Verified Badge** | Gold badge (`#FFB71B`) awarded automatically once student status is verified via the CUNY email magic link flow. Appears on user profiles and listing cards. |
| **Seller Rating** | Continuous rating score (0.0–5.0) computed from completed peer transactions. Seeded at `5.0` for newly verified students. |
| **Saved Listing** | A local/cloud bookmark of an active listing saved by a student for quick retrieval. |
| **Thread** | A real-time bidirectional message thread between a prospective buyer and seller, linked to a specific listing. |
| **Exchange Assistant** | Context-aware AI assistant accessible via a floating action button on post-login screens, providing valuation guidelines, meetup safety advice, and listing draft assistance. [stretch] |

---

## Screen Map (9 Screens + Overlay)

```
[Unauthenticated]
  ├── 01 Sign In (CUNY email entry + regex validation)
  └── 02 Check Email (Magic link verification)
        │
[Authenticated App Shell — Persistent Bottom Navigation]
  ├── Tab 1: Home Feed (Campus safe zone banner, category chips, campus-first listings)
  ├── Tab 2: Search (Keyword search, campus filter, price toggle, recent searches)
  ├── Tab 3: Sell / Create Listing (5 photos, title, price, condition, category, description)
  ├── Tab 4: Inbox (Message thread list, unread badge counter) [stretch]
  └── Tab 5: Profile (Student identity card, seller stats, My Listings management, sign out)
        │
[Drill-In Screens]
  ├── Listing Detail (Photo gallery, seller card with gold verified badge, safe zone box, "Message Seller")
  └── Active Chat (Chronological buyer-seller message thread, real-time message stream) [stretch]
        │
[Floating Overlay]
  └── Exchange Assistant (Sparkle FAB → bottom sheet; listing-contextual prompts & safety fallback) [stretch]
```

---

## Spec-Set Index

| # | Spec | Owner | One-Line Purpose |
|---|---|---|---|
| 01 | [`docs/specs/active/01-project-scaffold.md`](docs/specs/active/01-project-scaffold.md) | You | Gradle Kotlin DSL setup, Compose dependencies, Firebase BOM, Material 3 design tokens, secrets hygiene. |
| 02 | [`docs/specs/active/02-data-model.md`](docs/specs/active/02-data-model.md) | You | Firestore collections schema (`users`, `listings`, `threads`, `messages`, `campuses`) & server-side security rules. |
| 03 | [`docs/specs/active/03-auth.md`](docs/specs/active/03-auth.md) | You | CUNY email domain validation, passwordless magic link verification, session management. |
| 04 | [`docs/specs/active/04-home-feed.md`](docs/specs/active/04-home-feed.md) | Teammate | Campus-first feed, safe zone header banner, category chip filtering, bookmarking. |
| 05 | [`docs/specs/active/05-create-listing.md`](docs/specs/active/05-create-listing.md) | You | 5-photo upload picker, client compression, form validation (title, price >= 0, condition), Firestore write. |
| 06 | [`docs/specs/active/06-listing-detail.md`](docs/specs/active/06-listing-detail.md) | Teammate | Multi-photo carousel, condition/price badges, seller card with gold badge, safe zone callout. |
| 07 | [`docs/specs/active/07-search-and-filter.md`](docs/specs/active/07-search-and-filter.md) | Teammate | Keyword query search, campus selector, "Under $100" toggle, recent searches. |
| 08 | [`docs/specs/active/08-profile.md`](docs/specs/active/08-profile.md) | You | Student profile card, ratings, My Listings management (mark sold/delete), sign out. |
| 09 | [`docs/specs/active/09-stretch-inbox-chat.md`](docs/specs/active/09-stretch-inbox-chat.md) | Anyone | `[stretch]` Real-time Firestore buyer↔seller chat threads & unread badge counters. |
| 10 | [`docs/specs/active/10-stretch-exchange-assistant.md`](docs/specs/active/10-stretch-exchange-assistant.md) | Anyone | `[stretch]` Floating AI assistant FAB, bottom sheet, context-aware prompt chips, offline safety engine. |
| 11 | [`docs/specs/active/11-submission.md`](docs/specs/active/11-submission.md) | You | Submission package: README, demonstration walkthrough script, release APK build. |

---

## Dependency Graph

```
01 Project Scaffold
        │
        ▼
02 Data Model & Security Rules
        │
        ▼
03 Authentication (CUNY email + Magic Link)
        │
        ├─────────────────────────────┐
        ▼                             ▼
04 Home Feed                   05 Create Listing
        │                             │
        └──────────────┬──────────────┘
                       ▼
               06 Listing Detail
                       │
              ┌────────┴────────┐
              ▼                 ▼
    07 Search & Filter     08 Profile & My Listings
              │                 │
              └────────┬────────┘
                       ▼
          09 [stretch] Inbox & Chat
                       │
                       ▼
          10 [stretch] Exchange Assistant
                       │
                       ▼
               11 Submission
```

---

## Cross-Spec Seam Registry

| Interface / Contract | Package | Owner Spec | Consumer Specs | Responsibility Boundary |
|---|---|---|---|---|
| `CUNYExchangeTheme` & Tokens | `com.cuny.exchange.ui.theme` | `01` | All UI specs (`03`–`10`) | Color scheme, typography (Geist Sans), shapes (16dp container, 12dp control, pill). |
| `User`, `Listing`, `Condition`, `Category`, `Campus` | `com.cuny.exchange.data.model` | `02` | `03`, `04`, `05`, `06`, `07`, `08` | Domain entity definitions, immutability, Firestore serializability. |
| `firestore.rules` | Project root | `02` | Firebase backend | Server-side authorization, immutable field protection, owner mutation checks. |
| `AuthRepository` | `com.cuny.exchange.data.repository` | `03` | `04`, `05`, `06`, `08`, `AppNavHost` | Firebase Auth sign-in link, session state Flow (`currentUserFlow`), signOut. |
| `ListingRepository` | `com.cuny.exchange.data.repository` | `04` (read) / `05` (write) / `08` (manage) | `04`, `06`, `07`, `08` | Firestore queries: campus/category stream, detail lookup, creation, search, status toggle, deletion. |
| `StorageRepository` | `com.cuny.exchange.data.repository` | `05` | `05`, `08` | Client-side photo scaling/compression (<=1MB, 1200px max) and Firebase Storage upload/deletion. |
| `ChatRepository` | `com.cuny.exchange.data.repository` | `09` | `06`, `09` | Firestore thread/message streaming, unread counters, atomic message transactions. |
| `AppNavHost` & `NavRoutes` | `com.cuny.exchange.ui.navigation` | `01` (scaffold) / `03` (auth routes) | All screens | Single-activity Compose NavHost, backstack handling, bottom bar selection state. |

---

## Set-Level Oracle Contracts

1. **CUNY Institutional Email Regex Oracle:**
   - Valid email must strictly match: `^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\.)*cuny\.edu$`
   - Must reject: generic commercial domains (`@gmail.com`, `@yahoo.com`), typo domains (`@cuny.com`, `@cuny.org`), malformed prefixes (`@login.cuny.edu` with empty local part).
   - Must accept: `student@login.cuny.edu`, `jane.doe@bc.cuny.edu`, `user@qmail.cuny.edu`, `alex@baruch.cuny.edu`.
2. **Monetary Price Precision Oracle:**
   - Price must be non-negative: `price >= 0.0`.
   - Free items are valid: `price == 0.0` (renders as "Free").
   - Fractional digits capped at 2 decimal places. Floating-point inputs with >2 decimals are rounded half-up or rejected at form input.
   - Values formatted for display with leading `$` using tabular numbers (`fontFeatureSettings = "tnum"`) to prevent UI jitter.
3. **Photo Storage & Quota Oracle:**
   - Maximum 5 photos per listing (`photoUrls.size in 0..5`).
   - Photos scaled to maximum bounding box `1200x1200px` at 80% JPEG quality.
   - Resulting byte payload must not exceed `1,048,576 bytes` (1 MB) per image before upload.
4. **Firestore Server-Side Security Rule Oracle:**
   - Unauthenticated requests: `allow read, write: if false` on all private paths.
   - Token must carry verified email: `request.auth.token.email_verified == true`.
   - Update rule for listing: `request.auth.uid == resource.data.sellerId && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['id', 'sellerId', 'createdAt'])`.
   - Thread security: `request.auth.uid in [resource.data.buyerId, resource.data.sellerId]`.
5. **Rating Calculation Oracle:**
   - New user rating seeded at `5.0`.
   - Range is bounded: `rating in 0.0..5.0`.
   - Display formatted to 1 decimal place with star symbol: `"%.1f ★"`.

---

## Design Tokens

Derived from the audited mobile wireframe:

| Token | Value | Applied To |
|---|---|---|
| **CUNY Blue** (`Primary`) | `#0033A1` | Primary buttons, active tab indicators, links, progress indicators. |
| **Verified Gold** (`Accent`) | `#FFB71B` | **Exclusively** reserved for the Verified Student badge. |
| **Surface** | `#FFFFFF` (Light) / `#1A2238` (Dark) | Cards, sheets, dialog containers. |
| **Ground** | `#F2F4F8` (Light) / `#0E1424` (Dark) | Scaffold background. |
| **Text Primary** | `#0A0A0A` (Light) / `#F0F4F8` (Dark) | Headings, titles, prices. |
| **Text Secondary** | `#6B6B6B` (Light) / `#94A3B8` (Dark) | Subtitles, safe zone notes, timestamps. |
| **Danger Red** | `#D64045` | Destructive actions (Delete listing, Sign out confirmation). |
| **Success Green** | `#2E7D32` | Active listing status pill, verified checkmark. |
| **Corner Radius (Container)** | `16dp` | Cards, bottom sheets. |
| **Corner Radius (Control)** | `12dp` | Inputs, primary buttons. |
| **Corner Radius (Tag/Chip)** | `CircleShape` | Category chips, condition badges. |
| **Typography** | Geist Sans | Regular (400), Medium (500), SemiBold (600), Bold (700); tabular numerals for prices. |

---

## Definition of Done (DoD)

1. **Authentication:** Only `@*.cuny.edu` email addresses can register; user must complete magic link verification before reaching the home feed.
2. **Campus-First Feed:** Home feed displays local campus listings before CUNY-wide items, alongside the campus safe meetup zone banner.
3. **Listing Creation:** A user can upload up to 5 photos, specify price, condition, category, and publish; new listing immediately appears in the feed.
4. **Detail & Safety:** Tapping a listing displays full details, seller profile (with gold verified badge and rating), and designated safe meetup zone.
5. **Search & Filter:** Keyword query, campus filter, and price toggles accurately filter results.
6. **Profile Management:** User can view their own listings, mark them as sold, or delete them.
7. **Security Rules:** Firestore rules enforce server-side ownership checks: cross-user edits/deletes are strictly rejected with `PERMISSION_DENIED`.
8. **Build & Release:** Project compiles cleanly with `./gradlew assembleDebug` and produces an installable APK.
