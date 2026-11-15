package com.cuny.exchange.data.repository

import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Listing
import kotlinx.coroutines.flow.Flow

interface ListingRepository {
    fun getListingsFlow(category: Category): Flow<List<Listing>>
    fun getSavedListingIdsFlow(userId: String): Flow<Set<String>>
    fun getListingById(listingId: String): Flow<Listing?>
    fun isBookmarkedFlow(userId: String, listingId: String): Flow<Boolean>
    suspend fun toggleBookmark(listingId: String): Result<Unit>
    suspend fun updateListingStatus(listingId: String, status: String): Result<Unit>
    suspend fun createListing(
        listing: Listing,
        onWriteStarted: (() -> Unit)? = null
    ): Result<String>
    suspend fun deleteListing(listingId: String): Result<Unit>
    fun getActiveCategoryCountsFlow(): Flow<Map<String, Int>> = kotlinx.coroutines.flow.flowOf(emptyMap())
    suspend fun searchListings(
        query: String = "",
        campus: String = "All campuses",
        under100: Boolean = false,
        category: String = "All"
    ): List<Listing>
}
