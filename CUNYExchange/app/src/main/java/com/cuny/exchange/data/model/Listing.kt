package com.cuny.exchange.data.model

import com.google.firebase.Timestamp

data class Listing(
    val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerEmail: String = "",
    val sellerMajor: String = "",
    val sellerRating: Double = 5.0,
    val title: String = "",
    val price: Double = 0.0,
    val condition: String = Condition.GOOD.label,
    val category: String = Category.TEXTBOOKS.label,
    val campus: String = "",
    val safeZone: String = "",
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val status: String = "active", // "active", "sold", or "deletion-pending"
    val createdAt: Timestamp = Timestamp.now()
) {
    val isAvailable: Boolean get() = status == "active"
}
