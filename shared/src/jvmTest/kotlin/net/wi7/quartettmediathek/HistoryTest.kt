package net.wi7.quartettmediathek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.feed.episode
import net.wi7.quartettmediathek.feed.t
import net.wi7.quartettmediathek.library.HISTORY_LIMIT
import net.wi7.quartettmediathek.library.SqlLibraryRepository
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory

private class StepClock(var current: Instant) : Clock {
    override fun now(): Instant = current.also { current += 1.minutes }
}

class HistoryTest {
    private val clock = StepClock(t("2026-11-14T12:00:00Z"))
    private fun repo(continueLimit: Int = 20) =
        SqlLibraryRepository(QuartettDatabase(DatabaseDriverFactory().create()), clock = clock, continueWatchingLimit = continueLimit)

    private fun ep(key: String) = episode(key, "show", "Title $key", Broadcaster.ZDF, "2026-11-12T18:30:00Z", minutes = 100)

    @Test
    fun historyHasStartedAndWatchedEpisodesNewestFirst() = runTest {
        val repo = repo()
        repo.saveProgress(ep("a"), 100)          // in progress
        repo.setWatched(ep("b"), true)           // watched
        repo.saveProgress(ep("c"), 0)            // opened but nothing played: not part of the history
        repo.saveProgress(ep("d"), 6000)         // finished
        assertEquals(listOf("d", "b", "a"), repo.history().first().map { it.episode.ref.key })
        assertEquals(listOf(false, true, true), repo.history().first().map { it.watched }.reversed())
    }

    @Test
    fun allProgressIsNotLimitedLikeContinueWatching() = runTest {
        val repo = repo(continueLimit = 3)
        (1..30).forEach { repo.saveProgress(ep("e$it"), 100) }
        assertEquals(3, repo.continueWatching().first().size)
        // The old overlay only knew continueWatching(): 27 started episodes lost their progress bar.
        assertEquals(30, repo.allProgress().first().size)
    }

    @Test
    fun historyIsLimited() = runTest {
        val repo = repo()
        (1..HISTORY_LIMIT + 5).forEach { repo.saveProgress(ep("e$it"), 100) }
        val history = repo.history().first()
        assertEquals(HISTORY_LIMIT, history.size)
        assertEquals("e${HISTORY_LIMIT + 5}", history.first().episode.ref.key)
    }

    @Test
    fun watchedMarkSurvivesWatchingAgain() = runTest {
        val repo = repo()
        val e = ep("a")
        repo.saveProgress(e, 6000)
        repo.saveProgress(e, 30) // restarted from the beginning
        assertTrue(repo.progress(e.ref).first()!!.watched)
    }

    @Test
    fun removeFromHistory() = runTest {
        val repo = repo()
        repo.saveProgress(ep("a"), 100)
        repo.setWatched(ep("b"), true)
        repo.removeFromHistory(ep("a").ref)
        repo.removeFromHistory(ep("b").ref)
        assertTrue(repo.history().first().isEmpty())
        assertTrue(repo.watchedKeys().first().isEmpty())
    }
}
