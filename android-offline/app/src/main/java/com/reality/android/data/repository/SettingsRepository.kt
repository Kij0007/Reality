package com.reality.android.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reality.android.core.network.MutationGate
import com.reality.android.data.local.RealityDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

enum class ThemePreference { SYSTEM, LIGHT, DARK }
data class AppSettings(val serverZoneId: String = "Asia/Kolkata", val theme: ThemePreference = ThemePreference.SYSTEM)
private val Context.realityDataStore by preferencesDataStore(name = "reality_offline_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context, private val mutations: MutationGate, private val dao: RealityDao,
) {
    private val dataStore = context.applicationContext.realityDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<Unit>()
    private val themeKey = stringPreferencesKey("theme")
    private val current = MutableStateFlow(AppSettings())
    val currentSettings: StateFlow<AppSettings> = current.asStateFlow()
    init {
        combine(dataStore.data.retrySettingsReads(), dao.observeTimezone()) { preferences, zone ->
            AppSettings(serverZoneId = zone ?: "Asia/Kolkata",
                theme = runCatching { ThemePreference.valueOf(preferences[themeKey] ?: "SYSTEM") }
                    .getOrDefault(ThemePreference.SYSTEM))
        }.onEach { current.value = it; loaded.complete(Unit) }.launchIn(scope)
    }
    val settings: Flow<AppSettings> = flow { loaded.await(); emitAll(currentSettings) }
    suspend fun awaitLoaded() { loaded.await() }
    suspend fun saveServerZone(id: String) {
        val normalized = id.trim()
        require(runCatching { ZoneId.of(normalized) }.isSuccess) { "Enter a valid time zone, such as Asia/Kolkata." }
        mutations.changeConfiguration {
            if (normalized != (dao.metadata()?.timezone ?: "Asia/Kolkata")) {
                require(dao.sessions().none { it.endTime == null }) {
                    "Finish running sessions before changing the activity clock."
                }
            }
            dao.setTimezone(normalized)
            currentSettings.first { it.serverZoneId == normalized }
        }
    }
    suspend fun saveTheme(theme: ThemePreference) { dataStore.edit { it[themeKey] = theme.name } }
}
