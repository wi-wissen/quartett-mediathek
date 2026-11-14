package net.wi7.quartettmediathek.tv.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
import net.wi7.quartettmediathek.repository.Progress
import net.wi7.quartettmediathek.repository.StartScreen
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.navigation.RailWidth
import net.wi7.quartettmediathek.tv.navigation.Routes
import net.wi7.quartettmediathek.tv.navigation.ShellHeader
import net.wi7.quartettmediathek.tv.ui.components.HeaderState
import net.wi7.quartettmediathek.tv.ui.components.LocalHeaderState
import net.wi7.quartettmediathek.tv.ui.components.SnapTopMargin
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.LocalFixedNow
import net.wi7.quartettmediathek.tv.ui.screens.AppRail
import net.wi7.quartettmediathek.tv.ui.screens.AvatarState
import net.wi7.quartettmediathek.tv.ui.screens.DiscoverContent
import net.wi7.quartettmediathek.tv.ui.screens.DiscoverSectionData
import net.wi7.quartettmediathek.tv.ui.screens.DiscoverUiState
import net.wi7.quartettmediathek.tv.ui.screens.EpisodeContent
import net.wi7.quartettmediathek.tv.ui.screens.EpisodeUiState
import net.wi7.quartettmediathek.tv.ui.screens.HomeContent
import net.wi7.quartettmediathek.tv.ui.screens.SearchContent
import net.wi7.quartettmediathek.tv.ui.screens.SearchUiState
import net.wi7.quartettmediathek.tv.ui.screens.SettingsContent
import net.wi7.quartettmediathek.tv.ui.screens.SettingsUiState
import net.wi7.quartettmediathek.tv.ui.screens.ShellUiState
import net.wi7.quartettmediathek.tv.ui.screens.ShowContent
import net.wi7.quartettmediathek.tv.ui.screens.ShowUiState
import net.wi7.quartettmediathek.tv.ui.screens.SubscriptionsContent
import net.wi7.quartettmediathek.tv.ui.screens.SubscriptionsUiState
import net.wi7.quartettmediathek.tv.ui.screens.WatchlistContent
import net.wi7.quartettmediathek.tv.ui.screens.WatchlistUiState
import net.wi7.quartettmediathek.tv.ui.screens.previewHomeState
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme
import net.wi7.quartettmediathek.tv.update.UpdatePhase
import net.wi7.quartettmediathek.tv.update.UpdateUiState
import net.wi7.quartettmediathek.update.ReleaseInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.hours

/**
 * Renders every main screen inside the real app frame (rail + fixed header) at 960×540 dp in xhdpi
 * (= 1920×1080 px, an Android TV) with Robolectric's native graphics. Images: app/src/test/screenshots/.
 *
 * Record: ./gradlew :app:recordRoborazziDebug   Verify: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest(private val locale: String) {
    protected val overlay = LibraryOverlay()

    protected fun snap(name: String, activeRoute: String?, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/${locale}_$name.png") {
            CompositionLocalProvider(LocalFixedNow provides FakeData.now) {
                QuartettTheme {
                    CompositionLocalProvider(LocalHeaderState provides remember { HeaderState() }) {
                    Box(Modifier.fillMaxSize().background(QColors.Background)) {
                        Box(Modifier.fillMaxSize().padding(start = RailWidth)) { content() }
                        ShellHeader(
                            ShellUiState(updated = FakeData.now - 13.hours),
                            onRefresh = {},
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        AppRail(
                            activeRoute = activeRoute,
                            requesters = TOP_LEVEL.associateWith { FocusRequester() },
                            contentEntry = FocusRequester(),
                            onNavigate = {},
                            onFocusChange = {},
                        )
                    }
                    }
                }
            }
        }

    companion object {
        val TOP_LEVEL = listOf(Routes.HOME, Routes.DISCOVER, Routes.SEARCH, Routes.SUBSCRIPTIONS, Routes.BOOKMARKS, Routes.SETTINGS)
    }
}

/** Android TV: 960×540 dp, landscape, TV UI mode, xhdpi (1920×1080 px). */
private const val TV_DE = "de-w960dp-h540dp-land-television-xhdpi"
private const val TV_EN = "en-w960dp-h540dp-land-television-xhdpi"

/** All screens in German (the mockup's language). */
@Config(sdk = [35], application = Application::class, qualifiers = TV_DE)
class GermanScreenshots : ScreenshotTest("de") {
    @Test
    fun home() = snap("home", Routes.HOME) { HomeContent(previewHomeState()) }

    @Test
    fun discover() = snap("discover", Routes.DISCOVER) {
        DiscoverContent(
            DiscoverUiState(true, null, FakeData.discoverRows.map { r -> DiscoverSectionData(r.category, r.items.map { overlay.decorate(it) }) }),
        ) {}
    }

    @Test
    fun search() = snap("search", Routes.SEARCH) {
        SearchContent(SearchUiState("nacht", false, FakeData.shows.take(6).map { overlay.decorate(it) }, false), {}, {})
    }

    @Test
    fun subscriptions() = snap("subscriptions", Routes.SUBSCRIPTIONS) {
        val eps = FakeData.allEpisodes.sortedByDescending { it.publishedAt }.take(12)
        SubscriptionsContent(
            SubscriptionsUiState(
                true,
                FakeData.shows.take(6).mapIndexed { i, s -> AvatarState(s.ref.key, s.title, null, i % 2 == 0, s) },
                null,
                eps.mapIndexed { i, e -> overlay.decorate(e).copy(isNew = i < 3) },
            ),
        ) {}
    }

    @Test
    fun watchlist() = snap("watchlist", Routes.BOOKMARKS) {
        WatchlistContent(WatchlistUiState(true, FakeData.initialBookmarks.map { overlay.decorate(it) }))
    }

    @Test
    fun settings() = snap("settings", Routes.SETTINGS) {
        SettingsContent(SettingsUiState(true, StartScreen.HOME), {}, {})
    }

    @Test
    fun settingsUpdate() = snap("settings_update", Routes.SETTINGS) {
        SettingsContent(SettingsUiState(true, StartScreen.HOME), {}, {}, update = previewUpdate(UpdatePhase.Idle))
    }

    @Test
    fun show() = snap("show", Routes.HOME) {
        val eps = FakeData.episodesByShow.getValue("hafenrevier")
        ShowContent(
            ShowUiState(
                loaded = true, show = FakeData.show("hafenrevier"), episodes = eps.map { overlay.decorate(it) },
                subscription = null, resumeEpisode = eps.first(),
                moreFromBroadcaster = FakeData.shows.filter { it.broadcaster.name == "ARD" }.map { overlay.decorate(it) },
            ),
            "screenshot", {}, {}, {},
        )
    }

    /** Show page scrolled to the episode row: header and fade are hidden, the row title sits at the top margin. */
    @Test
    fun showScrolled() = snap("show_scrolled", Routes.HOME) {
        val eps = FakeData.episodesByShow.getValue("hafenrevier")
        val listState = rememberLazyListState()
        val inset = with(LocalDensity.current) { -SnapTopMargin.roundToPx() }
        LaunchedEffect(Unit) {
            repeat(10) { withFrameNanos { } } // let the initial focus effect settle first
            listState.scrollToItem(1, inset)
        }
        ShowContent(
            ShowUiState(
                loaded = true, show = FakeData.show("hafenrevier"), episodes = eps.map { overlay.decorate(it) },
                subscription = null, resumeEpisode = eps.first(),
                moreFromBroadcaster = FakeData.shows.filter { it.broadcaster.name == "ARD" }.map { overlay.decorate(it) },
            ),
            "screenshot", {}, {}, {}, listState,
        )
    }

    @Test
    fun settingsUpdateDownloading() = snap("settings_update_downloading", Routes.SETTINGS) {
        SettingsContent(SettingsUiState(true, StartScreen.HOME), {}, {}, update = previewUpdate(UpdatePhase.Downloading, 0.42f))
    }

    @Test
    fun episode() = snap("episode", Routes.HOME) {
        val ep = FakeData.episode("abgruende-3")
        EpisodeContent(
            EpisodeUiState(
                true, ep, false, Progress(ep, 2210, false, FakeData.now), true,
                FakeData.episodesByShow.getValue("abgruende").filter { it.ref != ep.ref }.map { overlay.decorate(it) },
            ),
            "screenshot",
            onToggleBookmark = {},
        )
    }
}

/** English UI (device not set to German): one screen is enough to catch missing or overflowing strings. */
@Config(sdk = [35], application = Application::class, qualifiers = TV_EN)
class EnglishScreenshots : ScreenshotTest("en") {
    @Test
    fun home() = snap("home", Routes.HOME) { HomeContent(previewHomeState()) }

    @Test
    fun episode() = snap("episode", Routes.HOME) {
        val ep = FakeData.episode("abgruende-3")
        EpisodeContent(
            EpisodeUiState(true, ep, false, Progress(ep, 2210, false, FakeData.now), true, emptyList()),
            "screenshot",
            onToggleBookmark = {},
        )
    }
}

private fun previewUpdate(phase: UpdatePhase, progress: Float = 0f) = UpdateUiState(
    enabled = true, currentVersion = "0.1.2", lastCheckMs = (FakeData.now - 2.hours).toEpochMilliseconds(),
    phase = phase, progress = progress,
    release = ReleaseInfo("v0.1.3", "0.1.3", 103, "https://example.org/quartett-mediathek.apk", 3_162_376, null),
)
