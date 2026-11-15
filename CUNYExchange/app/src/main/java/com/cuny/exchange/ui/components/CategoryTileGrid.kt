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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.ui.theme.CunyBlue

@Composable
fun CategoryTileGrid(
    categoryCounts: Map<String, Int> = emptyMap(),
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        CategoryTileData(Category.TEXTBOOKS.label, Icons.AutoMirrored.Outlined.MenuBook, Color(0xFFEFF6FF), CunyBlue),
        CategoryTileData(Category.CALCULATORS.label, Icons.Outlined.Calculate, Color(0xFFFEF3C7), Color(0xFFB45309)),
        CategoryTileData(Category.ELECTRONICS.label, Icons.Outlined.Devices, Color(0xFFF3E8FF), Color(0xFF7E22CE)),
        CategoryTileData(Category.CLOTHING.label, Icons.Outlined.Checkroom, Color(0xFFFCE7F3), Color(0xFFBE185D)),
        CategoryTileData(Category.FURNITURE.label, Icons.Outlined.Weekend, Color(0xFFE0F2FE), Color(0xFF0369A1)),
        CategoryTileData(Category.OTHER.label, Icons.Outlined.MoreHoriz, Color(0xFFF1F5F9), Color(0xFF475569))
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Render 2-column rows
        for (i in categories.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryTile(
                    tileData = categories[i],
                    count = categoryCounts[categories[i].name],
                    onClick = { onCategoryClick(categories[i].name) },
                    modifier = Modifier.weight(1f)
                )

                if (i + 1 < categories.size) {
                    CategoryTile(
                        tileData = categories[i + 1],
                        count = categoryCounts[categories[i + 1].name],
                        onClick = { onCategoryClick(categories[i + 1].name) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class CategoryTileData(
    val name: String,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color
)

@Composable
private fun CategoryTile(
    tileData: CategoryTileData,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Category tile: ${tileData.name}" },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tileData.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tileData.icon,
                    contentDescription = null,
                    tint = tileData.iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = tileData.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0F172A)
            )

            val countText = if (count != null && count > 0) {
                "$count ${if (count == 1) "item" else "items"}"
            } else {
                "Browse items"
            }

            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
        }
    }
}
