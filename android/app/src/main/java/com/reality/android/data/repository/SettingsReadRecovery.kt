package com.reality.android.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/** Keep observing after temporary disk failures. A terminal catch would make
 * later successful saves wait forever for a publication that can never arrive. */
internal fun Flow<Preferences>.retrySettingsReads(): Flow<Preferences> = flow {
    var receivedSnapshot = false
    emitAll(this@retrySettingsReads
        .onEach { receivedSnapshot = true }
        .retryWhen { error, _ ->
            if (error !is IOException) return@retryWhen false
            if (!receivedSnapshot) {
                emit(emptyPreferences())
                receivedSnapshot = true
            }
            // After startup retain the last confirmed settings, especially the
            // backend address, rather than resetting it during an IO failure.
            delay(1_000)
            true
        })
}
