package com.cuny.exchange.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.cuny.exchange.data.model.User
import com.cuny.exchange.ui.theme.CunyBlue

@Composable
fun ProfileHeader(
    user: User?,
    activeCount: Int,
    soldCount: Int,
    modifier: Modifier = Modifier
) {
    val goldBadgeColor = Color(0xFFFFB71B)
    val displayName = user?.displayName.orEmpty().ifBlank { "CUNY Student" }
    val initials = displayName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "S" }

    val affiliation = buildString {
        val campus = user?.campus.orEmpty().trim()
        val major = user?.major.orEmpty().trim()
        if (campus.isNotEmpty()) append(campus)
        if (campus.isNotEmpty() && major.isNotEmpty()) append(" · ")
        if (major.isNotEmpty()) {
            append(major)
            if (!major.endsWith("major", ignoreCase = true)) append(" major")
        }
    }.ifEmpty { "CUNY Member" }

    val ratingFormatted = String.format(java.util.Locale.US, "%.1f", user?.rating ?: 5.0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Profile header for $displayName" },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Initials avatar container (48x48dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CunyBlue)
                        .semantics { contentDescription = "Student avatar with initials $initials" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 2. Name & Institutional Affiliation
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = affiliation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // 3. Gold Verified Student Badge (#FFB71B)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, goldBadgeColor),
                modifier = Modifier.semantics { contentDescription = "Gold Verified Student Badge" }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = goldBadgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Verified Student",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF92400E)
                    )
                }
            }

            // 4. Metrics summary row (Seller Rating, Items Sold, Active Listings)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$ratingFormatted ★",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                }

                // Sold Count
                Text(
                    text = "$soldCount sold",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF475569)
                )

                // Active Listings Count
                Text(
                    text = "$activeCount active",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = CunyBlue
                )
            }
        }
    }
}
