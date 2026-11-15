package com.cuny.exchange.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuny.exchange.data.model.Campus
import com.cuny.exchange.ui.components.CategoryTileGrid
import com.cuny.exchange.ui.components.ListingCard
import com.cuny.exchange.ui.components.RecentSearchesRow
import com.cuny.exchange.ui.components.SearchInputBar
import com.cuny.exchange.ui.theme.CunyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onListingClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    savedListingIds: Set<String> = emptySet(),
    onToggleSave: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var campusDropdownExpanded by remember { mutableStateOf(false) }

    val activeChipBg = Color(0xFFE6EEFA)
    val activeChipBorder = CunyBlue

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    SearchInputBar(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChanged,
                        onClearQuery = { viewModel.onQueryChanged("") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Bar Row (Campus Dropdown, Under $100 Toggle, Active Category, Clear All)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Campus Filter Dropdown
                Box {
                    val isCampusFiltered = uiState.selectedCampus != "All campuses"
                    FilterChip(
                        selected = isCampusFiltered,
                        onClick = { campusDropdownExpanded = true },
                        label = {
                            Text(
                                text = if (isCampusFiltered) uiState.selectedCampus else "All Campuses",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Campus filter: ${uiState.selectedCampus}" },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = activeChipBg,
                            selectedLabelColor = CunyBlue
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isCampusFiltered,
                            borderColor = Color(0xFFCBD5E1),
                            selectedBorderColor = activeChipBorder,
                            selectedBorderWidth = 1.5.dp
                        )
                    )

                    DropdownMenu(
                        expanded = campusDropdownExpanded,
                        onDismissRequest = { campusDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All campuses") },
                            onClick = {
                                viewModel.onCampusSelected("All campuses")
                                campusDropdownExpanded = false
                            }
                        )
                        Campus.DEFAULT_CAMPUSES.forEach { campus ->
                            DropdownMenuItem(
                                text = { Text(campus.name) },
                                onClick = {
                                    viewModel.onCampusSelected(campus.name)
                                    campusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Under $100 Filter Chip
                FilterChip(
                    selected = uiState.under100Only,
                    onClick = viewModel::toggleUnder100,
                    label = {
                        Text(
                            text = "Under $100",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Filter under 100 dollars" },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeChipBg,
                        selectedLabelColor = CunyBlue
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.under100Only,
                        borderColor = Color(0xFFCBD5E1),
                        selectedBorderColor = activeChipBorder,
                        selectedBorderWidth = 1.5.dp
                    )
                )

                // 3. Category Filter Chip (when a category filter is active)
                if (uiState.selectedCategory != "All") {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.onCategorySelected("All") },
                        label = {
                            Text(
                                text = uiState.selectedCategory,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear category filter",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Category filter: ${uiState.selectedCategory}" },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = activeChipBg,
                            selectedLabelColor = CunyBlue
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            borderColor = Color(0xFFCBD5E1),
                            selectedBorderColor = activeChipBorder,
                            selectedBorderWidth = 1.5.dp
                        )
                    )
                }

                // 4. Clear all filters button
                if (uiState.isSearchActive) {
                    TextButton(
                        onClick = viewModel::clearFilters,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Clear all",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CunyBlue
                        )
                    }
                }
            }

            // Main Content Area
            if (!uiState.isSearchActive) {
                // Discovery Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "What do you need this semester?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )

                    // Recent Searches
                    if (uiState.recentSearches.isNotEmpty()) {
                        RecentSearchesRow(
                            recentSearches = uiState.recentSearches,
                            onSelectRecent = viewModel::onQueryChanged
                        )
                    }

                    // Category Tiles Grid
                    CategoryTileGrid(
                        categoryCounts = uiState.categoryCounts,
                        onCategoryClick = viewModel::onCategorySelected
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                // Search Results Layout
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CunyBlue,
                                modifier = Modifier.semantics { contentDescription = "Loading search results" }
                            )
                        }
                    }

                    uiState.errorMessage != null && uiState.results.isEmpty() -> {
                        // Error Zero-State with Retry action
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
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = uiState.errorMessage ?: "Search failed. Please try again.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF0F172A)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = viewModel::retry,
                                        colors = ButtonDefaults.buttonColors(containerColor = CunyBlue)
                                    ) {
                                        Text("Retry")
                                    }
                                    TextButton(
                                        onClick = viewModel::clearFilters
                                    ) {
                                        Text("Clear filters")
                                    }
                                }
                            }
                        }
                    }

                    uiState.results.isEmpty() -> {
                        // Empty Zero-State
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
                                    imageVector = Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "No listings found matching your search.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF0F172A)
                                )
                                Button(
                                    onClick = viewModel::clearFilters,
                                    colors = ButtonDefaults.buttonColors(containerColor = CunyBlue)
                                ) {
                                    Text("Clear all filters")
                                }
                            }
                        }
                    }

                    else -> {
                        // Results Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Results Summary Header
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = uiState.resultsCountLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(
                                items = uiState.results,
                                key = { it.id }
                            ) { listing ->
                                ListingCard(
                                    listing = listing,
                                    isSaved = savedListingIds.contains(listing.id),
                                    onSaveToggle = { onToggleSave(listing.id) },
                                    onClick = { onListingClick(listing.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
