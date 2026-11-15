package com.cuny.exchange

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.cuny.exchange.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeUnitTest {
    @Test
    fun brandColors_areConsistentWithSpec() {
        assertEquals(0xFF0033A1.toInt(), CunyBlue.toArgb())
        assertEquals(0xFFFFB71B.toInt(), VerifiedGold.toArgb())
    }

    @Test
    fun errorColors_meetWcagContrastRequirements() {
        // WCAG 2.2 AA requires >= 4.5:1 for normal text
        assertTrue("Light error on surface must meet 4.5:1", calculateContrast(ErrorLight, SurfaceLight) >= 4.5)
        assertTrue("Light onError on error fill must meet 4.5:1", calculateContrast(OnErrorLight, ErrorLight) >= 4.5)
        assertTrue("Dark error on surface must meet 4.5:1", calculateContrast(ErrorDark, SurfaceDark) >= 4.5)
        assertTrue("Dark onError on error fill must meet 4.5:1", calculateContrast(OnErrorDark, ErrorDark) >= 4.5)
    }

    private fun calculateContrast(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            return if (v <= 0.04045f) (v / 12.92).toDouble() else ((v + 0.055) / 1.055).toDouble().pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
