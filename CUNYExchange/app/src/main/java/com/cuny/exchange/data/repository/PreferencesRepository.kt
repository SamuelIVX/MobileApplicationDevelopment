package com.cuny.exchange.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface PreferencesRepository {
    val recentSearchesFlow: Flow<List<String>>
    suspend fun saveRecentSearches(searches: List<String>)
}

class SharedPreferencesPreferencesRepository(
    context: Context
) : PreferencesRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cuny_exchange_preferences", Context.MODE_PRIVATE)

    private val _recentSearchesFlow = MutableStateFlow(loadRecentSearches())
    override val recentSearchesFlow: Flow<List<String>> = _recentSearchesFlow.asStateFlow()

    private fun loadRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    override suspend fun saveRecentSearches(searches: List<String>) {
        val capped = searches.filter { it.isNotBlank() }.take(5)
        val serialized = capped.joinToString(DELIMITER)
        prefs.edit().putString(KEY_RECENT_SEARCHES, serialized).apply()
        _recentSearchesFlow.update { capped }
    }

    companion object {
        private const val KEY_RECENT_SEARCHES = "recent_searches_list"
        private const val DELIMITER = "\u001F"
    }
}
