package com.reality.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.reality.android.MainActivity
import com.reality.android.R
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the real Hilt application and native navigation with the configured backend.
 * CI intentionally has no server on emulator host port 8081; no runtime repository is replaced.
 */
class StartupNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(resource: Int) = compose.activity.getString(resource)

    private fun awaitStartup() {
        val settings = text(R.string.nav_settings)
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription(settings).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun awaitText(resource: Int) {
        val label = text(resource)
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun clickDestination(resource: Int) {
        compose.onAllNodes(hasText(text(resource)) and hasClickAction()).onFirst().performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val files = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(files, "verification-$name.png").outputStream().use { output ->
            assertTrue("PNG capture failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test fun unreachableBackendDoesNotPreventSettingsOrTopLevelNavigation() {
        awaitStartup()
        screenshot("home")
        compose.onNodeWithContentDescription(text(R.string.nav_settings)).performClick()
        awaitText(R.string.settings_url)
        compose.onNodeWithText(text(R.string.settings_url)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_save)).assertIsDisplayed()
        screenshot("settings")

        clickDestination(R.string.nav_activities)
        awaitText(R.string.wf_your_activities)
        compose.onNodeWithText(text(R.string.wf_your_activities)).assertIsDisplayed()
        screenshot("activities")

        clickDestination(R.string.nav_track)
        awaitText(R.string.wf_tracking)
        compose.onNodeWithText(text(R.string.wf_tracking)).assertIsDisplayed()
        screenshot("tracking")

        clickDestination(R.string.nav_more)
        awaitText(R.string.more_heading)
        compose.onNodeWithText(text(R.string.more_heading)).assertIsDisplayed()
        screenshot("more")
    }

    @Test fun nativeEmptyActivityFormValidatesLocallyAndBackReturnsToActivities() {
        awaitStartup()
        clickDestination(R.string.nav_activities)
        awaitText(R.string.wf_your_activities)
        compose.onAllNodes(hasText(text(R.string.wf_create_activity)) and hasClickAction())
            .onFirst().performClick()
        awaitText(R.string.wf_save_activity)
        screenshot("form")
        compose.onNodeWithText(text(R.string.wf_save_activity)).performScrollTo().performClick()

        // The ViewModel suite separately verifies no repository write is invoked for this form.
        compose.onNodeWithText("Enter an activity name.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose a category.").assertExists()
        compose.onNodeWithText("Choose at least one valid repeat day.").assertExists()
        compose.onNodeWithText(text(R.string.wait_for_server)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.wf_saving)).assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.go_back)).performClick()
        awaitText(R.string.wf_your_activities)
        compose.onNodeWithText(text(R.string.wf_your_activities)).assertIsDisplayed()
    }
}
