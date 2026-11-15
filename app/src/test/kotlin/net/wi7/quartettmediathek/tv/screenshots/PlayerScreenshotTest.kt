package net.wi7.quartettmediathek.tv.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.captureRoboImage
import net.wi7.quartettmediathek.tv.player.OsdActions
import net.wi7.quartettmediathek.tv.player.OsdModel
import net.wi7.quartettmediathek.tv.player.PlayerOsd
import net.wi7.quartettmediathek.tv.player.PlayerShade
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PLAYER_TV_DE = "de-w960dp-h540dp-land-television-xhdpi"
private const val PLAYER_TV_EN = "en-w960dp-h540dp-land-television-xhdpi"

/** The player controls on a dark stand-in for the video, with the Quality button next to Audio / Language. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PlayerScreenshotTest(private val locale: String, private val model: OsdModel) {
    @Test
    fun osd() = captureRoboImage("src/test/screenshots/${locale}_player_osd.png") {
        QuartettTheme {
            Box(Modifier.fillMaxSize().background(Color(0xFF2A3A44))) {
                PlayerShade(Modifier.fillMaxSize())
                PlayerOsd(model, noActions, remember { FocusRequester() })
            }
        }
    }
}

private val noActions = OsdActions({}, {}, {}, {}, {}, {}, {}, {})

private fun osdModel(
    infoRight: String, quality: String, version: String, subs: String, save: String, next: String,
) = OsdModel(
    showTitle = "Abgründe", channel = "ARD", infoRight = infoRight,
    episodeTitle = "Folge 3: Der Summstein", subLine = "Folge 3 · Sa., 14. November · verfügbar bis 14.02.2027",
    positionSeconds = 612, durationSeconds = 2210, seekTargetSeconds = null, seekHint = null, playing = true,
    speedLabel = "1×", versionAvailable = true, versionLabel = version,
    qualityAvailable = true, qualityLabel = quality,
    subtitlesAvailable = true, subtitlesLabel = subs, bookmarked = false, bookmarkLabel = save, hasNext = true, nextLabel = next,
)

@Config(sdk = [35], application = Application::class, qualifiers = PLAYER_TV_DE)
class GermanPlayerScreenshot : PlayerScreenshotTest(
    "de", osdModel("MP4 · HD 720p", "Qualität", "Ton / Sprache", "Untertitel", "Merken", "Nächste Folge"),
)

@Config(sdk = [35], application = Application::class, qualifiers = PLAYER_TV_EN)
class EnglishPlayerScreenshot : PlayerScreenshotTest(
    "en", osdModel("MP4 · HD 720p", "Quality", "Audio / Language", "Subtitles", "Save", "Next episode"),
)
