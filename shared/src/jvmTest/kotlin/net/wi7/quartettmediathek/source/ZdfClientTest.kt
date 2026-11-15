package net.wi7.quartettmediathek.source

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.SubtitleFormat
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.source.zdf.ZdfClient
import net.wi7.quartettmediathek.source.zdf.parseZdfCredentials

class ZdfClientTest {
    private fun graphQlRoute(r: HttpRequestData): Reply? {
        val body = (r.body as TextContent).text
        return when {
            "curatedCollectionByCanonical" in body -> Reply(Fixtures.text("zdf_start.json"))
            "bloss-nicht-liebe-movie-100" in body -> Reply(Fixtures.text("zdf_movie.json"))
            "smartCollectionByCanonical" in body -> Reply(Fixtures.text("zdf_season.json"))
            "videoByCanonical" in body -> Reply(Fixtures.text("zdf_video.json"))
            else -> null
        }
    }

    private fun client(seen: MutableList<HttpRequestData> = mutableListOf(), extra: (HttpRequestData) -> Reply? = { null }) =
        ZdfClient(
            mockClient(seen) { r ->
                val url = r.url.toString()
                extra(r) ?: when {
                    url == "https://www.zdf.de/" -> Reply(Fixtures.text("zdf_home.html"))
                    url == "https://api.zdf.de/graphql" -> graphQlRoute(r)
                    "/tmd/2/ngplayer_2_5/vod/ptmd/" in url -> Reply(Fixtures.text("zdf_ptmd.json"))
                    else -> null
                }
            },
        )

    @Test
    fun readsTokenAndAppIdFromHtml() {
        val c = parseZdfCredentials(Fixtures.text("zdf_home.html"))
        assertEquals("ahBaeMeekaiy5ohsai4bee4ki6Oopoi5quailieb", c.apiToken)
        assertEquals("zdf-web-80a9684f", c.appId)
    }

    @Test
    fun startPageRowsMapShowsAndEpisodes() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val rows = client(seen).startPageRows()
        assertTrue(rows.size >= 15)
        assertEquals("Zwischen Notaufnahme & Herzenssache", rows.first().title)
        val show = (rows.first().items.first() as Teaser.OfShow).show
        assertEquals("KRANK BERLIN", show.title)
        assertEquals(ShowRef(SourceId.ZDF, "krank-berlin-100"), show.ref)
        assertTrue(show.imageUrl!!.startsWith("https://"))
        assertTrue(rows.flatMap { it.items }.any { it is Teaser.OfEpisode })
        val graphQl = seen.last { it.url.toString().endsWith("/graphql") }
        assertEquals("Bearer ahBaeMeekaiy5ohsai4bee4ki6Oopoi5quailieb", graphQl.headers["Api-Auth"])
        assertEquals("zdf-web-80a9684f", graphQl.headers["Zdf-App-Id"])
        assertTrue(!(graphQl.body as TextContent).text.contains("@client"))
    }

    @Test
    fun showAndEpisodes() = runTest {
        val ref = ShowRef(SourceId.ZDF, "krank-berlin-100")
        val c = client()
        val (show, episodes) = c.loadShow(ref)
        assertEquals("KRANK BERLIN", show.title)
        assertTrue(show.description!!.startsWith("Unterbesetzt"))
        assertEquals(8, episodes.size)
        assertEquals(8, c.episodes(ref).size)
        // same editorial date: highest episode number first
        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1), episodes.map { it.episodeNumber })
        val e = episodes.last()
        assertEquals("Symptome", e.title)
        assertEquals(2528, e.durationSeconds)
        assertEquals("symptome-104", e.ref.key)
        assertEquals(ref, e.showRef)
    }

    @Test
    fun moviesHaveASingleEpisode() = runTest {
        val episodes = client().episodes(ShowRef(SourceId.ZDF, "bloss-nicht-liebe-movie-100"))
        assertEquals(1, episodes.size)
        assertEquals(5298, episodes.single().durationSeconds)
    }

    @Test
    fun resolvePrefersMp4AndFindsWebVtt() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val stream = client(seen).resolve(EpisodeRef(SourceId.ZDF, "symptome-104"))
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(1080, stream.height)
        assertTrue(stream.url.startsWith("https://nrodlzdf-a.akamaihd.net/") && stream.url.contains(".mp4"), stream.url)
        assertEquals(SubtitleFormat.WEBVTT, stream.subtitles.single().format)
        assertTrue(stream.subtitles.single().url.endsWith(".vtt"))
        assertEquals(listOf("main", "ad"), stream.audioTracks)
        assertTrue(seen.any { it.url.toString() == "https://api.zdf.de/tmd/2/ngplayer_2_5/vod/ptmd/mediathek/261125_2145_sendung_krb/4" })
    }

    @Test
    fun resolveListsMp4AlternativesBestFirst() = runTest {
        val stream = client().resolve(EpisodeRef(SourceId.ZDF, "symptome-104"))
        assertEquals(listOf(1080, 720, 540, 360, 270), stream.alternatives.map { it.height })
        assertTrue(stream.alternatives.all { it.mimeType == "video/mp4" })
        assertEquals(stream.url, stream.alternatives.first().url)
        assertEquals(stream.alternatives.size, stream.alternatives.map { it.url }.distinct().size)
    }

    @Test
    fun refetchesTokenOnceOn403() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        var graphQlCalls = 0
        val c = client(seen) { r ->
            if (r.url.toString() == "https://api.zdf.de/graphql" && graphQlCalls++ == 0) {
                Reply("denied", HttpStatusCode.Forbidden)
            } else {
                null
            }
        }
        assertTrue(c.startPageRows().isNotEmpty())
        assertEquals(2, seen.count { it.url.toString() == "https://www.zdf.de/" })
        assertEquals(2, graphQlCalls)
    }

    @Test
    fun givesUpAfterSecondFailure() = runTest {
        val c = client { r -> if (r.url.toString() == "https://api.zdf.de/graphql") Reply("no", HttpStatusCode.Unauthorized) else null }
        assertTrue(runCatching { c.startPageRows() }.exceptionOrNull() is SourceException)
    }

    @Test
    fun cachesTokenBetweenCalls() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val c = client(seen)
        c.startPageRows()
        c.startPageRows()
        assertEquals(1, seen.count { it.url.toString() == "https://www.zdf.de/" })
    }
}
