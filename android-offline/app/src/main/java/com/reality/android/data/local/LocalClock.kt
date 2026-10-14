package com.reality.android.data.local

import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalClock @Inject constructor(private val store: LocalRealityStore) {
    suspend fun now(): LocalDateTime {
        return LocalDateTime.now(ZoneId.of(store.timezone()))
    }
}
