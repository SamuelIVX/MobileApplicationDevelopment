# Spec: Stretch — Inbox & Real-Time Chat

## Applicable Skills
- `mobile-android-design`: Jetpack Compose chat streams, `rememberLazyListState` auto-scrolling, Material 3 message bubbles, unread bottom-bar badges.
- `owasp-security`: OWASP Top 10 A01 (Broken Access Control), strict two-party authorization for Firestore thread reads and subcollection writes.
- `better-accessibility`: Screen reader accessible message bubble semantics, accessible touch targets (min 48dp on back/send icons), dynamic content descriptions.
- `codebase-design`: `ChatRepository` interface, Firestore atomic batch writes for message append and thread summary updates.
- `tdd`: Testing thread ordering, unread badge calculation, atomic message dispatch, and unauthorized read rejection.

## Objective
Implement real-time in-app peer messaging between prospective buyers and sellers: `InboxScreen`
(Tab 4 in bottom navigation) and the drill-in `ChatScreen`. Students can communicate securely
about items, negotiate prices, and coordinate safe on-campus meetups without exchanging personal
phone numbers or social media handles. Messages stream in real-time via Cloud Firestore subcollections,
and unread counts reflect dynamically on the bottom navigation bar.

> **Status: `[stretch]` — implement only if time allows. Do NOT mark this complete in the
> submission README unless it is functioning end-to-end with real-time Firestore listeners.**

## Scope
- Package: `com.cuny.exchange.ui.screens.chat` & `com.cuny.exchange.data.repository`
- Modifies (creates):
  - `ui/screens/chat/InboxScreen.kt` — thread list with unread badges
  - `ui/screens/chat/ChatScreen.kt` — active messaging view with bubble streams
  - `ui/screens/chat/ChatViewModel.kt`
  - `ui/screens/chat/InboxViewModel.kt`
  - `data/repository/ChatRepository.kt` — Firestore thread and message listeners
  - `ui/components/ThreadCard.kt`
  - `ui/components/MessageBubble.kt`
- Off-limits: `04-home-feed`, `05-create-listing`, `06-listing-detail`.

## Non-Goals
- No voice or video calls — text chat only.
- No media attachments in chat (images are shared on the public listing).
- No push notification backend (FCM) required for coursework scope — in-app snapshot listeners update UI while app is foregrounded.

## Invariants
- Thread and message data MUST be accessible ONLY to the two participants (`request.auth.uid in [buyerId, sellerId]`). [OWASP A01 / Oracle 4 / privacy invariant]
- Sending a message MUST atomically update the parent thread's `lastMessage`, `lastUpdated`, and `unreadUserIds` list. [consistency invariant]
- The Send button MUST be disabled when the draft message is blank (`draftText.trim().isEmpty()`). [validation invariant]
- Incoming messages MUST trigger an automatic smooth scroll to the newest item at the bottom of the list. [UX invariant]

## Requirements
1. THE SYSTEM SHALL render `InboxScreen` displaying all message threads where `buyerId == currentUser.uid` OR `sellerId == currentUser.uid`, ordered by `lastUpdated DESC`.
2. Each `ThreadCard` in the list SHALL display:
   - Counterpart student's initial avatar
   - Counterpart student's name and campus (e.g. "John D. · Brooklyn College")
   - Referenced item title (`re: "Organic Chemistry Textbook"`)
   - Snippet of the latest message
   - Formatted timestamp (e.g. "9:41 AM" or "Yesterday")
   - Unread indicator: an active blue dot (`#0033A1`) and bold text when `currentUser.uid in thread.unreadUserIds`.
3. THE SYSTEM SHALL compute the total count of unread threads and display a badge counter on the "Inbox" tab icon in the bottom navigation bar.
4. WHEN the student taps a `ThreadCard`, THE SYSTEM SHALL:
   - Remove `currentUser.uid` from `thread.unreadUserIds`
   - Navigate to `ChatScreen(threadId = thread.id)`.
5. WHEN no message threads exist, THE SYSTEM SHALL display an empty state:
   - "No messages yet."
   - "When you message a seller or someone asks about your listing, your chats will appear here."
6. THE SYSTEM SHALL render `ChatScreen` with a top header displaying:
   - Back button returning to `InboxScreen` (or `ListingDetailScreen`)
   - Counterpart student's name
   - Affiliation: "[Campus] student"
   - Subject tag: listing title linked to the conversation.
7. THE SYSTEM SHALL display messages in a chronological scrolling column:
   - **Own messages (sent):** aligned right, styled with CUNY Blue light ground (`#E6EEFA`), border (`#B8D3F7`), dark text, and timestamp.
   - **Counterpart messages (received):** aligned left, styled with neutral ground (`#F1F5F9`), border (`#DDE1E9`), dark text, and timestamp.
8. THE SYSTEM SHALL maintain a real-time Firestore listener on `threads/{threadId}/messages`. When a new message arrives, the list SHALL scroll to the bottom automatically.
9. THE SYSTEM SHALL render a sticky bottom input bar:
   - Text input with placeholder: "Message [Seller Name]..." (max 1000 characters)
   - Send icon button (disabled when input is blank).
10. WHEN the student taps "Send", THE SYSTEM SHALL:
    - Insert a new document into `threads/{threadId}/messages` with `senderId = currentUser.uid`, `text`, and server timestamp
    - Atomically update `threads/{threadId}` with `lastMessage = text`, `lastUpdated = now`, and add `counterpartId` to `unreadUserIds` using `arrayUnion`
    - Clear the draft input field.

## Failure Modes & Edge Cases
1. **Offline Message Dispatch:** WHEN a student sends a message while offline, Firestore offline persistence SHALL queue the message and render a pending clock icon on the message bubble until synchronized.
2. **Rapid Multi-Click on Send:** WHEN student taps Send rapidly, the button SHALL disable immediately on the first tap to prevent duplicate message bubbles.
3. **Message Length Exceeded:** WHEN student pastes text exceeding 1000 characters, the input SHALL reject additional characters or flag an error.
4. **Third-Party Intrusion:** WHEN a non-participant attempts to read thread messages, Firestore rules SHALL fail closed and return `PERMISSION_DENIED`.

## Acceptance Criteria
- [ ] Inbox lists threads ordered by most recent activity timestamp.
- [ ] Unread threads show blue indicator dot and increment the bottom nav badge counter.
- [ ] Opening a thread clears the unread badge for the current user.
- [ ] Sent messages appear on the right with light blue bubble; received appear on the left with grey bubble.
- [ ] Sending a message updates the parent thread's `lastMessage` and clears the input field.
- [ ] Non-participants receive `PERMISSION_DENIED` when querying thread documents.

## Design

### `ui/screens/chat/ChatViewModel.kt`
```kotlin
package com.cuny.exchange.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuny.exchange.data.model.Message
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val thread: Thread? = null,
    val currentUserId: String = "",
    val counterpartName: String = "",
    val counterpartCampus: String = "",
    val messages: List<Message> = emptyList(),
    val draftText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val threadId: String,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.filterNotNull().collect { user ->
                _uiState.update { it.copy(currentUserId = user.id) }
                chatRepository.getThreadFlow(threadId).collect { thread ->
                    if (thread != null) {
                        val isBuyer = user.id == thread.buyerId
                        _uiState.update {
                            it.copy(
                                thread = thread,
                                counterpartName = if (isBuyer) thread.sellerName else thread.buyerName
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            chatRepository.getMessagesFlow(threadId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun onDraftChanged(text: String) {
        if (text.length <= 1000) _uiState.update { it.copy(draftText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.draftText.trim()
        if (text.isEmpty() || state.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, draftText = "") }
            try {
                chatRepository.sendMessage(threadId, text)
            } catch (e: Exception) {
                _uiState.update { current ->
                    val restoredDraft = if (current.draftText.isEmpty()) text else "$text ${current.draftText}"
                    current.copy(draftText = restoredDraft, errorMessage = "Failed to send message")
                }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }
}
```

## Current State
- `[verified]` `InboxScreen` and `ChatScreen` are greenfield stretch features.
- `[verified]` Wireframe prototype includes full tap-through chat flow with simulated seller response.

## Tests
- `inbox_displays_threads_ordered_by_time (R1)` — threads appear with most recent first.
- `unread_badge_reflects_actual_unreads (R3)` — bottom nav badge matches count of threads where `currentUser.uid in thread.unreadUserIds`.
- `sending_message_clears_draft (R10)` — invoking `sendMessage()` clears the text box.
- `send_disabled_for_blank_text (R9)` — whitespace-only draft disables the Send icon button. Mutant check: `text.trim().isNotEmpty()`.
- `third_party_denied_access_to_thread (R1)` — Firestore rules reject message read for non-participant.

## Constraints
- Dependencies: `02-data-model` and `06-listing-detail` must merge first.
- Downstream: n/a (`[stretch]`).
- Oracle Contract: Set-level Oracle 4 (Security Rules) in `README.md`.

## Context
- Firestore Subcollections: https://firebase.google.com/docs/firestore/data-model#subcollections
