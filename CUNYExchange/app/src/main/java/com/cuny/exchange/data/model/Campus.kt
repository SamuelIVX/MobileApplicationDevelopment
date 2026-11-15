package com.cuny.exchange.data.model

data class Campus(
    val id: String = "",
    val name: String = "",
    val safeZone: String = "",
    val address: String = ""
) {
    companion object {
        val DEFAULT_CAMPUSES = listOf(
            Campus(
                id = "baruch",
                name = "Baruch College",
                safeZone = "Newman Library lobby (security desk)",
                address = "151 E 25th St, New York, NY 10010"
            ),
            Campus(
                id = "brooklyn",
                name = "Brooklyn College",
                safeZone = "Library lobby (security desk)",
                address = "2900 Bedford Ave, Brooklyn, NY 11210"
            ),
            Campus(
                id = "city-tech",
                name = "City Tech (NYCCT)",
                safeZone = "Namm Hall security desk",
                address = "300 Jay St, Brooklyn, NY 11201"
            ),
            Campus(
                id = "ccny",
                name = "City College of New York (CCNY)",
                safeZone = "NAC rotunda information desk",
                address = "160 Convent Ave, New York, NY 10031"
            ),
            Campus(
                id = "csi",
                name = "College of Staten Island (CSI)",
                safeZone = "Campus Center (1C) lobby info desk",
                address = "2800 Victory Blvd, Staten Island, NY 10314"
            ),
            Campus(
                id = "hunter",
                name = "Hunter College",
                safeZone = "Hunter West building 3rd floor skybridge",
                address = "695 Park Ave, New York, NY 10065"
            ),
            Campus(
                id = "john-jay",
                name = "John Jay College",
                safeZone = "Haaren Hall atrium desk",
                address = "524 W 59th St, New York, NY 10019"
            ),
            Campus(
                id = "queens",
                name = "Queens College",
                safeZone = "Rosenthal Library security entrance",
                address = "65-30 Kissena Blvd, Queens, NY 11367"
            )
        )

        fun findById(id: String): Campus? =
            DEFAULT_CAMPUSES.firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun findByName(name: String): Campus? =
            DEFAULT_CAMPUSES.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
