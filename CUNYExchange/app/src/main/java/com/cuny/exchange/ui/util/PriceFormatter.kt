package com.cuny.exchange.ui.util

import java.util.Locale

object PriceFormatter {
    /**
     * Formats listing price according to CUNYExchange Oracle 2 specification:
     * - Free if price <= 0.0
     * - Whole dollars without decimal places (e.g. $85)
     * - Cents formatted with two decimal places (e.g. $14.50)
     */
    fun formatPrice(price: Double): String {
        return when {
            price <= 0.0 -> "Free"
            price % 1.0 == 0.0 -> "$${price.toLong()}"
            else -> String.format(Locale.US, "$%.2f", price)
        }
    }
}
