package com.cuny.exchange.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuny.exchange.ui.components.CategoryChips
import com.cuny.exchange.ui.components.ListingCard
import com.cuny.exchange.ui.components.SafeZoneBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onListingClick: (String) -> Unit,
    onNavigateToSell: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val cunyBlue = Color(0xFF0033A1)

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null && (uiState.campusListings.isNotEmpty() || uiState.cunyListings.isNotEmpty())) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CUNYExchange",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = cunyBlue
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Safe Zone Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                SafeZoneBanner(
                    campusName = uiState.currentUser?.campus.orEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Category Filter Chips
            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryChips(
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { category ->
                        viewModel.selectCategory(category)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Loading State
            if (uiState.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = cunyBlue)
                    }
                }
            } else if (uiState.errorMessage != null && uiState.campusListings.isEmpty() && uiState.cunyListings.isEmpty()) {
                // 4. Error State (separate from empty state)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.errorMessage ?: "Failed to load listings",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cunyBlue)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else if (uiState.campusListings.isEmpty() && uiState.cunyListings.isEmpty()) {
                // 5. Empty Feed State (both sections empty, successful load)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No listings in this category yet. Be the first to sell!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToSell,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cunyBlue)
                        ) {
                            Text("Sell an Item")
                        }
                    }
                }
            } else {
                // 6. Partitioned Feed

                // Primary Campus Section (if user has a selected campus)
                val userCampus = uiState.currentUser?.campus.orEmpty().trim()
                if (userCampus.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "At $userCampus",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (uiState.campusListings.isNotEmpty()) {
                        items(
                            items = uiState.campusListings,
                            key = { it.id }
                        ) { listing ->
                            ListingCard(
                                listing = listing,
                                isSaved = uiState.savedListingIds.contains(listing.id),
                                onSaveToggle = { viewModel.toggleSave(listing.id) },
                                onClick = { onListingClick(listing.id) }
                            )
                        }
                    } else {
                        // Empty Campus Section notice
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "No active listings at $userCampus right now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Across CUNY Section
                if (uiState.cunyListings.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Across CUNY",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = uiState.cunyListings,
                        key = { it.id }
                    ) { listing ->
                        ListingCard(
                            listing = listing,
                            isSaved = uiState.savedListingIds.contains(listing.id),
                            onSaveToggle = { viewModel.toggleSave(listing.id) },
                            onClick = { onListingClick(listing.id) }
                        )
                    }
                }
            }
        }
    }
}
