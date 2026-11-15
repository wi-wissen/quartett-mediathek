package net.wi7.quartettmediathek.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.wi7.quartettmediathek.model.Broadcaster

class EpisodeOrderTest {
    private fun ep(s: Int, e: Int, at: String = "2025-10-30T08:00:00Z") =
        episode("s${s}e$e", "Ringe", "S$s E$e", Broadcaster.ZDF, at).copy(season = s, episodeNumber = e)

    // Both seasons published at the same moment, as on ZDFneo.
    private val ringe = listOf(ep(1, 2), ep(2, 8), ep(1, 1), ep(2, 1), ep(1, 3))

    @Test
    fun sameDayIsOrderedBySeasonAndEpisode() {
        assertEquals(listOf("s2e8", "s2e1", "s1e3", "s1e2", "s1e1"), ringe.sortedWith(EpisodeOrder.newestFirst).map { it.ref.key })
    }

    @Test
    fun dateStillWinsOverNumbers() {
        val later = ep(1, 1, "2025-11-01T08:00:00Z")
        assertEquals("s1e1", (ringe.drop(1) + later).sortedWith(EpisodeOrder.newestFirst).first().ref.key)
    }

    @Test
    fun seriesStartsAtTheBeginningAndContinuesAfterTheLastWatched() {
        assertEquals("s1e1", EpisodeOrder.nextUp(ringe) { false }?.ref?.key)
        assertEquals("s2e1", EpisodeOrder.nextUp(ringe) { it.ref.key in setOf("s1e1", "s1e3") }?.ref?.key)
        assertEquals("s1e2", EpisodeOrder.nextUp(ringe) { it.ref.key in setOf("s1e1", "s1e3", "s2e1", "s2e8") }?.ref?.key)
        assertEquals("s1e1", EpisodeOrder.nextUp(ringe) { true }?.ref?.key)
    }

    @Test
    fun showsWithoutSeasonsStartWithTheNewest() {
        val nano = listOf(
            episode("old", "nano", "a", Broadcaster.THREESAT, "2026-11-10T18:30:00Z"),
            episode("new", "nano", "b", Broadcaster.THREESAT, "2026-11-11T18:30:00Z"),
        )
        assertEquals("new", EpisodeOrder.nextUp(nano) { false }?.ref?.key)
        assertEquals("old", EpisodeOrder.nextUp(nano) { it.ref.key == "new" }?.ref?.key)
    }

    @Test
    fun yearsAreNoSeasons() {
        assertNull(EpisodeOrder.displaySeason(ep(2026, 145)))
        assertEquals(2, EpisodeOrder.displaySeason(ep(2, 1)))
    }
}
