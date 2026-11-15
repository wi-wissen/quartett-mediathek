package net.wi7.quartettmediathek.source

import io.ktor.client.request.HttpRequestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.SubtitleFormat
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.source.ard.ArdClient
import net.wi7.quartettmediathek.source.ard.cleanArdShowTitle

class ArdClientTest {
    private val groupingId = "Y3JpZDovL2Rhc2Vyc3RlLmRlL2RpZS1mcmllc2VuanVua2llcw"

    private fun client(seen: MutableList<HttpRequestData> = mutableListOf(), item: String = Fixtures.text("ard_item.json")) =
        ArdClient(
            mockClient(seen) { r ->
                val url = r.url.toString()
                when {
                    "/pages/ard/home" in url -> Reply(Fixtures.text("ard_home.json"))
                    "/widgets/ard/editorials/" in url -> Reply(Fixtures.text("ard_widget.json"))
                    "/pages/ard/grouping/$groupingId" in url -> Reply(Fixtures.text("ard_grouping.json"))
                    "/pages/ard/item/" in url -> Reply(item)
                    else -> null
                }
            },
        )

    @Test
    fun startPageSkipsMenuAndMapsTeasers() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val rows = client(seen).startPageRows()
        val titles = rows.map { it.title }
        assertTrue("Menü" !in titles)
        assertTrue("Unsere Top-Serien" in titles)
        assertTrue(rows.size > 30)
        val row = rows.first()
        // widget fixture: 10 shows and one "editorialPage" teaser that must be ignored
        assertEquals(10, row.items.size)
        val show = (row.items.first() as Teaser.OfShow).show
        assertEquals("Die Friesenjunkies", show.title)
        assertEquals(ShowRef(SourceId.ARD, groupingId), show.ref)
        assertEquals("ARD", show.channelLabel)
        assertTrue(show.imageUrl!!.contains("w=960") && !show.imageUrl!!.contains("{width}"))
        assertTrue(seen.any { "editorials/1FdQ5oz2JK6o2qmyqMsqiI%3A" in it.url.toString() })
    }

    @Test
    fun groupingGivesShowAndEpisodesNewestFirst() = runTest {
        val ref = ShowRef(SourceId.ARD, groupingId)
        val c = client()
        val show = c.show(ref)
        assertEquals("Die Friesenjunkies", show.title)
        assertTrue(show.description!!.startsWith("Willkommen"))
        val episodes = c.episodes(ref)
        assertEquals(17, episodes.size)
        assertEquals(episodes.sortedByDescending { it.publishedAt }, episodes)
        val e = episodes.first()
        assertTrue(e.title.isNotBlank() && e.durationSeconds > 0 && e.availableUntil != null)
        assertTrue(e.hasSubtitles)
        assertTrue(Variant.STANDARD in e.variants)
        assertTrue(episodes.all { it.ref.source == SourceId.ARD })
    }

    @Test
    fun resolvePicksStandardSoundMp4In1080p() = runTest {
        val stream = client().resolve(EpisodeRef(SourceId.ARD, "abc"))
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(1080, stream.height)
        assertTrue(stream.url.contains("sendeton") && stream.url.contains("1920x1080"), stream.url)
        assertEquals(1, stream.subtitles.size)
        assertEquals(SubtitleFormat.WEBVTT, stream.subtitles.single().format)
        assertTrue(stream.subtitles.single().url.endsWith(".vtt"))
        assertTrue("standard" in stream.audioTracks && "audio-description" in stream.audioTracks)
    }

    @Test
    fun resolveListsStandardSoundAlternativesBestFirst() = runTest {
        val stream = client().resolve(EpisodeRef(SourceId.ARD, "abc"))
        assertEquals(listOf(1080, 720, 540, 360), stream.alternatives.map { it.height })
        assertEquals(stream.url, stream.alternatives.first().url)
        assertTrue(stream.alternatives.all { it.mimeType == "video/mp4" && "sendeton" in it.url })
        assertTrue(stream.alternatives.last().url.contains("640x360"))
    }

    @Test
    fun hlsOnlyHasNoAlternatives() = runTest {
        val hlsOnly = Fixtures.text("ard_item.json").replace("\"mimeType\":\"video/mp4\"", "\"mimeType\":\"video/x-none\"")
        assertTrue(client(item = hlsOnly).resolve(EpisodeRef(SourceId.ARD, "abc")).alternatives.isEmpty())
    }

    @Test
    fun resolveFallsBackToHls() = runTest {
        val hlsOnly = Fixtures.text("ard_item.json").replace("\"mimeType\":\"video/mp4\"", "\"mimeType\":\"video/x-none\"")
        val stream = client(item = hlsOnly).resolve(EpisodeRef(SourceId.ARD, "abc"))
        assertEquals("application/vnd.apple.mpegurl", stream.mimeType)
        assertTrue(stream.url.endsWith("master.m3u8"))
    }

    @Test
    fun loginOnlyItemsFail() = runTest {
        val blocked = Fixtures.text("ard_item.json").replace("\"blockedByLoginOnly\":false", "\"blockedByLoginOnly\":true")
        assertTrue(runCatching { client(item = blocked).resolve(EpisodeRef(SourceId.ARD, "abc")) }.exceptionOrNull() is SourceException)
    }

    @Test
    fun cleansShowTitles() {
        assertEquals("Die Friesenjunkies", cleanArdShowTitle("Die Friesenjunkies | Impro-Dramedy-Serie"))
        assertEquals("Das Vergessen", cleanArdShowTitle("Das Vergessen · Spurlos im Moor | Thriller-Serie"))
        assertEquals("Babylon Berlin", cleanArdShowTitle("Babylon Berlin - Staffel 4"))
        assertEquals("Tatort", cleanArdShowTitle("Tatort"))
    }
}
