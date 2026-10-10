package com.reality.android.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test fun activityIdentifiersSurviveNavigationSerialization() {
        val original = ActivityDetail(9_223_372_036_854_000L)
        assertEquals(original, Json.decodeFromString<ActivityDetail>(Json.encodeToString(original)))
    }

    @Test fun optionalTrackingFilterRemainsAbsent() {
        val original = Tracking()
        assertEquals(original, Json.decodeFromString<Tracking>(Json.encodeToString(original)))
    }

    @Test fun reportActivitySelectionSurvivesNavigationSerialization() {
        val original = Reports(42L)
        assertEquals(original, Json.decodeFromString<Reports>(Json.encodeToString(original)))
    }
}
