package com.cuny.exchange.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuny.exchange.data.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isOwnMessage) Color(0xFFE6EEFA) else Color(0xFFF1F5F9)
    val borderColor = if (isOwnMessage) Color(0xFFB8D3F7) else Color(0xFFDDE1E9)
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start

    val timeString = if (message.createdAt.seconds > 0) {
        SimpleDateFormat("h:mm a", Locale.US).format(Date(message.createdAt.seconds * 1000L))
    } else {
        ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = if (isOwnMessage) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            },
            color = bubbleColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .semantics {
                    contentDescription = "${if (isOwnMessage) "Sent" else "Received"} message: ${message.text}, at $timeString"
                }
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // If received message from counterpart, display sender name if available
                if (!isOwnMessage && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0033A1)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Message text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF0F172A)
                )

                // Timestamp
                if (timeString.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}
