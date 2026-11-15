package com.cuny.exchange.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class User(
    val id: String = "",
    val displayName: String = "",
    val campus: String = "",
    val major: String = "",
    val rating: Double = 5.0,
    val soldCount: Int = 0,
    @get:PropertyName("isVerified")
    @set:PropertyName("isVerified")
    var isVerified: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)
