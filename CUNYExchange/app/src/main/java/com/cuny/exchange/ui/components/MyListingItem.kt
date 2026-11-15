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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.ui.theme.CunyBlue
import com.cuny.exchange.ui.util.PriceFormatter

@Composable
fun MyListingItem(
    listing: Listing,
    isMutating: Boolean,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDeletionPending = listing.status == "deletion-pending"
    val isSold = listing.status == "sold"
    val isActive = listing.status == "active"

    val (badgeText, badgeBg, badgeTextColor) = when {
        isDeletionPending -> Triple("DELETING...", Color(0xFFFEF3C7), Color(0xFF92400E))
        isSold -> Triple("SOLD", Color(0xFFF1F5F9), Color(0xFF475569))
        else -> Triple("ACTIVE", Color(0xFFDCFCE7), Color(0xFF166534))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeletionPending, onClick = onClick)
            .semantics { contentDescription = "My listing: ${listing.title}, status: $badgeText" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail photo (72x72dp)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    val photoUrl = listing.photoUrls.firstOrNull()
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Thumbnail for ${listing.title}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Price & Status Badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = PriceFormatter.formatPrice(listing.price),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = CunyBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick action controls (min 48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Status Button (Mark as Sold / Relist / Mark as Active)
                if (isActive) {
                    OutlinedButton(
                        onClick = onToggleStatus,
                        enabled = !isMutating && !isDeletionPending,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Mark ${listing.title} as sold" },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569))
                    ) {
                        Text(
                            text = "Mark as Sold",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                } else if (isSold) {
                    Button(
                        onClick = onToggleStatus,
                        enabled = !isMutating && !isDeletionPending,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Relist ${listing.title} as active" },
                        colors = ButtonDefaults.buttonColors(containerColor = CunyBlue)
                    ) {
                        Text(
                            text = "Relist / Mark as Active",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Trash Icon Button
                IconButton(
                    onClick = onDelete,
                    enabled = !isMutating,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Delete listing ${listing.title}" }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete listing",
                        tint = if (!isMutating) Color(0xFFD64045) else Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}
