package net.wi7.quartettmediathek.tv.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rail padding belongs to the rail destinations; the player is never padded, and chrome waits for the player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w960dp-h540dp-land-xhdpi")
class ShellNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun chromeRule() {
        assertTrue(shellChromeVisible(isPlayer = false, playersComposed = 0))
        assertFalse(shellChromeVisible(isPlayer = true, playersComposed = 1))
        assertFalse(shellChromeVisible(isPlayer = false, playersComposed = 1))
    }

    @Test
    fun playerIsNotPaddedAndLeavesCompositionOnBack() {
        lateinit var nav: NavHostController
        var composed = 0
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                railDestination(Routes.HOME) { Box(Modifier.fillMaxSize().testTag("home")) }
                playerDestination(onComposed = { composed++ }, onDisposed = { composed-- }) {
                    Box(Modifier.fillMaxSize().testTag("player"))
                }
            }
        }
        rule.onNodeWithTag("home").assertLeftPositionInRootIsEqualTo(RailWidth)
        rule.runOnUiThread { nav.navigate(Routes.player(EpisodeRef(SourceId.entries.first(), "x"), null)) }
        rule.waitForIdle()
        rule.onNodeWithTag("player").assertLeftPositionInRootIsEqualTo(0.dp)
        assertEquals(1, composed)
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()
        rule.onNodeWithTag("player").assertDoesNotExist()
        assertEquals(0, composed)
        rule.onNodeWithTag("home").assertLeftPositionInRootIsEqualTo(RailWidth)
    }

    /** User report: opening a show page focused the rail (it flew open) while the page was still loading. */
    @Test
    fun detailPageHoldsFocusWhileLoadingAndThenReleasesIt() {
        lateinit var nav: NavHostController
        val loaded = androidx.compose.runtime.mutableStateOf(false)
        val button = androidx.compose.ui.focus.FocusRequester()
        rule.setContent {
            nav = rememberNavController()
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                // stands in for the rail: the only other focusable thing on screen
                Box(Modifier.testTag("rail").focusable())
                NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                    railDestination(Routes.HOME) { Box(Modifier.fillMaxSize()) }
                    railDestination(Routes.SHOW_PATTERN, holdFocusWhileLoading = true) {
                        if (loaded.value) {
                            Box(Modifier.testTag("play").focusRequester(button).focusable())
                            androidx.compose.runtime.LaunchedEffect(Unit) { button.requestFocus() }
                        }
                    }
                }
            }
        }
        rule.runOnUiThread { nav.navigate(Routes.show(net.wi7.quartettmediathek.model.ShowRef(SourceId.ZDF, "x"))) }
        rule.waitForIdle()
        rule.onNodeWithTag(LOADING_FOCUS_TAG).assertIsFocused()
        rule.onNodeWithTag("rail").assertIsNotFocused()
        loaded.value = true
        rule.waitForIdle()
        rule.onNodeWithTag("play").assertIsFocused()
        rule.onNodeWithTag("rail").assertIsNotFocused()
    }
}
