package com.cuny.exchange.data.model

enum class Category(val label: String) {
    ALL("All"),
    TEXTBOOKS("Textbooks"),
    CALCULATORS("Calculators"),
    ELECTRONICS("Electronics"),
    CLOTHING("Clothing"),
    FURNITURE("Furniture"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String): Category =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: OTHER
    }
}
