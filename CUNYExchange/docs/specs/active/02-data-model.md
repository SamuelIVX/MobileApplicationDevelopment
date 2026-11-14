# Spec: Firestore Data Model & Security Rules

## Applicable Skills
- `domain-modeling`: Ubiquitous language mapping, entity immutability, entity lifecycle definitions.
- `owasp-security`: OWASP Top 10 A01 (Broken Access Control), server-side authorization enforcement, schema validation.
- `codebase-design`: Clean data models, Firestore POJO deserialization contracts, repository data encapsulation.

## Objective
Define the Cloud Firestore NoSQL schema, Kotlin data classes, and server-side security rules
(`firestore.rules`) for CUNYExchange. This schema represents the single source of truth for all
entities identified in the domain audit: students, listings, categories, safe zones, message threads,
and real-time messages. The security rules enforce server-side access control (OWASP A01), ensuring
that users can only modify their own listings and participate in authorized message threads.

## Scope
- Package: `com.cuny.exchange.data.model`
- Modifies (creates):
  - `firestore.rules` — root Firebase Firestore security rules
  - `app/src/main/java/com/cuny/exchange/data/model/User.kt`
  - `app/src/main/java/com/cuny/exchange/data/model/Listing.kt`
  - `app/src/main/java/com/cuny/exchange/data/model/Condition.kt`
  - `app/src/main/java/com/cuny/exchange/data/model/Category.kt`
  - `app/src/main/java/com/cuny/exchange/data/model/Campus.kt`
  - `app/src/main/java/com/cuny/exchange/data/model/Thread.kt`
  - `app/src/main/java/com/cuny/exchange/data/model/Message.kt`
- Off-limits: UI screens, Navigation, and Auth implementation logic (owned by specs `03`–`10`).

## Non-Goals
- No SQL database or ORM — Firestore document-based collections only.
- No client-side admin bypass rules.
- No payment processing or credit card token storage (transactions settle peer-to-peer in person at safe zones).

## Invariants
- `price` on a listing MUST be a non-negative number (`price >= 0.0`). [domain invariant / Oracle 2]
- `photoUrls` list MUST contain between 0 and 5 URLs inclusive (`photoUrls.size in 0..5`). [DoS protection / Oracle 3]
- All write requests to `/listings/{id}` MUST originate from the authenticated listing owner (`request.auth.uid == resource.data.sellerId`). [OWASP A01 / Oracle 4]
- Chat threads and messages MUST be readable and writable ONLY by the two participants (`request.auth.uid in [thread.buyerId, thread.sellerId]`). [OWASP A01 / privacy invariant]
- Unauthenticated requests MUST be rejected across all private collections. [OWASP A01]

## Requirements
1. WHEN a user profile is created at `/users/{uid}`, THE SYSTEM SHALL enforce that `id == request.auth.uid`, `isVerified == true`, `rating == 5.0`, and `soldCount == 0`.
2. WHEN a listing document is created at `/listings/{id}`, THE SYSTEM SHALL enforce that:
   - `sellerId == request.auth.uid`
   - `price >= 0.0`
   - `photoUrls.size() <= 5`
   - `title` is a non-empty string of max 100 characters
   - `description` is a string of max 1000 characters
   - `condition` is one of `['New', 'Like New', 'Good', 'Fair']`
   - `category` is one of `['Textbooks', 'Calculators', 'Electronics', 'Clothing', 'Furniture', 'Other']`
   - `status == 'active'`
3. WHEN a listing document is updated at `/listings/{id}`, THE SYSTEM SHALL permit the update ONLY IF `request.auth.uid == resource.data.sellerId` AND immutable fields (`id`, `sellerId`, `createdAt`, `campus`, `safeZone`) remain unchanged.
4. WHEN a thread is initiated at `/threads/{id}`, THE SYSTEM SHALL verify that `request.auth.uid == request.resource.data.buyerId`, the referenced `listingId` exists in `/listings`, and the `sellerId` matches the listing's owner.
5. WHEN a message is written to `/threads/{threadId}/messages/{messageId}`, THE SYSTEM SHALL verify that `request.auth.uid == request.resource.data.senderId` AND the user is either the `buyerId` or `sellerId` of the parent thread.
6. THE SYSTEM SHALL define static, read-only campus documents in `/campuses/{campusId}` containing predefined safe meetup zone instructions.

## Failure Modes & Edge Cases
1. **Unverified Email Session:** WHEN a user with an unverified email attempts read/write, Firestore rules SHALL reject the request with `PERMISSION_DENIED` (`request.auth.token.email_verified == true` required).
2. **Negative or Malformed Price:** WHEN a write contains `price < 0` or NaN, Firestore rules SHALL reject the transaction with `PERMISSION_DENIED`.
3. **Photo Count Overflow:** WHEN a write contains more than 5 photo URLs, Firestore rules SHALL reject the transaction with `PERMISSION_DENIED`.
4. **Third-Party Message Snooping:** WHEN user C attempts to query `/threads/{threadId}/messages` where C is neither `buyerId` nor `sellerId`, Firestore rules SHALL return `PERMISSION_DENIED`.
5. **Listing Spoofing:** WHEN a client attempts to create a listing specifying someone else's UID as `sellerId`, Firestore rules SHALL reject the document write.

## Acceptance Criteria
- [ ] Firestore emulator tests verify unauthenticated users cannot read `/listings` or `/users`.
- [ ] Firestore emulator tests verify a user cannot update another user's listing (returns `PERMISSION_DENIED`).
- [ ] Firestore emulator tests verify listing creation with `price = -1.0` fails rules validation.
- [ ] Firestore emulator tests verify listing creation with 6 photo URLs fails rules validation.
- [ ] Kotlin data classes (`Listing`, `User`, `Thread`, `Message`) deserialize valid Firestore payloads without null-pointer exceptions.

## Design

### `firestore.rules`
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Helper: Validates active, verified CUNY student session
    function isCunyStudent() {
      return request.auth != null
        && request.auth.token.email.matches('.*@([a-zA-Z0-9-]+\\.)*cuny\\.edu$')
        && request.auth.token.email_verified == true;
    }

    // Users Collection
    match /users/{userId} {
      allow read: if isCunyStudent();
      allow create: if isCunyStudent() && request.auth.uid == userId
        && request.resource.data.keys().hasAll(['id', 'displayName', 'campus', 'major', 'rating', 'soldCount', 'isVerified', 'createdAt'])
        && exists(/databases/$(database)/documents/campuses/$(request.resource.data.campus))
        && request.resource.data.id == request.auth.uid
        && request.resource.data.isVerified == true
        && request.resource.data.rating == 5.0
        && request.resource.data.soldCount == 0;
      allow update: if isCunyStudent() && request.auth.uid == userId
        && !request.resource.data.diff(resource.data).affectedKeys()
            .hasAny(['isVerified', 'rating', 'soldCount', 'id', 'campus', 'displayName']);
    }

    // Listings Collection
    match /listings/{listingId} {
      allow read: if isCunyStudent() && (resource.data.status in ['active', 'sold'] || resource.data.sellerId == request.auth.uid);
      allow create: if isCunyStudent()
        && request.resource.data.id == listingId
        && request.resource.data.sellerId == request.auth.uid
        && request.resource.data.price >= 0
        && request.resource.data.photoUrls.size() <= 5
        && request.resource.data.title is string
        && request.resource.data.title.trim().size() > 0
        && request.resource.data.title.size() <= 100
        && request.resource.data.description is string
        && request.resource.data.description.size() <= 1000
        && request.resource.data.status == 'active'
        && request.resource.data.condition in ['New', 'Like New', 'Good', 'Fair']
        && request.resource.data.category in ['Textbooks', 'Calculators', 'Electronics', 'Clothing', 'Furniture', 'Other']
        && request.resource.data.sellerName == get(/databases/$(database)/documents/users/$(request.auth.uid)).data.displayName
        && request.resource.data.sellerMajor == get(/databases/$(database)/documents/users/$(request.auth.uid)).data.major
        && request.resource.data.sellerRating == get(/databases/$(database)/documents/users/$(request.auth.uid)).data.rating
        && exists(/databases/$(database)/documents/campuses/$(request.resource.data.campus))
        && request.resource.data.safeZone == get(/databases/$(database)/documents/campuses/$(request.resource.data.campus)).data.safeZone;
      allow update: if isCunyStudent()
        && resource.data.sellerId == request.auth.uid
        && request.resource.data.sellerId == resource.data.sellerId
        && request.resource.data.price >= 0
        && request.resource.data.photoUrls.size() <= 5
        && request.resource.data.title is string
        && request.resource.data.title.trim().size() > 0
        && request.resource.data.title.size() <= 100
        && request.resource.data.description is string
        && request.resource.data.description.size() <= 1000
        && request.resource.data.status in ['active', 'sold', 'deletion-pending']
        && request.resource.data.condition in ['New', 'Like New', 'Good', 'Fair']
        && request.resource.data.category in ['Textbooks', 'Calculators', 'Electronics', 'Clothing', 'Furniture', 'Other']
        && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['id', 'sellerName', 'sellerMajor', 'sellerRating', 'campus', 'safeZone', 'createdAt']);
      allow delete: if isCunyStudent()
        && resource.data.sellerId == request.auth.uid;
    }

    // Threads & Chat Subcollections
    match /threads/{threadId} {
      allow read: if isCunyStudent()
        && (request.auth.uid == resource.data.buyerId || request.auth.uid == resource.data.sellerId);
      allow create: if isCunyStudent()
        && request.auth.uid == request.resource.data.buyerId
        && exists(/databases/$(database)/documents/listings/$(request.resource.data.listingId))
        && request.resource.data.sellerId == get(/databases/$(database)/documents/listings/$(request.resource.data.listingId)).data.sellerId;
      allow update: if isCunyStudent()
        && (request.auth.uid == resource.data.buyerId || request.auth.uid == resource.data.sellerId)
        && request.resource.data.buyerId == resource.data.buyerId
        && request.resource.data.sellerId == resource.data.sellerId;

      match /messages/{messageId} {
        allow read: if isCunyStudent()
          && (request.auth.uid == get(/databases/$(database)/documents/threads/$(threadId)).data.buyerId
           || request.auth.uid == get(/databases/$(database)/documents/threads/$(threadId)).data.sellerId);
        allow create: if isCunyStudent()
          && (request.auth.uid == get(/databases/$(database)/documents/threads/$(threadId)).data.buyerId
           || request.auth.uid == get(/databases/$(database)/documents/threads/$(threadId)).data.sellerId)
          && request.resource.data.senderId == request.auth.uid
          && request.resource.data.text is string
          && request.resource.data.text.trim().size() > 0
          && request.resource.data.text.size() <= 1000;
      }
    }

    // Campuses catalog (Read-only)
    match /campuses/{campusId} {
      allow read: if isCunyStudent();
      allow write: if false; // Admin/seed only
    }
  }
}
```

### Kotlin Data Classes
```kotlin
package com.cuny.exchange.data.model

import com.google.firebase.Timestamp

enum class Condition(val label: String) {
    NEW("New"),
    LIKE_NEW("Like New"),
    GOOD("Good"),
    FAIR("Fair")
}

enum class Category(val label: String) {
    ALL("All"),
    TEXTBOOKS("Textbooks"),
    CALCULATORS("Calculators"),
    ELECTRONICS("Electronics"),
    CLOTHING("Clothing"),
    FURNITURE("Furniture"),
    OTHER("Other")
}

data class Listing(
    val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerMajor: String = "",
    val sellerRating: Double = 5.0,
    val title: String = "",
    val price: Double = 0.0,
    val condition: String = Condition.GOOD.label,
    val category: String = Category.TEXTBOOKS.label,
    val campus: String = "",
    val safeZone: String = "",
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val status: String = "active", // "active", "sold", or "deletion-pending"
    val createdAt: Timestamp = Timestamp.now()
) {
    val isAvailable: Boolean get() = status == "active"
}

data class User(
    val id: String = "",
    val displayName: String = "",
    val campus: String = "",
    val major: String = "",
    val rating: Double = 5.0,
    val soldCount: Int = 0,
    val isVerified: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)

data class Thread(
    val id: String = "",
    val listingId: String = "",
    val listingTitle: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val buyerName: String = "",
    val sellerName: String = "",
    val lastMessage: String = "",
    val lastUpdated: Timestamp = Timestamp.now(),
    val unreadUserIds: List<String> = emptyList()
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
```

## Current State
- `[verified]` Schema conforms directly to audited wireframe entities.
- `[verified]` Firestore POJO standards require no-arg constructors with default parameter values for reflection.

## Tests
- `listing_creation_fails_for_non_owner (R2)` — creating a listing where `sellerId != request.auth.uid` fails with `PERMISSION_DENIED`.
- `listing_update_fails_for_cross_user (R3)` — user B attempting to update user A's listing fails with `PERMISSION_DENIED`.
- `listing_creation_fails_for_negative_price (R2)` — creating a listing with `price = -10` is rejected by rules. Mutant check: ensure `< 0` boundary kills `>= 0` vs `> 0` logic appropriately.
- `listing_creation_fails_with_over_5_photos (R2)` — creating a listing with 6 photos is rejected by rules. Mutant check: `<= 5` vs `< 5`.
- `thread_messages_inaccessible_to_third_party (R5)` — user C attempting to read messages in a thread between user A and user B receives `PERMISSION_DENIED`.

## Constraints
- Dependencies: `01-project-scaffold` must merge first.
- Downstream: `03-auth`, `04-home-feed`, `05-create-listing` depend on these models and rule permissions.
- Oracle Contract: Set-level Oracle 2 (Price), Oracle 3 (Photo Limits), Oracle 4 (Security Rules), Oracle 5 (Rating).

## Context
- Firebase Security Rules Architecture: https://firebase.google.com/docs/firestore/security/get-started
- OWASP Top 10 A01: Broken Access Control: https://owasp.org/Top10/A01_2021-Broken_Access_Control/
