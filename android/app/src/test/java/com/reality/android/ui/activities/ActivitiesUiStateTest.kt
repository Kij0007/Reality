package com.reality.android.ui.activities

import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.SessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitiesUiStateTest {
    private fun activity(id: Long, name: String?, category: ActivityCategory?, duration: Int?) = ActivityDto(
        id = id, name = name, minimumDuration = duration, category = category,
        active = true, scheduledDays = setOf("MONDAY"), startDate = "2026-10-01",
        createdAt = "2026-10-01T10:00:00", updatedAt = null,
    )
    private val items = listOf(
        activity(1, "Deep work", ActivityCategory.WORK, 30),
        activity(2, "Reading", ActivityCategory.STUDY, 15),
        activity(3, null, null, null),
    )

    @Test fun searchAndCategoryBothApplyWithoutMutatingServerData() {
        val state = ActivitiesUiState(
            activities = items, loading = false, query = "DEEP", category = ActivityCategory.WORK,
        )
        assertEquals(listOf(1L), state.visible.map { it.id })
        assertEquals(3, state.activities.size)
    }

    @Test fun nullLegacyValuesDoNotCrashFilteringOrSorting() {
        assertEquals(listOf(1L, 2L, 3L), ActivitiesUiState(
            activities = items, sort = ActivitySort.DURATION,
        ).visible.map { it.id })
        assertTrue(ActivitiesUiState(activities = items, query = "missing").visible.isEmpty())
    }

    @Test fun activityActionsRequireConfirmedSessionRead() {
        val state = ActivityDetailUiState(activity = items.first(), loading = false, sessionsKnown = false, activityKnown = true)
        assertFalse(state.canStart)
        assertFalse(state.canDelete)
        assertTrue(state.copy(sessionsKnown = true).canStart)
        assertTrue(state.copy(sessionsKnown = true).canDelete)
    }

    @Test fun openSessionPreventsDuplicateStartAndActivityDeletion() {
        val state = ActivityDetailUiState(
            activity = items.first(), loading = false, sessionsKnown = true, activityKnown = true,
            sessions = listOf(SessionDto(
                id = 9, activityId = 1, startTime = "2026-10-08T10:00:00", endTime = null, duration = null,
            )),
        )
        assertFalse(state.canStart)
        assertFalse(state.canDelete)
        assertTrue(state.copy(sessions = emptyList()).canStart)
        assertFalse(state.copy(sessions = emptyList(), busy = true).canStart)
    }
}
