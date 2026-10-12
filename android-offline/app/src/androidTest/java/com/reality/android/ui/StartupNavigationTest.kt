package com.reality.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.reality.android.MainActivity
import com.reality.android.R
import com.reality.android.data.local.LocalSnapshot
import com.reality.android.data.local.RealityCalculations
import com.reality.android.data.local.RealityDatabase
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs the actual Hilt/Room application. CI enables airplane mode; no repository is replaced. */
class StartupNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var inspectionDatabase: RealityDatabase? = null

    private fun text(resource: Int) = compose.activity.getString(resource)

    private fun awaitText(label: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun awaitText(resource: Int) = awaitText(text(resource))

    private fun clickDestination(resource: Int) {
        val matcher = hasText(text(resource)) and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun clickScrollableButton(resource: Int) {
        val matcher = hasText(text(resource)) and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher).onFirst()
            .performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    private fun scrollListTo(resource: Int) {
        val matcher = hasText(text(resource))
        // Break/report rows arrive after asynchronous Room reads and may not yet be composed.
        compose.waitUntil(15_000) {
            runCatching {
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
            }.isSuccess
        }
        compose.waitForIdle()
    }

    private fun hideKeyboard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val manager = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }

    // A separate read-only connection verifies real persisted rows without injecting a fake repository.
    private fun snapshot(): LocalSnapshot = runBlocking {
        val database = inspectionDatabase ?: Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RealityDatabase::class.java, "reality-offline.db",
        ).build().also { inspectionDatabase = it }
        val dao = database.realityDao()
        LocalSnapshot(dao.activities().map { it.dto() }, dao.sessions().map { it.dto() }, dao.breaks().map { it.dto() })
    }

    @After fun closeInspectionConnection() { inspectionDatabase?.close() }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val capture = File(instrumentation.targetContext.filesDir, "verification-$name.png")
        capture.outputStream().use { output ->
            assertTrue("PNG capture failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val exportDirectory = InstrumentationRegistry.getArguments().getString("realityScreenshotDir") ?: return
        require(Regex("/sdcard/Download/RealityVerification-[A-Za-z0-9-]+").matches(exportDirectory))
        // Export while instrumentation is alive; AGP later uninstalls the app.
        // The test shell writes this directory, so production needs no storage permission.
        if (Build.VERSION.SDK_INT >= 31) {
            shellOutput("mkdir -p $exportDirectory")
            val destination = "$exportDirectory/verification-$name.png"
            val descriptors = instrumentation.uiAutomation.executeShellCommandRw("dd of=$destination")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { output ->
                capture.inputStream().use { input -> input.copyTo(output) }
            }
            ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { it.readBytes() }
            val exportedSize = shellOutput("wc -c $destination").trim().substringBefore(' ').toLongOrNull()
            assertEquals("Screenshot export failed for $name", capture.length(), exportedSize)
        } else error("CI screenshot export requires Android 12 or newer.")
    }

    private fun shellOutput(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Test fun offlineStartupExposesSettingsAndBackupWithoutInternetPermission() {
        awaitText(R.string.insights_dashboard_title)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse("Offline Reality must not request internet", Manifest.permission.INTERNET in permissions)
        screenshot("dashboard")
        compose.onNodeWithContentDescription(text(R.string.nav_settings)).performClick()
        awaitText(R.string.settings_clock)
        screenshot("settings")
        clickScrollableButton(R.string.nav_backup)
        awaitText(R.string.backup_title)
        compose.onNodeWithText(text(R.string.backup_export)).assertIsDisplayed().assertIsEnabled()
        screenshot("backup")
        compose.onNodeWithContentDescription(text(R.string.go_back)).performClick()
        awaitText(R.string.settings_clock)
    }

    @Test fun nativeActivityFormRejectsInvalidInputWithoutWritingRows() {
        awaitText(R.string.insights_dashboard_title)
        val originalCount = snapshot().activities.size
        clickDestination(R.string.nav_activities)
        awaitText(R.string.wf_your_activities)
        clickScrollableButton(R.string.wf_create_activity)
        awaitText(R.string.wf_name)
        clickScrollableButton(R.string.wf_save_activity)
        compose.onNodeWithText("Enter an activity name.").performScrollTo().assertIsDisplayed()
        screenshot("validation")
        assertEquals(originalCount, snapshot().activities.size)
    }

    @Test fun nativeWorkflowPersistsActivityBreakResumeFinishAndReportOffline() {
        awaitText(R.string.insights_dashboard_title)
        val name = "Offline verification ${System.nanoTime()}"
        clickDestination(R.string.nav_activities)
        awaitText(R.string.wf_your_activities)
        clickScrollableButton(R.string.wf_create_activity)
        awaitText(R.string.wf_name)
        compose.onNode(hasText(text(R.string.wf_name)) and hasSetTextAction())
            .performScrollTo().performTextInput(name)
        compose.onNode(hasText(text(R.string.wf_minimum_minutes)) and hasSetTextAction())
            .performScrollTo().performTextInput("1")
        hideKeyboard()
        clickScrollableButton(R.string.wf_category_study)
        clickScrollableButton(R.string.wf_monday)
        clickScrollableButton(R.string.wf_save_activity)
        compose.waitUntil(15_000) { snapshot().activities.any { it.name == name } }
        val activity = snapshot().activities.single { it.name == name }
        assertEquals(1, activity.minimumDuration)
        assertEquals(setOf("MONDAY"), activity.scheduledDays)
        awaitText(R.string.wf_your_activities)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(name))
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        awaitText(R.string.wf_start_session)
        clickScrollableButton(R.string.wf_start_session)
        compose.waitUntil(15_000) { snapshot().sessions.any { it.activityId == activity.id && it.endTime == null } }
        val sessionId = snapshot().sessions.single { it.activityId == activity.id }.id
        clickScrollableButton(R.string.wf_view_sessions)
        awaitText(R.string.wf_tracking_intro)
        scrollListTo(R.string.wf_take_break)
        clickScrollableButton(R.string.wf_take_break)
        awaitText(R.string.wf_break_note)
        compose.onNode(hasText(text(R.string.wf_break_note)) and hasSetTextAction()).performTextInput("Offline break")
        hideKeyboard()
        compose.onNode(hasText(text(R.string.wf_start_break)) and hasClickAction()).assertIsEnabled().performClick()
        compose.waitUntil(15_000) { snapshot().breaks.any { it.sessionId == sessionId && it.endTime == null } }
        scrollListTo(R.string.wf_resume)
        screenshot("tracking")
        clickScrollableButton(R.string.wf_resume)
        compose.waitUntil(15_000) { snapshot().breaks.single { it.sessionId == sessionId }.endTime != null }
        scrollListTo(R.string.wf_stop_session)
        clickScrollableButton(R.string.wf_stop_session)
        compose.waitUntil(15_000) { snapshot().sessions.single { it.id == sessionId }.endTime != null }
        val stopped = snapshot().sessions.single { it.id == sessionId }
        val pause = snapshot().breaks.single { it.sessionId == sessionId }
        assertTrue(requireNotNull(stopped.duration) >= 0)
        assertTrue(requireNotNull(pause.duration) >= 0)
        assertEquals("Offline break", pause.description)

        clickDestination(R.string.nav_activities)
        awaitText(R.string.wf_your_activities)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(name))
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        awaitText(R.string.wf_report)
        clickScrollableButton(R.string.wf_report)
        awaitText(R.string.insights_reports)
        scrollListTo(R.string.insights_completion)
        compose.onNodeWithText(text(R.string.insights_completion)).assertIsDisplayed()
        scrollListTo(R.string.insights_finished_sessions)
        compose.onNodeWithText(text(R.string.insights_finished_sessions)).assertIsDisplayed()
        screenshot("report")
        val currentDay = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val report = RealityCalculations.month(snapshot(), activity.id, YearMonth.from(currentDay), currentDay)
        assertEquals(1, report.totalSessions)
        assertEquals(stopped.duration, report.totalDuration)
    }
}


