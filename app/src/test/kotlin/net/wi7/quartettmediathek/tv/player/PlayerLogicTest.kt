package net.wi7.quartettmediathek.tv.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Variant

class PlayerLogicTest {
    private fun ep(key: String, day: Int) = Episode(
        ref = EpisodeRef(SourceId.ARD, key), showRef = null, showTitle = "S", title = key,
        broadcaster = Broadcaster.ARD, channelLabel = "NDR", description = null, imageUrl = null,
        durationSeconds = 2700, publishedAt = Instant.fromEpochSeconds(day * 86400L),
    )

    @Test fun osdHidesOnlyWhilePlayingAfterTimeout() {
        assertFalse(PlayerLogic.shouldHideOsd(true, 0, 3999))
        assertTrue(PlayerLogic.shouldHideOsd(true, 0, 4000))
        assertFalse(PlayerLogic.shouldHideOsd(false, 0, 60_000))
    }

    @Test fun speedCycles() {
        assertEquals(1f, PlayerLogic.nextSpeed(0.75f))
        assertEquals(1.25f, PlayerLogic.nextSpeed(1f))
        assertEquals(0.75f, PlayerLogic.nextSpeed(2f))
        assertEquals(1f, PlayerLogic.nextSpeed(3f))
    }

    @Test fun speedLabels() {
        assertEquals("1×", PlayerLogic.formatSpeed(1f, ','))
        assertEquals("1,25×", PlayerLogic.formatSpeed(1.25f, ','))
        assertEquals("1.25×", PlayerLogic.formatSpeed(1.25f, '.'))
        assertEquals("0.75×", PlayerLogic.formatSpeed(0.75f, '.'))
        assertEquals("2×", PlayerLogic.formatSpeed(2f, '.'))
    }

    @Test fun nextEpisodeIsNextInPublicationOrder() {
        val list = listOf(ep("c", 3), ep("b", 2), ep("a", 1)) // newest first
        assertEquals("c", PlayerLogic.nextEpisode(list, EpisodeRef(SourceId.ARD, "b"))?.ref?.key)
        assertEquals("b", PlayerLogic.nextEpisode(list, EpisodeRef(SourceId.ARD, "a"))?.ref?.key)
        assertNull(PlayerLogic.nextEpisode(list, EpisodeRef(SourceId.ARD, "c")))
        assertNull(PlayerLogic.nextEpisode(list, EpisodeRef(SourceId.ARD, "zzz")))
    }

    @Test fun watchedThreshold() {
        assertFalse(PlayerLogic.isWatched(949, 1000))
        assertTrue(PlayerLogic.isWatched(950, 1000))
        assertFalse(PlayerLogic.isWatched(10, 0))
    }

    @Test fun startPosition() {
        assertEquals(0, PlayerLogic.startPosition(0, 500, false, 1000))
        assertEquals(120, PlayerLogic.startPosition(120, 500, false, 1000))
        assertEquals(500, PlayerLogic.startPosition(null, 500, false, 1000))
        assertEquals(0, PlayerLogic.startPosition(null, null, false, 1000))
        assertEquals(0, PlayerLogic.startPosition(null, 500, true, 1000))
        assertEquals(0, PlayerLogic.startPosition(null, 980, false, 1000))
    }

    @Test fun variantChoice() {
        val both = setOf(Variant.STANDARD, Variant.AUDIO_DESCRIPTION)
        assertEquals(Variant.AUDIO_DESCRIPTION, PlayerLogic.preferredVariant(Variant.AUDIO_DESCRIPTION, both))
        assertEquals(Variant.STANDARD, PlayerLogic.preferredVariant(Variant.SIGN_LANGUAGE, both))
        assertEquals(Variant.AUDIO_DESCRIPTION, PlayerLogic.preferredVariant(null, setOf(Variant.AUDIO_DESCRIPTION)))
    }

    @Test fun timeFormatting() {
        assertEquals("7:20", PlayerLogic.formatTime(440))
        assertEquals("1:02:03", PlayerLogic.formatTime(3723))
        assertEquals("+7:20 · 1 min", PlayerLogic.seekHint(440, "1 min"))
        assertEquals("−0:10", PlayerLogic.seekHint(-10, null))
    }
}
