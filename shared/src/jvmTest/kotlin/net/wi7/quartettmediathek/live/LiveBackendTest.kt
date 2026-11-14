package net.wi7.quartettmediathek.live

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.wi7.quartettmediathek.data.QuartettGraph
import net.wi7.quartettmediathek.feed.TitleParser
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.platform.createPlatformHttpClient
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end tests of the whole data layer against the LIVE broadcaster APIs and MediathekViewWeb, following what a
 * user does in the app. "Restart" = a new [QuartettGraph] on the same database file, as when Android TV kills the app.
 *
 * Skipped unless enabled:  ./gradlew :shared:jvmTest -Plive=1 --tests '*live*'
 * Also runs daily on GitHub (workflow live-tests.yml), because the APIs can change at any time.
 */
class LiveBackendTest {

    // ---- the user flows ----

    @Test
    fun startPageHasAllFourBroadcasters() = live { app ->
        val counts = app.discover().flatMap { it.items }.groupingBy { it.broadcaster() }.eachCount()
        log("discover per broadcaster: $counts")
        for (b in Broadcaster.entries) assertTrue((counts[b] ?: 0) > 0, "no Discover teasers from $b")
    }

    @Test
    fun showPagesOpenAndPlayAfterRestart() = live { app ->
        val shows = app.discover().flatMap { it.items }.filterIsInstance<Teaser.OfShow>().map { it.show }
        for (source in listOf(SourceId.ARD, SourceId.ZDF, SourceId.MVW)) {
            val show = shows.firstOrNull { it.ref.source == source } ?: fail("no show from $source on the start page")
            val episodes = app.graph.catalog.episodes(show.ref)
            log("show $source '${show.title}': ${episodes.size} episodes")
            assertTrue(episodes.isNotEmpty(), "show '${show.title}' ($source) has no episodes")
            assertStandardFirst(episodes, "show '${show.title}'")
            app.restart().openAndPlay(episodes.take(2), "show '${show.title}'")
        }
    }

    @Test
    fun arteEpisodeOpensAndPlaysAfterRestart() = live { app ->
        val episode = app.discover().flatMap { it.items }.filterIsInstance<Teaser.OfEpisode>()
            .map { it.episode }.firstOrNull { it.ref.source == SourceId.ARTE } ?: fail("no arte episode on the start page")
        app.restart().openAndPlay(listOf(episode), "arte start page")
    }

    @Test
    fun searchResultsOpenAndPlayAfterRestart() = live { app ->
        for (query in listOf("die anstalt", "nano", "tatort", "ringe der macht")) {
            val results = app.graph.catalog.search(query)
            val episodes = results.filterIsInstance<Teaser.OfEpisode>().map { it.episode }
            val shows = results.filterIsInstance<Teaser.OfShow>().map { it.show }
            log("search '$query': ${shows.size} shows, ${episodes.size} episodes, ${episodes.count { it.imageUrl != null }} with image")
            assertTrue(episodes.isNotEmpty(), "search '$query' found no episodes")
            assertStandardFirst(episodes, "search '$query'")
            app.restart().openAndPlay(episodes.take(3), "search '$query'")
            val show = shows.first()
            assertTrue(app.graph.catalog.episodes(show.ref).isNotEmpty(), "search show '${show.title}' has no episodes")
        }
    }

    @Test
    fun versionsOfAnEpisodeResolveToStreamsAfterRestart() = live { app ->
        val first = app.graph.catalog.search("ringe der macht").filterIsInstance<Teaser.OfEpisode>().first().episode
        log("versions of '${first.title}': ${first.versionRefs}")
        assertTrue(Variant.ORIGINAL_VERSION in first.versionRefs, "no original version ref for '${first.title}': ${first.versionRefs}")
        assertTrue(Variant.AUDIO_DESCRIPTION in first.versionRefs, "no audio description ref for '${first.title}': ${first.versionRefs}")
        val restarted = app.restart()
        for (variant in listOf(Variant.STANDARD, Variant.ORIGINAL_VERSION, Variant.AUDIO_DESCRIPTION)) {
            val ref = first.versionRefs[variant] ?: fail("no ref for $variant")
            val stream = runCatching { restarted.graph.playback.stream(ref, variant) }
                .getOrElse { fail("version $variant ($ref) has no stream after restart: ${it.message}") }
            assertPlayable(stream, "version $variant of '${first.title}'")
            log("ok version $variant of '${first.title}': $ref -> ${stream.mimeType} ${stream.height ?: ""}p")
        }
    }

    @Test
    fun subscriptionFeedOpensAndPlaysAfterRestart() = live { searchApp ->
        val show = searchApp.graph.catalog.search("die anstalt").filterIsInstance<Teaser.OfShow>().first().show
        // Fresh database: nothing from the search may help, like a feed loaded days later.
        val app = App(File.createTempFile("quartett-feed", ".db").also { it.delete(); it.deleteOnExit() })
        app.graph.library.subscribe(show)
        val feed = withTimeout(120_000) { app.graph.library.feed().first { it.isNotEmpty() } }.map { it.episode }
        log("feed '${show.title}': ${feed.size} entries, ${feed.count { it.imageUrl != null }} with image")
        assertStandardFirst(feed, "feed '${show.title}'")
        assertTrue(feed.count { it.imageUrl != null } * 10 >= feed.take(40).size * 8, "fewer than 80 % of feed episodes have an image")
        val restarted = app.restart()
        restarted.openAndPlay(feed.take(3), "feed '${show.title}'")
        // ZDF episodes found via MediathekViewWeb must play in the broadcaster's best quality, not MVW's 720p.
        val height = restarted.graph.playback.stream(feed.first().ref).height ?: 0
        log("feed '${show.title}' first stream: ${height}p")
        assertTrue(height >= 1080, "feed '${show.title}' plays only ${height}p")
    }

    @Test
    fun progressAndHistorySurviveRestart() = live { app ->
        val episode = app.graph.catalog.search("nano").filterIsInstance<Teaser.OfEpisode>().first().episode
        app.graph.library.saveProgress(episode, 600)
        val after = app.restart()
        val history = after.graph.library.history().first()
        assertTrue(history.any { it.episode.ref == episode.ref && it.positionSeconds == 600L }, "progress lost after restart")
        after.openAndPlay(listOf(history.first().episode), "history")
    }

    // ---- helpers ----

    private inner class App(val dbFile: File) {
        val graph = QuartettGraph(DatabaseDriverFactory("jdbc:sqlite:${dbFile.absolutePath}"))

        suspend fun discover() = run {
            withTimeout(180_000) { graph.catalog.refresh(force = false) }
            graph.catalog.discoverRows().first { it.isNotEmpty() }
        }

        fun restart() = App(dbFile)

        suspend fun openAndPlay(episodes: List<Episode>, where: String) {
            assertTrue(episodes.isNotEmpty(), "$where: nothing to open")
            for (e in episodes) {
                val opened = runCatching { graph.catalog.episode(e.ref) }
                    .getOrElse { fail("$where: episode '${e.title}' (${e.ref}) does not open after restart: ${it.message}") }
                if (opened.ref.source == SourceId.MVW && Variant.STANDARD in opened.variants) {
                    val raw = graph.rawMvwTitle(opened.ref) ?: fail("$where: raw MVW hit for '${e.title}' missing")
                    val variant = TitleParser.parse(raw).variant
                    assertTrue(variant == Variant.STANDARD, "$where: '${e.title}' would play the $variant file ('$raw') although a standard version exists")
                }
                val stream = runCatching { graph.playback.stream(opened.ref) }
                    .getOrElse { fail("$where: no stream for '${e.title}' (${e.ref}): ${it.message}") }
                assertPlayable(stream, "$where '${e.title}'")
                log("ok $where: '${e.title}' ${e.ref.source} -> ${stream.mimeType} ${stream.height ?: ""}p")
            }
        }
    }

    private fun live(block: suspend (App) -> Unit) = runBlocking {
        assumeTrue("live tests disabled (use -Plive=1)", System.getProperty("qm.live") == "1" || System.getenv("QM_LIVE") == "1")
        val db = File.createTempFile("quartett-live", ".db").also { it.delete(); it.deleteOnExit() }
        withTimeout(600_000) { block(App(db)) }
    }

    private fun Teaser.broadcaster() = when (this) {
        is Teaser.OfShow -> show.broadcaster
        is Teaser.OfEpisode -> episode.broadcaster
    }

    /** Lists must not offer the audio-description or sign-language file when the standard one exists. */
    private fun assertStandardFirst(episodes: List<Episode>, where: String) {
        val byTitle = episodes.groupBy { e ->
            val p = TitleParser.parse(e.title)
            "${e.showTitle}|${p.title}|${p.season ?: e.season}|${p.episode ?: e.episodeNumber}".lowercase()
        }
        val offenders = byTitle.values.filter { group ->
            group.size > 1 || (group.single().let { TitleParser.parse(it.title).variant != Variant.STANDARD && Variant.STANDARD in it.variants })
        }
        if (offenders.isNotEmpty()) fail("$where lists variants separately or picks a non-standard one: " +
            offenders.joinToString { g -> g.joinToString(" / ") { "'${it.title}'" } })
    }

    private suspend fun assertPlayable(stream: StreamInfo, where: String) {
        val isHls = stream.mimeType.contains("mpegurl", ignoreCase = true)
        // Manifests are small and fetched whole; for video files only the first bytes are requested.
        val response: HttpResponse = http.get(stream.url) { if (!isHls) header("Range", "bytes=0-2047") }
        val status = response.status.value
        if (status == 403 && "/dach/" in stream.url) {
            // Geo-restricted to Germany/Austria/Switzerland; CI and this container are elsewhere. Resolution was checked.
            log("geoblocked here (DACH only), not checked: $where")
            return
        }
        assertTrue(status == 200 || status == 206, "$where: stream answers HTTP $status (${stream.url})")
        if (isHls) {
            assertTrue(response.bodyAsText().trimStart('\uFEFF', ' ', '\n', '\r').startsWith("#EXTM3U"), "$where: HLS manifest does not start with #EXTM3U")
        } else {
            val type = response.contentType()?.toString().orEmpty()
            assertTrue(type.startsWith("video/") || type.contains("octet-stream"), "$where: stream content type is '$type'")
        }
    }

    private companion object {
        val http: HttpClient by lazy { createPlatformHttpClient() }
        fun log(message: String) = println("LIVE $message")
    }
}
