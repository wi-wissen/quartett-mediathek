package net.wi7.quartettmediathek.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Variant

class EpisodeMergerTest {
    private val anstaltZdf = candidate(
        "zdf1", "Die Anstalt", "Die Anstalt vom 10. November 2026", Broadcaster.ZDF,
        "2026-11-10T21:15:00Z", url = "https://cdn/anstalt.mp4",
    )
    private val anstaltSat = candidate(
        "sat1", "Die Anstalt", "Die Anstalt (S2026/E145)", Broadcaster.THREESAT,
        "2026-11-10T21:15:30Z", url = "https://cdn/anstalt.mp4",
    )

    @Test
    fun sameVideoUrlKeepsOneAndPrefersSubscriptionBroadcaster() {
        val deduped = EpisodeMerger.dedupeBroadcasts(listOf(anstaltZdf, anstaltSat), listOf(Broadcaster.THREESAT, Broadcaster.ZDF))
        assertEquals(listOf("sat1"), deduped.map { it.episode.ref.key })
        val other = EpisodeMerger.dedupeBroadcasts(listOf(anstaltSat, anstaltZdf), listOf(Broadcaster.ZDF))
        assertEquals(listOf("zdf1"), other.map { it.episode.ref.key })
    }

    @Test
    fun withoutPreferenceFirstInputWins() {
        assertEquals("zdf1", EpisodeMerger.dedupeBroadcasts(listOf(anstaltZdf, anstaltSat)).single().episode.ref.key)
    }

    @Test
    fun fallbackKeyNeedsTimeDurationAndSize() {
        fun c(key: String, b: Broadcaster, at: String, minutes: Int, size: Long?) =
            candidate(key, "nano", "nano", b, at, minutes, url = null, size = size)
        val a = c("a", Broadcaster.ZDF, "2026-11-10T19:00:00Z", 30, 1000)
        val sameBroadcast = c("b", Broadcaster.THREESAT, "2026-11-10T19:01:30Z", 30, 1000)
        val otherSize = c("c", Broadcaster.THREESAT, "2026-11-10T19:00:00Z", 30, 2000)
        val otherTime = c("d", Broadcaster.THREESAT, "2026-11-10T19:10:00Z", 30, 1000)
        val noSize = c("e", Broadcaster.THREESAT, "2026-11-10T19:00:00Z", 30, null)
        val result = EpisodeMerger.dedupeBroadcasts(listOf(a, sameBroadcast, otherSize, otherTime, noSize))
        assertEquals(listOf("a", "c", "d", "e"), result.map { it.episode.ref.key })
    }

    @Test
    fun dedupeFillsMissingImageFromLosingCandidate() {
        val withImage = anstaltSat.copy(episode = anstaltSat.episode.copy(imageUrl = "https://img/1.jpg"))
        val merged = EpisodeMerger.dedupeBroadcasts(listOf(anstaltZdf, withImage)).single()
        assertEquals("zdf1", merged.episode.ref.key)
        assertEquals("https://img/1.jpg", merged.episode.imageUrl)
    }

    private val standard = candidate("std", "nano", "Game of Drones", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", url = "u1")
    private val ad = candidate("ad", "nano", "Game of Drones (Audiodeskription)", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", url = "u2")

    @Test
    fun variantsMergeIntoOneEpisode() {
        val merged = EpisodeMerger.merge(listOf(standard, ad)).single()
        assertEquals("Game of Drones", merged.episode.title)
        assertEquals("std", merged.episode.ref.key)
        assertEquals(setOf(Variant.STANDARD, Variant.AUDIO_DESCRIPTION), merged.episode.variants)
        assertEquals("ad", merged.representations.getValue(Variant.AUDIO_DESCRIPTION).ref.key)
    }

    @Test
    fun preferredVariantPicksItsRepresentation() {
        val merged = EpisodeMerger.merge(listOf(ad, standard), Variant.AUDIO_DESCRIPTION).single()
        assertEquals("ad", merged.episode.ref.key)
        assertEquals("Game of Drones", merged.episode.title)
        assertEquals(2, merged.episode.variants.size)
    }

    @Test
    fun missingPreferredVariantFallsBackToStandard() {
        val merged = EpisodeMerger.merge(listOf(standard), Variant.SIGN_LANGUAGE).single()
        assertEquals("std", merged.episode.ref.key)
    }

    @Test
    fun onlyAdFileKeepsAdVariantAlone() {
        val merged = EpisodeMerger.merge(listOf(ad), Variant.STANDARD).single()
        assertEquals(setOf(Variant.AUDIO_DESCRIPTION), merged.episode.variants)
    }

    @Test
    fun differentTimestampIsAnotherEpisodeAndOutputIsNewestFirst() {
        val later = candidate("n2", "nano", "Game of Drones", Broadcaster.THREESAT, "2026-11-13T18:30:00Z", url = "u3")
        val result = EpisodeMerger.merge(listOf(standard, later))
        assertEquals(listOf("n2", "std"), result.map { it.episode.ref.key })
    }

    @Test
    fun parsesSeasonEpisodeFromTitle() {
        val m = EpisodeMerger.merge(listOf(anstaltSat)).single().episode
        assertEquals(2026, m.season)
        assertEquals(145, m.episodeNumber)
        assertEquals("Die Anstalt", m.title)
        assertNull(EpisodeMerger.merge(listOf(anstaltZdf)).single().episode.season)
    }
}
