package com.rent.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rent.app.widget.HeatmapPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// One process-wide Preferences DataStore instance.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rent_prefs")

/**
 * Thin wrapper around Preferences DataStore holding both user settings
 * (username / token / threshold) and the cached [ContributionState].
 */
class RentDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val TOKEN = stringPreferencesKey("token")
        val THRESHOLD = intPreferencesKey("threshold")
        val CACHED_STATE = stringPreferencesKey("cached_state")

        // Appearance / behavior
        val PALETTE = stringPreferencesKey("palette")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val OPACITY = intPreferencesKey("bg_opacity")
        val MARGIN = intPreferencesKey("margin_dp")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WEEKS = intPreferencesKey("weeks_to_show")
    }

    data class Settings(
        val username: String,
        val token: String,
        val threshold: Int,
        val palette: HeatmapPalette,
        val darkMode: Boolean,
        val backgroundOpacity: Int,
        val marginDp: Int,
        val autoUpdate: Boolean,
        val weeksToShow: Int
    )

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            username = prefs[Keys.USERNAME].orEmpty(),
            token = prefs[Keys.TOKEN].orEmpty(),
            threshold = prefs[Keys.THRESHOLD] ?: DEFAULT_THRESHOLD,
            palette = HeatmapPalette.fromNameOrDefault(prefs[Keys.PALETTE]),
            darkMode = prefs[Keys.DARK_MODE] ?: DEFAULT_DARK_MODE,
            backgroundOpacity = prefs[Keys.OPACITY] ?: DEFAULT_OPACITY,
            marginDp = prefs[Keys.MARGIN] ?: DEFAULT_MARGIN,
            autoUpdate = prefs[Keys.AUTO_UPDATE] ?: DEFAULT_AUTO_UPDATE,
            weeksToShow = (prefs[Keys.WEEKS] ?: DEFAULT_WEEKS).coerceIn(MIN_WEEKS, MAX_WEEKS)
        )
    }

    suspend fun getSettings(): Settings = settingsFlow.first()

    suspend fun saveSettings(
        username: String,
        token: String,
        threshold: Int,
        palette: HeatmapPalette,
        darkMode: Boolean,
        backgroundOpacity: Int,
        marginDp: Int,
        autoUpdate: Boolean,
        weeksToShow: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username.trim()
            prefs[Keys.TOKEN] = token.trim()
            prefs[Keys.THRESHOLD] = threshold.coerceAtLeast(1)
            prefs[Keys.PALETTE] = palette.name
            prefs[Keys.DARK_MODE] = darkMode
            prefs[Keys.OPACITY] = backgroundOpacity.coerceIn(0, 100)
            prefs[Keys.MARGIN] = marginDp.coerceIn(0, MAX_MARGIN)
            prefs[Keys.AUTO_UPDATE] = autoUpdate
            prefs[Keys.WEEKS] = weeksToShow.coerceIn(MIN_WEEKS, MAX_WEEKS)
        }
    }

    val cachedStateFlow: Flow<ContributionState?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_STATE]?.let { raw ->
            runCatching { json.decodeFromString<ContributionState>(raw) }.getOrNull()
        }
    }

    suspend fun getCachedState(): ContributionState? = cachedStateFlow.first()

    suspend fun saveState(state: ContributionState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_STATE] = json.encodeToString(state)
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 10
        const val DEFAULT_DARK_MODE = false
        const val DEFAULT_OPACITY = 100
        const val DEFAULT_MARGIN = 0
        const val DEFAULT_AUTO_UPDATE = true
        const val MAX_MARGIN = 32
        const val DEFAULT_WEEKS = 52
        const val MIN_WEEKS = 4
        const val MAX_WEEKS = 53
    }
}
