package com.cuny.exchange.data.model

enum class Condition(val label: String) {
    NEW("New"),
    LIKE_NEW("Like New"),
    GOOD("Good"),
    FAIR("Fair");

    companion object {
        fun fromLabel(label: String): Condition =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: GOOD
    }
}
