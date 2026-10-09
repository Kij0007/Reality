package com.reality.android.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionActionsTest {
    @get:Rule val compose = createComposeRule()
    private val session = SessionDto(
        id = 7, activityId = 2, startTime = "2026-10-08T10:00:00", endTime = null, duration = null,
    )

    @Test fun unknownBreakStateOnlyOffersSafeStop() {
        var stopped = 0
        compose.setContent {
            MaterialTheme {
                SessionActionButtons(session, null, true, {}, {}, { stopped++ })
            }
        }
        compose.onNodeWithText("Take a break").assertDoesNotExist()
        compose.onNodeWithText("Resume").assertDoesNotExist()
        compose.onNodeWithText("Stop session").performClick()
        assertEquals(1, stopped)
    }

    @Test fun busyPausedSessionDisablesResumeAndStop() {
        val pause = BreakDto(
            id = 1, sessionId = 7, startTime = "2026-10-08T10:05:00",
            endTime = null, duration = null, description = null,
        )
        compose.setContent {
            MaterialTheme {
                SessionActionButtons(session, listOf(pause), false, {}, {}, {})
            }
        }
        compose.onNodeWithText("Resume").assertIsNotEnabled()
        compose.onNodeWithText("Stop session").assertIsNotEnabled()
        compose.onNodeWithText("Take a break").assertDoesNotExist()
    }
}
