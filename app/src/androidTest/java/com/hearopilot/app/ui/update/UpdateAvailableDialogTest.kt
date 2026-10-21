package com.hearopilot.app.ui.update

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hearopilot.app.ui.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateAvailableDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsReleaseDetailsAndTriggersDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var downloadClicked = false
        val release = AvailableRelease(
            version = "9.99.00",
            name = "体验改进版本",
            notes = "• 新增应用内检查更新\n• 改进面对面双屏的间距\n• 修复若干离线翻译问题",
            pageUrl = "https://github.com/hu2062639812-create/local-simultaneous-interpretation/releases",
            downloadUrl = null
        )

        composeRule.setContent {
            MaterialTheme {
                UpdateAvailableDialog(
                    currentVersion = "1.35.00",
                    release = release,
                    onDismiss = {},
                    onDownload = { downloadClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            context.getString(R.string.update_available_title, release.version)
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.update_download)
        ).assertExists()

        composeRule.onNodeWithText(context.getString(R.string.update_download)).performClick()
        composeRule.waitForIdle()
        assertTrue(downloadClicked)
    }
}
