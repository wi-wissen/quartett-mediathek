package net.wi7.quartettmediathek.data

import kotlinx.coroutines.runBlocking
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.source.mvw.MvwEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ContentIndexTest {
    private class FixedClock(var now: Instant) : Clock { override fun now() = now }

    private val episode = Episode(
        ref = EpisodeRef(SourceId.ZDF, "schatten-und-flammen-100"),
        showRef = ShowRef(SourceId.ZDF, "der-herr-der-ringe-die-ringe-der-macht-100"),
        showTitle = "Der Herr der Ringe: Die Ringe der Macht",
        title = "Schatten und Flammen",
        broadcaster = Broadcaster.ZDF,
        channelLabel = "ZDF",
        description = null,
        imageUrl = "https://example.org/a.jpg",
        durationSeconds = 3600,
        publishedAt = Instant.parse("2026-11-01T20:15:00Z"),
    )
    private val mvw = MvwEntry("abc", "ZDF", "Die Anstalt", "Folge", null, 1_794_000_000, 2900, 1_000, "https://www.zdf.de/x", null, "https://v.mp4", null, null)

    @Test
    fun survivesRestart() = runBlocking {
        val db = QuartettDatabase(DatabaseDriverFactory().create())
        ContentIndex(db).apply { rememberEpisodes(listOf(episode)); rememberMvw(listOf(mvw)) }
        val afterRestart = ContentIndex(db)
        assertEquals(episode, afterRestart.episode(episode.ref))
        assertEquals(mvw, afterRestart.mvwEntry("abc"))
    }

    @Test
    fun prunesUnusedEntries() = runBlocking {
        val db = QuartettDatabase(DatabaseDriverFactory().create())
        val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
        ContentIndex(db, clock).rememberEpisodes(listOf(episode))
        clock.now += 91.days
        ContentIndex(db, clock).prune()
        assertNull(ContentIndex(db, clock).episode(episode.ref))
    }

    @Test
    fun keepsAtMostMaxItems() = runBlocking {
        val db = QuartettDatabase(DatabaseDriverFactory().create())
        val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
        val index = ContentIndex(db, clock, maxItems = 2)
        for (i in 1..3) {
            index.rememberEpisodes(listOf(episode.copy(ref = EpisodeRef(SourceId.ZDF, "e$i"))))
            clock.now += 1.days
        }
        index.prune()
        assertEquals(2L, db.contentCacheQueries.countContentItems().executeAsOne())
        assertNull(ContentIndex(db, clock).episode(EpisodeRef(SourceId.ZDF, "e1")))
    }
}
