package com.reality.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reality.android.data.local.LocalRealityStore
import com.reality.android.data.repository.SettingsRepository
import com.reality.android.data.repository.ThemePreference
import com.reality.android.ui.navigation.RealityNavigation
import com.reality.android.ui.theme.RealityTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ApplicationViewModel @Inject constructor(settings: SettingsRepository, store: LocalRealityStore) : ViewModel() {
    val settings = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val generation = store.revision.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: ApplicationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            val generation by model.generation.collectAsStateWithLifecycle()
            val dark = when (settings?.theme) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val color = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(color) else SystemBarStyle.light(color, color)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            RealityTheme(settings?.theme) {
                val ready = settings
                if (ready == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                } else key(ready.serverZoneId, generation) {
                    // Restores replace IDs and reset all cached forms, filters and screen ViewModels.
                    RealityNavigation()
                }
            }
        }
    }
}
