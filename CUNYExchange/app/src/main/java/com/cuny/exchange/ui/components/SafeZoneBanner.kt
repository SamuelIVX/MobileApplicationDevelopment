package com.cuny.exchange.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.cuny.exchange.data.model.Campus

@Composable
fun SafeZoneBanner(
    campusName: String,
    modifier: Modifier = Modifier,
    safeZoneText: String? = null
) {
    val displayCampus = campusName.trim().ifEmpty { "CUNY Campus" }
    val resolvedSafeZone = safeZoneText
        ?: Campus.findByName(campusName)?.safeZone
        ?: "Library lobby security desk & Student Center"

    val cunyBlue = Color(0xFF0033A1)
    val lightBlueBg = Color(0xFFE6EEFA)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(lightBlueBg)
            .border(1.dp, cunyBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Safe meetup zone for $displayCampus. Safe zones today: $resolvedSafeZone"
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cunyBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = cunyBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your campus: $displayCampus",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = cunyBlue
                )
                Text(
                    text = "Safe zones today: $resolvedSafeZone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
