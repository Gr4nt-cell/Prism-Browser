package com.gr4nt.browser

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.data.ToolbarLayout
import com.gr4nt.browser.ui.DesktopWorkspace
import com.gr4nt.browser.data.Store
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on phone and tablet emulators. No external sites, downloads or stored-data deletion. */
@RunWith(AndroidJUnit4::class)
class BrowserChromeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun startPage() {
        rule.runOnIdle {
            Store.settings.autoHideToolbar = false
            Store.settings.desktopLayout = true
            Store.settings.layout = ToolbarLayout.COMPACT
            DesktopWorkspace.sidebarOpen = false
            Browser.startSection = 0
            Browser.newTab(private = false)
        }
        rule.waitForIdle()
    }

    @Test fun tabOverviewKeepsPrivateBrowsingAndAddressEntryReachable() {
        val overview = if (rule.activity.resources.configuration.smallestScreenWidthDp >= 600) "Tab Overview" else "Tabs"
        rule.onNodeWithContentDescription(overview).performClick()
        rule.onNodeWithText("Private").performClick()
        rule.onNodeWithText("Private Browsing").assertIsDisplayed()
        rule.onNodeWithContentDescription("New Tab", useUnmergedTree = false).performClick()
        rule.onNodeWithTag("address-editor").assertIsDisplayed().performTextInput("example.test")
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { check(Browser.current?.isPrivate == true) }
    }

    @Test fun tabletSidebarKeepsLibraryAndDownloadActionsReachable() {
        assumeTrue(rule.activity.resources.configuration.smallestScreenWidthDp >= 600)
        rule.runOnIdle { DesktopWorkspace.sidebarOpen = false }
        rule.onNodeWithContentDescription("Toggle Sidebar").performClick()
        rule.onAllNodesWithText("Bookmarks").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Reading List").assertIsDisplayed()
        rule.onAllNodesWithText("History").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Archive").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Downloads").onFirst().performClick()
        rule.runOnIdle { check(Browser.showDownloads) }
        rule.onNodeWithText("Done").performClick()
        rule.runOnIdle { check(!Browser.showDownloads) }
    }
    @Test fun commandDockKeepsTabLongPressActions() {
        assumeTrue(rule.activity.resources.configuration.smallestScreenWidthDp < 600)
        rule.onNodeWithContentDescription("Tabs").performTouchInput { longClick() }
        rule.onNodeWithText("New Private Tab").assertIsDisplayed()
        rule.onNodeWithText("New Private Tab").performClick()
        rule.onNodeWithTag("address-editor").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { check(Browser.current?.isPrivate == true) }
    }

    @Test fun browserActionsKeepDownloadsAndSettingsReachable() {
        rule.onNodeWithContentDescription("Browser Actions").performClick()
        rule.onNodeWithText("Page tools").assertIsDisplayed()
        rule.onNodeWithText("Downloads").performScrollTo().performClick()
        rule.runOnIdle { check(Browser.showDownloads) }
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithContentDescription("Browser Actions").performClick()
        rule.onNodeWithText("Settings").performScrollTo().performClick()
        rule.runOnIdle { check(Browser.showSettings) }
        rule.onNodeWithText("Search Engine").assertIsDisplayed()
    }

}
