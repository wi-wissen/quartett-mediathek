package net.wi7.quartettmediathek.tv.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.library.ProgressIndex
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.Progress
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.data.fake.FakeLibraryRepository
import net.wi7.quartettmediathek.tv.ui.components.tileProgressBar
import net.wi7.quartettmediathek.tv.ui.screens.historyTiles

class TileMarkingTest {
    private val episodes = FakeData.allEpisodes

    @Test
    fun everyStartedEpisodeIsMarkedNotOnlyTheTwentyMostRecent() {
        val started = episodes.take(30).mapIndexed { i, e -> Progress(e, 100, false, FakeData.now - i.minutes) }
        val overlay = LibraryOverlay(progress = ProgressIndex(started))
        started.forEach { assertEquals(100L, overlay.decorate(it.episode).progressSeconds, it.episode.ref.key) }
    }

    @Test
    fun watchedAndStartedAreExclusive() {
        val a = episodes[0]
        val b = episodes[1]
        val overlay = LibraryOverlay(
            progress = ProgressIndex(
                listOf(
                    Progress(a, 100, false, FakeData.now),
                    Progress(b, b.durationSeconds.toLong(), true, FakeData.now),
                ),
            ),
        )
        assertEquals(100L, overlay.decorate(a).progressSeconds)
        assertFalse(overlay.decorate(a).watched)
        assertNull(overlay.decorate(b).progressSeconds)
        assertTrue(overlay.decorate(b).watched)
        assertFalse(overlay.decorate(episodes[2]).watched)
    }

    @Test
    fun playedUnderAnotherRefStillMarksTheTile() {
        val played = episodes[0].copy(ref = EpisodeRef(SourceId.ZDF, "zdf-canonical"))
        val overlay = LibraryOverlay(progress = ProgressIndex(listOf(Progress(played, played.durationSeconds.toLong(), true, FakeData.now))))
        assertTrue(overlay.decorate(episodes[0]).watched)
    }

    @Test
    fun overlayFromLibraryMarksSampleData() = runTest {
        val overlay = FakeLibraryRepository().overlay().first()
        FakeData.initialWatched.forEach { assertTrue(overlay.decorate(FakeData.episode(it)).watched, it) }
        FakeData.initialProgress.forEach { (key, pos) -> assertEquals(pos, overlay.decorate(FakeData.episode(key)).progressSeconds, key) }
    }

    @Test
    fun barIsFullWhenWatchedPartialWhenStarted() {
        assertEquals(1f, tileProgressBar(null, true))
        assertEquals(1f, tileProgressBar(0.3f, true))
        assertEquals(0.3f, tileProgressBar(0.3f, false))
        assertNull(tileProgressBar(null, false))
        assertNull(tileProgressBar(0f, false))
        assertEquals(0.02f, tileProgressBar(0.001f, false)) // still visible
    }

    @Test
    fun historyTilesKeepOrderAndShowPlayTime() = runTest {
        val library = FakeLibraryRepository()
        val tiles = historyTiles(library.history().first(), library.overlay().first())
        assertEquals("abgruende-3", (tiles.first().teaser as Teaser.OfEpisode).episode.ref.key)
        assertTrue(tiles.zipWithNext().all { (a, b) -> a.playedAt!! >= b.playedAt!! })
        assertEquals(FakeData.initialProgress.size + FakeData.initialWatched.size, tiles.size)
        assertTrue(tiles.any { it.watched } && tiles.any { it.progressSeconds != null })
    }

    @Test
    fun removeFromHistoryClearsTheMarker() = runTest {
        val library = FakeLibraryRepository()
        val e = FakeData.episode("abgruende-3")
        library.removeFromHistory(e.ref)
        assertNull(library.overlay().first().decorate(e).progressSeconds)
        assertTrue(library.history().first().none { it.episode.ref == e.ref })
    }
}
