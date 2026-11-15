package com.cuny.exchange.data.model

import com.google.firebase.Timestamp

data class User(
    val id: String = "",
    val displayName: String = "",
    val campus: String = "",
    val major: String = "",
    val rating: Double = 5.0,
    val soldCount: Int = 0,
    val isVerified: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)
