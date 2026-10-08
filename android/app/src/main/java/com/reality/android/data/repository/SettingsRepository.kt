package com.reality.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reality.android.BuildConfig
import com.reality.android.core.network.normalizeBackendUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val backendUrl: String = BuildConfig.BACKEND_BASE_URL,
    val serverZoneId: String = "Asia/Kolkata",
    val theme: ThemePreference = ThemePreference.SYSTEM,
)

private val Context.realityDataStore: DataStore<Preferences> by preferencesDataStore(name = "reality_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = context.applicationContext.realityDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<Unit>()
    private val backendKey = stringPreferencesKey("backend_url")
    private val zoneKey = stringPreferencesKey("server_zone")
    private val themeKey = stringPreferencesKey("theme")

    private val storedSettings = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            val defaultUrl = normalizeBackendUrl(BuildConfig.BACKEND_BASE_URL, BuildConfig.DEBUG)
            AppSettings(
                backendUrl = runCatching {
                    normalizeBackendUrl(preferences[backendKey] ?: defaultUrl, BuildConfig.DEBUG)
                }.getOrDefault(defaultUrl),
                serverZoneId = preferences[zoneKey]?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
                    ?: "Asia/Kolkata",
                theme = runCatching {
                    ThemePreference.valueOf(preferences[themeKey] ?: "SYSTEM")
                }.getOrDefault(ThemePreference.SYSTEM),
            )
        }
        .onEach { loaded.complete(Unit) }

    val currentSettings: StateFlow<AppSettings> =
        storedSettings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val settings: Flow<AppSettings> = flow {
        loaded.await()
        emitAll(currentSettings)
    }

    suspend fun awaitLoaded() { loaded.await() }

    suspend fun saveBackendUrl(url: String) {
        val normalized = normalizeBackendUrl(url, BuildConfig.DEBUG)
        dataStore.edit { it[backendKey] = normalized }
    }

    suspend fun saveServerZone(id: String) {
        val normalized = id.trim()
        require(runCatching { ZoneId.of(normalized) }.isSuccess) { "Enter a valid server timezone, such as Asia/Kolkata." }
        dataStore.edit { it[zoneKey] = normalized }
    }

    suspend fun saveTheme(theme: ThemePreference) {
        dataStore.edit { it[themeKey] = theme.name }
    }
}
