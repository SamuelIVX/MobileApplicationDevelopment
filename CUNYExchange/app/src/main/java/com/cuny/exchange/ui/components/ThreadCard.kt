package com.cuny.exchange.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.ui.theme.CunyBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThreadCard(
    thread: Thread,
    currentUserId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnread = thread.unreadUserIds.contains(currentUserId)
    val isBuyer = currentUserId == thread.buyerId
    val counterpartName = if (isBuyer) thread.sellerName.ifBlank { "Seller" } else thread.buyerName.ifBlank { "Buyer" }
    val counterpartCampus = if (isBuyer) thread.sellerCampus else thread.buyerCampus

    val initials = counterpartName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "S" }

    val formattedTime = formatTimestamp(thread.lastUpdated.seconds * 1000L)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Chat thread with $counterpartName regarding ${thread.listingTitle}${if (isUnread) ", unread" else ""}"
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isUnread) Color(0xFFF0F6FF) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isUnread) Color(0xFFBFDBFE) else Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Counterpart avatar (48x48dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CunyBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Thread text summary
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (counterpartCampus.isNotBlank()) "$counterpartName · $counterpartCampus" else counterpartName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isUnread) CunyBlue else Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Subject listing tag: re: "Title"
                Text(
                    text = "re: \"${thread.listingTitle}\"",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF0284C7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Last message snippet
                Text(
                    text = thread.lastMessage.ifBlank { "No messages yet" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isUnread) Color(0xFF1E293B) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Unread blue indicator dot
            if (isUnread) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CunyBlue)
                        .semantics { contentDescription = "Unread message indicator" }
                )
            }
        }
    }
}

private fun formatTimestamp(timeMs: Long): String {
    if (timeMs <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timeMs
    val oneDay = 24 * 60 * 60 * 1000L

    return when {
        diff < oneDay -> SimpleDateFormat("h:mm a", Locale.US).format(Date(timeMs))
        diff < 2 * oneDay -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timeMs))
    }
}
