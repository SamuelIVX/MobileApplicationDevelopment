package com.cuny.exchange.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.ui.components.PhotoCarousel
import com.cuny.exchange.ui.components.SafeZoneCallout
import com.cuny.exchange.ui.components.SellerCard
import com.cuny.exchange.ui.theme.CunyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    viewModel: ListingDetailViewModel,
    onNavigateBack: () -> Unit,
    onMessageSeller: (Listing) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Back button" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    val listing = uiState.listing
                    if (listing != null) {
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Check out this ${listing.title} on CUNYExchange for $${listing.price} at ${listing.campus}!"
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share listing")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "Share listing button" }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share listing"
                            )
                        }

                        IconButton(
                            onClick = viewModel::toggleSave,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = if (uiState.isSaved) "Remove from saved" else "Save listing"
                                }
                        ) {
                            Icon(
                                imageVector = if (uiState.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (uiState.isSaved) Color(0xFFD64045) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            val listing = uiState.listing
            if (listing != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.isOwner) {
                            // Owner Actions: Delete Listing & Mark Sold/Active
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .semantics { contentDescription = "Delete listing button" },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Color(0xFFD64045)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFD64045)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            val isSold = listing.status == "sold"
                            Button(
                                onClick = {
                                    if (isSold) viewModel.markAsActive() else viewModel.markAsSold()
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(52.dp)
                                    .semantics {
                                        contentDescription = if (isSold) "Mark as Active button" else "Mark as Sold button"
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSold) CunyBlue else Color(0xFF16A34A)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSold) "Mark Active" else "Mark Sold",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            // Buyer Actions: Save & Message Seller
                            IconButton(
                                onClick = viewModel::toggleSave,
                                modifier = Modifier
                                    .size(52.dp)
                                    .semantics {
                                        contentDescription = if (uiState.isSaved) "Unsave item" else "Save item"
                                    }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (uiState.isSaved) Color(0xFFD64045) else Color(0xFF64748B),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            val isSold = listing.status == "sold"
                            Button(
                                onClick = { onMessageSeller(listing) },
                                enabled = !isSold,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .semantics {
                                        contentDescription = if (isSold) "Item sold, message seller disabled" else "Message seller button"
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CunyBlue,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mail,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSold) "Sold" else "Message Seller",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CunyBlue,
                            modifier = Modifier.semantics { contentDescription = "Loading listing details" }
                        )
                    }
                }

                uiState.listing == null -> {
                    // Not found or error state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = uiState.errorMessage ?: "This listing is no longer available",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0F172A)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = viewModel::retry,
                                    colors = ButtonDefaults.buttonColors(containerColor = CunyBlue)
                                ) {
                                    Text("Try Again")
                                }
                                OutlinedButton(onClick = onNavigateBack) {
                                    Text("Return to Home")
                                }
                            }
                        }
                    }
                }

                else -> {
                    val listing = uiState.listing!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Photo Carousel
                        PhotoCarousel(photoUrls = listing.photoUrls)

                        // 2. Sold Banner if status == sold
                        if (listing.status == "sold") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "SOLD — This item is no longer available",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }

                        // 3. Main Info Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Price
                            val priceText = if (listing.price == 0.0) {
                                "Free"
                            } else {
                                val formatted = "%.2f".format(listing.price)
                                "$${if (formatted.endsWith(".00")) formatted.dropLast(3) else formatted}"
                            }

                            Text(
                                text = priceText,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = CunyBlue
                            )

                            // Title
                            Text(
                                text = listing.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )

                            // Badges (Condition, Category, Campus)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetailBadge(text = listing.condition, containerColor = Color(0xFFEFF6FF), contentColor = CunyBlue)
                                DetailBadge(text = listing.category, containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF334155))
                                DetailBadge(text = listing.campus, containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF334155))
                            }

                            // Description
                            if (listing.description.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = listing.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }

                            // 4. Seller Trust Card
                            SellerCard(
                                sellerName = listing.sellerName,
                                sellerMajor = listing.sellerMajor,
                                sellerRating = listing.sellerRating,
                                soldCount = uiState.sellerProfile?.soldCount ?: 0,
                                isVerified = true
                            )

                            // 5. Safe Meetup Zone Callout
                            SafeZoneCallout(safeZoneText = listing.safeZone)

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Listing?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this listing? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteListing(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD64045))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier.heightIn(min = 28.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}
