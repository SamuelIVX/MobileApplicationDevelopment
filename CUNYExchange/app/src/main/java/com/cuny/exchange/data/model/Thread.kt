package com.cuny.exchange.data.model

import com.google.firebase.Timestamp

data class Thread(
    val id: String = "",
    val listingId: String = "",
    val listingTitle: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val buyerName: String = "",
    val sellerName: String = "",
    val buyerCampus: String = "",
    val sellerCampus: String = "",
    val lastMessage: String = "",
    val lastUpdated: Timestamp = Timestamp.now(),
    val unreadUserIds: List<String> = emptyList()
)
