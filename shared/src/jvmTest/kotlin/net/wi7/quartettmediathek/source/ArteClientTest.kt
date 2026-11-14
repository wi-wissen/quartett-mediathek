package net.wi7.quartettmediathek.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.source.arte.ArteClient

class ArteClientTest {
    private val client = ArteClient(
        mockClient { r ->
            val url = r.url.toString()
            when {
                url == "https://api.arte.tv/api/emac/v4/de/web/pages/HOME/" -> Reply(Fixtures.text("arte_home.json"))
                url == "https://api.arte.tv/api/player/v2/config/de/128830-009-A" -> Reply(Fixtures.text("arte_player.json"))
                else -> null
            }
        },
    )

    @Test
    fun homeRowsContainProgrammeTeasers() = runTest {
        val rows = client.startPageRows()
        assertTrue(rows.size >= 4)
        val teasers = rows.flatMap { it.items }
        val episodes = teasers.filterIsInstance<Teaser.OfEpisode>().map { it.episode }
        assertTrue(episodes.size > 10)
        assertTrue(episodes.all { it.title.isNotBlank() && it.ref.source == SourceId.ARTE && it.ref.key.isNotBlank() })
        assertTrue(episodes.any { it.durationSeconds > 0 && it.availableUntil != null })
        assertTrue(episodes.all { it.imageUrl == null || !it.imageUrl!!.contains("__SIZE__") })
        // collections (series, magazines) come as shows
        assertTrue(teasers.any { it is Teaser.OfShow })
        // "Alle Genres" style external links are dropped
        assertTrue(rows.none { r -> r.items.isEmpty() })
    }

    @Test
    fun playerConfigGivesHlsStream() = runTest {
        val stream = client.resolve(EpisodeRef(SourceId.ARTE, "128830-009-A"))
        assertTrue(stream.url.startsWith("https://manifest-arte.akamaized.net/") && stream.url.endsWith(".m3u8"))
        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(listOf("DE", "UT", "FR"), stream.audioTracks)
    }

    @Test
    fun playerConfigGivesMetadata() = runTest {
        val p = client.program("128830-009-A")
        assertEquals("Twist", p.title)
        assertEquals("Birdwatching: Verrückt nach Vögeln", p.subtitle)
        assertEquals(1801, p.durationSeconds)
        assertTrue(p.imageUrl!!.startsWith("https://"))
        val e = client.episode(EpisodeRef(SourceId.ARTE, "128830-009-A"))
        assertEquals("Birdwatching: Verrückt nach Vögeln", e.title)
        assertEquals("Twist", e.showTitle)
    }
}
