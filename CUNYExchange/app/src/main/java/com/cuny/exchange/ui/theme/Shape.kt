package com.cuny.exchange.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),      // Control shape (Buttons, text fields)
    large = RoundedCornerShape(16.dp),       // Container shape (Cards, sheets)
    extraLarge = CircleShape                 // Tag shape (Pill chips)
)
