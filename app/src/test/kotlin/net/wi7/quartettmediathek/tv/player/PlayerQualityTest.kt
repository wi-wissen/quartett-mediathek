package net.wi7.quartettmediathek.tv.player

import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.StreamVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerQualityTest {
    private fun mp4(vararg heights: Int) = StreamInfo(
        url = "https://x/${heights.firstOrNull()}.mp4", mimeType = "video/mp4", height = heights.firstOrNull(),
        alternatives = heights.map { StreamVariant(it, "https://x/$it.mp4", "video/mp4") },
    )
    private val hls = StreamInfo("https://x/master.m3u8", "application/vnd.apple.mpegurl", 1080)

    @Test fun progressiveFilesGiveAutoPlusHeightsBestFirst() {
        val options = PlayerLogic.qualityOptions(mp4(1080, 720, 540, 360), emptyList())
        assertEquals(listOf(null, 1080, 720, 540, 360), options.map { it.height })
        assertTrue(options.first().isAuto)
    }

    @Test fun adaptiveStreamUsesTrackHeights() {
        val options = PlayerLogic.qualityOptions(hls, PlayerLogic.distinctHeights(listOf(360, 720, 720, 1080, 0)))
        assertEquals(listOf(null, 1080, 720, 360), options.map { it.height })
    }

    @Test fun nothingToChooseHidesTheButton() {
        assertTrue(PlayerLogic.qualityOptions(mp4(720), emptyList()).isEmpty())
        assertTrue(PlayerLogic.qualityOptions(hls, listOf(1080)).isEmpty())
        assertTrue(PlayerLogic.qualityOptions(hls, emptyList()).isEmpty())
    }

    @Test fun selectionFollowsTheWishAndFallsBack() {
        val options = PlayerLogic.qualityOptions(mp4(1080, 720, 540), emptyList())
        assertTrue(PlayerLogic.selectedQuality(options, null).isAuto)
        assertEquals(720, PlayerLogic.selectedQuality(options, 720).height)
        // wish for a height this video lacks: the best one below, else the lowest
        assertEquals(540, PlayerLogic.selectedQuality(options, 600).height)
        assertEquals(1080, PlayerLogic.selectedQuality(options, 2160).height)
        assertEquals(540, PlayerLogic.selectedQuality(options, 270).height)
        assertTrue(PlayerLogic.selectedQuality(emptyList(), 720).isAuto)
    }

    @Test fun urlAndShownHeight() {
        val s = mp4(1080, 720)
        assertEquals("https://x/720.mp4", PlayerLogic.qualityUrl(s, QualityOption(720)))
        assertEquals(s.url, PlayerLogic.qualityUrl(s, QualityOption(null)))
        assertEquals(hls.url, PlayerLogic.qualityUrl(hls, QualityOption(720)))
        assertEquals(720, PlayerLogic.shownHeight(s, QualityOption(720)))
        assertEquals(1080, PlayerLogic.shownHeight(s, QualityOption(null)))
    }
}
