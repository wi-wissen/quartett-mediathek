package net.wi7.quartettmediathek.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.wi7.quartettmediathek.feed.episode
import net.wi7.quartettmediathek.feed.t
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.repository.Progress

class ProgressIndexTest {
    private val at = t("2026-11-14T12:00:00Z")
    private fun progress(key: String, show: String, title: String, minutes: Int = 60, source: SourceId = SourceId.ZDF) =
        Progress(
            episode(key, show, title, Broadcaster.ZDF, "2026-11-01T20:15:00Z", minutes).let { it.copy(ref = EpisodeRef(source, key)) },
            100, false, at,
        )

    @Test
    fun matchesByRef() {
        val p = progress("canonical", "Show", "Folge 1")
        assertEquals(p, ProgressIndex(listOf(p)).find(p.episode))
    }

    @Test
    fun sameBroadcastUnderAnotherRefMatchesByFingerprint() {
        // Played from the ZDF show page, shown in the feed as a MediathekViewWeb hit with decorated title.
        val played = progress("schatten-und-flammen", "Der Herr der Ringe: Die Ringe der Macht", "Schatten und Flammen")
        val mvw = episode(
            "mvw-123", "Der Herr der Ringe: Die Ringe der Macht", "Schatten und Flammen (S1/E2)", Broadcaster.ZDF, "2026-11-01T20:15:00Z", 61,
        )
        assertEquals(played, ProgressIndex(listOf(played)).find(mvw))
    }

    @Test
    fun differentTitleOrBroadcasterOrLengthDoesNotMatch() {
        val played = progress("a", "Show", "Folge 1", minutes = 60)
        val index = ProgressIndex(listOf(played))
        assertNull(index.find(episode("x", "Show", "Folge 2", Broadcaster.ZDF, "2026-11-01T20:15:00Z", 60)))
        assertNull(index.find(episode("x", "Show", "Folge 1", Broadcaster.ARD, "2026-11-01T20:15:00Z", 60)))
        assertNull(index.find(episode("x", "Show", "Folge 1", Broadcaster.ZDF, "2026-11-01T20:15:00Z", 10)))
    }

    @Test
    fun newestEntryWinsForTheSameBroadcast() {
        val old = progress("a", "Show", "Folge 1").let { it.copy(positionSeconds = 5, updatedAt = t("2026-11-10T12:00:00Z")) }
        val new = progress("b", "Show", "Folge 1", source = SourceId.MVW).copy(positionSeconds = 50)
        val other = episode("c", "Show", "Folge 1", Broadcaster.ZDF, "2026-11-01T20:15:00Z", 60)
        assertEquals(50, ProgressIndex(listOf(old, new)).find(other)?.positionSeconds)
    }
}
