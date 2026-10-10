package com.reality.android.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.reality.android.MainActivity
import com.reality.android.R
import java.io.File
import org.junit.Assert.assertEquals
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val capture = File(instrumentation.targetContext.filesDir, "verification-$name.png")
        capture.outputStream().use { output ->
            assertTrue("PNG capture failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val exportDirectory = InstrumentationRegistry.getArguments().getString("realityScreenshotDir") ?: return
        require(Regex("/sdcard/Download/RealityVerification-[A-Za-z0-9-]+").matches(exportDirectory))
        // AGP uninstalls the app after connected tests. Export while instrumentation is alive.
        // UiAutomation writes as the test shell; the production app needs no storage permission.
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
        } else {
            error("CI screenshot export requires Android 12 or newer. Ordinary UI tests need no export argument.")
        }
    }

    private fun shellOutput(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Test fun signedOutStartupKeepsSettingsReachableWithoutServer() {
        awaitStartup()
        awaitText(R.string.auth_login)
        screenshot("login")
        compose.onNodeWithContentDescription(text(R.string.nav_settings)).performClick()
        awaitText(R.string.settings_url)
        compose.onNodeWithText(text(R.string.settings_save)).assertIsDisplayed()
        screenshot("settings")
        compose.onNodeWithText(text(R.string.go_back)).performClick()
        awaitText(R.string.auth_login)
        compose.onNodeWithText(text(R.string.auth_login)).assertIsDisplayed()
    }

    @Test fun nativeAccountFormValidatesBeforeContactingServer() {
        awaitStartup()
        compose.onNodeWithText(text(R.string.auth_new_account)).performScrollTo().performClick()
        awaitText(R.string.auth_register)
        screenshot("register")
        compose.onNodeWithText(text(R.string.auth_register)).performScrollTo().performClick()
        compose.onNodeWithText("Use 3–40 lowercase letters, numbers, dots, underscores or hyphens for your username.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.auth_waking)).assertDoesNotExist()
        screenshot("validation")
        compose.onNodeWithText(text(R.string.auth_existing_account)).performScrollTo().performClick()
        awaitText(R.string.auth_login)
    }
}