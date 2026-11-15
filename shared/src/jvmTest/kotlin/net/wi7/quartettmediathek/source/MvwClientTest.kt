package net.wi7.quartettmediathek.source

import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.source.mvw.MvwClause
import net.wi7.quartettmediathek.source.mvw.MvwClient
import net.wi7.quartettmediathek.source.mvw.MvwField
import net.wi7.quartettmediathek.source.mvw.MvwQuery
import net.wi7.quartettmediathek.source.mvw.ardItemId
import net.wi7.quartettmediathek.source.mvw.toEpisode
import net.wi7.quartettmediathek.source.mvw.toEpisodes
import net.wi7.quartettmediathek.source.mvw.toStreamInfo

class MvwClientTest {
    private fun client(fixture: String, seen: MutableList<HttpRequestData> = mutableListOf()) =
        MvwClient(mockClient(seen) { Reply(Fixtures.text(fixture)) })

    @Test
    fun sendsPlainTextPostAndParsesAnstalt() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val result = client("mvw_anstalt.json", seen).query(MvwQuery(listOf(MvwClause(MvwField.TOPIC, "die anstalt"))))
        val request = seen.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://mediathekviewweb.de/api/query", request.url.toString())
        val body = request.body as TextContent
        assertTrue(body.contentType.toString().startsWith("text/plain"))
        assertTrue(body.text.contains(""""query":"die anstalt""""))
        assertTrue(request.headers["User-Agent"]!!.startsWith("QuartettMediathek/"))

        assertEquals(15, result.entries.size)
        assertTrue(result.totalResults >= 15)
        val first = result.entries.first()
        assertEquals("Die Anstalt", first.topic)
        assertTrue(first.urlVideo!!.endsWith(".mp4"))
        assertNotNull(first.urlSubtitle)
    }

    @Test
    fun mapsToEpisodesAndDropsOutOfScopeChannels() = runTest {
        val result = client("mvw_nano.json").query(MvwQuery(emptyList()))
        assertTrue(result.entries.any { it.channel == "SRF" })
        val episodes = result.entries.toEpisodes()
        assertTrue(episodes.isNotEmpty())
        assertTrue(episodes.size < result.entries.size, "SRF must be dropped")
        assertTrue(episodes.all { it.broadcaster == Broadcaster.THREESAT })
        assertTrue(episodes.all { it.title.isNotBlank() && it.durationSeconds > 0 && it.ref.source == SourceId.MVW })
        assertNull(result.entries.first { it.channel == "SRF" }.toEpisode())
    }

    @Test
    fun extractsArdItemIdFromWebsiteUrl() = runTest {
        val entries = client("mvw_friesen.json").query(MvwQuery(emptyList())).entries
        assertEquals(9, entries.size)
        assertTrue(entries.all { Regex("^Y3JpZDovL2FyZC5kZS[A-Za-z0-9_-]+$").matches(it.ardItemId.orEmpty()) })
        assertEquals(Broadcaster.ARD, entries.first().toEpisode()!!.broadcaster)
        // 3sat entries do not point to the ARD Mediathek
        val nano = client("mvw_nano.json").query(MvwQuery(emptyList())).entries
        assertTrue(nano.all { it.ardItemId == null })
    }

    @Test
    fun audioDescriptionTitlesGetVariant() = runTest {
        val episodes = client("mvw_friesen.json").query(MvwQuery(emptyList())).entries.toEpisodes()
        assertTrue(episodes.any { net.wi7.quartettmediathek.model.Variant.AUDIO_DESCRIPTION in it.variants })
    }

    @Test
    fun plainMp4StreamPrefersHd() = runTest {
        val entry = client("mvw_anstalt.json").query(MvwQuery(emptyList())).entries.first()
        val stream = entry.toStreamInfo()!!
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(entry.urlVideoHd ?: entry.urlVideo, stream.url)
        assertEquals(1, stream.subtitles.size)
    }

    @Test
    fun mp4StreamListsAllQualitiesBestFirst() = runTest {
        val entry = client("mvw_friesen.json").query(MvwQuery(emptyList())).entries.first()
        val stream = entry.toStreamInfo()!!
        assertEquals(listOf(720, 540, 360).take(stream.alternatives.size), stream.alternatives.map { it.height }.sortedDescending())
        assertEquals(listOf(entry.urlVideoHd, entry.urlVideo, entry.urlVideoLow), stream.alternatives.map { it.url })
        assertEquals(stream.url, stream.alternatives.first().url)
        val onlyLow = entry.copy(urlVideoHd = null, urlVideo = null).toStreamInfo()!!
        assertTrue(onlyLow.alternatives.size == 1)
    }

    @Test
    fun errorsBecomeSourceException() = runTest {
        val c = MvwClient(mockClient { Reply("""{"err":["bad"]}""") })
        val failure = runCatching { c.query(MvwQuery(emptyList())) }.exceptionOrNull()
        assertTrue(failure is SourceException)
        val http = MvwClient(mockClient { Reply("", io.ktor.http.HttpStatusCode.BadGateway) })
        assertTrue(runCatching { http.query(MvwQuery(emptyList())) }.exceptionOrNull() is SourceException)
    }
}
