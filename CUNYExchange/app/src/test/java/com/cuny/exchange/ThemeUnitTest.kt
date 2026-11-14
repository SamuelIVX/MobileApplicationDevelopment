package com.cuny.exchange

import androidx.compose.ui.graphics.toArgb
import com.cuny.exchange.ui.theme.CunyBlue
import com.cuny.exchange.ui.theme.VerifiedGold
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeUnitTest {
    @Test
    fun brandColors_areConsistentWithSpec() {
        assertEquals(0xFF0033A1.toInt(), CunyBlue.toArgb())
        assertEquals(0xFFFFB71B.toInt(), VerifiedGold.toArgb())
    }
}
