package com.reality.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.reality.android.data.repository.ThemePreference
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reality.android.data.repository.AppSettings
import com.reality.android.data.repository.SettingsRepository
import com.reality.android.data.repository.SessionStore
import com.reality.android.data.repository.AuthRepository
import com.reality.android.ui.auth.AuthScreen
import com.reality.android.core.network.AuthRules
import com.reality.android.core.network.invalidateSavedSession
import com.reality.android.ui.navigation.RealityNavigation
import com.reality.android.ui.theme.RealityTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import androidx.activity.viewModels

@HiltViewModel
class ApplicationViewModel @Inject constructor(repository: SettingsRepository, sessions: SessionStore, auth: AuthRepository) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val session = sessions.state
    init {
        viewModelScope.launch {
            combine(repository.settings, sessions.state) { settings, session -> settings to session }
                .map { (settings, session) -> settings.backendUrl to session.session }
                .distinctUntilChanged { old, new -> old.first == new.first && old.second?.auth?.token == new.second?.auth?.token }
                .collect { (url, session) ->
                    if (session != null) {
                        if (AuthRules.usable(session, url)) auth.currentUser()
                        else sessions.clear(session.auth.token)
                    }
                }
        }
        // This ViewModel always exists, including startup with an already saved account.
        viewModelScope.launch {
            while (isActive) {
                invalidateSavedSession(sessions, repository.settings.first().backendUrl)
                delay(60_000)
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: ApplicationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings: AppSettings? by model.settings.collectAsStateWithLifecycle()
            val session by model.session.collectAsStateWithLifecycle()
            val dark = when (settings?.theme) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            RealityTheme(settings?.theme) {
                val ready = settings
                if (ready == null || !session.loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading_preferences))
                        }
                    }
                } else {
                    // A different server must never reuse the previous server's IDs or navigation state.
                    val signedIn = AuthRules.usable(session.session, ready.backendUrl)
                    key(ready.backendUrl, ready.serverZoneId, session.session?.auth?.token) {
                        if (signedIn) RealityNavigation() else AuthScreen()
                    }
                }
            }
        }
    }
}
