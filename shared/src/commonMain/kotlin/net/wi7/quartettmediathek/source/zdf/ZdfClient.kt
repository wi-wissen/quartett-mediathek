package net.wi7.quartettmediathek.source.zdf

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.StreamVariant
import net.wi7.quartettmediathek.model.distinctByHeightDesc
import net.wi7.quartettmediathek.model.SubtitleFormat
import net.wi7.quartettmediathek.model.SubtitleTrack
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.network.HttpStatusException
import net.wi7.quartettmediathek.network.bool
import net.wi7.quartettmediathek.network.fetchText
import net.wi7.quartettmediathek.network.get
import net.wi7.quartettmediathek.network.int
import net.wi7.quartettmediathek.network.list
import net.wi7.quartettmediathek.network.nonBlank
import net.wi7.quartettmediathek.network.parseInstantOrNull
import net.wi7.quartettmediathek.network.parseJson
import net.wi7.quartettmediathek.network.path
import net.wi7.quartettmediathek.network.string
import net.wi7.quartettmediathek.source.RecommendationSource
import net.wi7.quartettmediathek.source.ShowSource
import net.wi7.quartettmediathek.source.SourceException
import net.wi7.quartettmediathek.source.StreamResolver

/** API token and app id read from the zdf.de HTML. */
data class ZdfCredentials(val apiToken: String, val appId: String)

/** Extracts the credentials from the zdf.de start page HTML. Never hard-code them: they rotate. */
fun parseZdfCredentials(html: String): ZdfCredentials {
    val token = Regex("""apiToken\\?":\\?"([^"\\]+)""").find(html)?.groupValues?.get(1)
        ?: throw SourceException("zdf.de: no apiToken found")
    val appId = Regex("""zdf-web-[0-9a-f]+""").find(html)?.value
        ?: throw SourceException("zdf.de: no app id found")
    return ZdfCredentials(token, appId)
}

/** ZDF GraphQL client (SPEC 2.3). Credentials are cached in memory and refreshed once on 401/403. */
class ZdfClient(
    private val http: HttpClient,
    private val homeUrl: String = "https://www.zdf.de/",
    private val graphQlUrl: String = "https://api.zdf.de/graphql",
    private val apiBase: String = "https://api.zdf.de",
    private val maxSeasons: Int = 4,
) : RecommendationSource, ShowSource, StreamResolver {

    private val lock = Mutex()
    private var cached: ZdfCredentials? = null

    private suspend fun credentials(refresh: Boolean): ZdfCredentials = lock.withLock {
        if (!refresh) cached?.let { return it }
        parseZdfCredentials(http.fetchText(homeUrl)).also { cached = it }
    }

    /** Runs [call] with credentials; on 401/403 fetches fresh credentials and retries exactly once. */
    private suspend fun <T> authed(call: suspend (ZdfCredentials) -> T): T {
        val first = credentials(refresh = false)
        return try {
            call(first)
        } catch (e: SourceException) {
            val status = (e.cause as? HttpStatusException)?.status
            if (status != 401 && status != 403) throw e
            call(credentials(refresh = true))
        }
    }

    private suspend fun graphQl(query: String, variables: Map<String, JsonElement> = emptyMap()): JsonElement {
        val body = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject { variables.forEach { (k, v) -> put(k, v) } })
        }.toString()
        val text = authed { c ->
            http.fetchText(graphQlUrl) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                header("Api-Auth", "Bearer ${c.apiToken}")
                header("Zdf-App-Id", c.appId)
                setBody(body)
            }
        }
        val root = parseJson(text, "ZDF GraphQL")
        val data = root["data"]
        if (data == null || data is JsonNull) {
            throw SourceException("ZDF GraphQL error: ${root["errors"][0]["message"].string ?: "no data"}")
        }
        return data
    }

    override suspend fun startPageRows(): List<SourceRow> {
        val data = graphQl(ZdfQueries.START_PAGE)
        return data.path("curatedCollectionByCanonical", "modules", "nodes").list.mapNotNull { module ->
            val title = module["title"].nonBlank ?: return@mapNotNull null
            val items = module["items"]["nodes"].list.mapNotNull(::mapTeaser)
            if (items.isEmpty()) null else SourceRow(Broadcaster.ZDF, title, items)
        }
    }

    override suspend fun show(ref: ShowRef): Show = loadShow(ref).first

    override suspend fun episodes(ref: ShowRef): List<Episode> = loadShow(ref).second

    /** Show metadata plus episodes (newest first) of up to [maxSeasons] seasons. */
    suspend fun loadShow(ref: ShowRef): Pair<Show, List<Episode>> {
        suspend fun page(season: Int, offset: Int): JsonElement =
            graphQl(
                ZdfQueries.SHOW,
                mapOf("c" to JsonPrimitive(ref.key), "i" to JsonPrimitive(season), "e" to JsonPrimitive(offset)),
            )["smartCollectionByCanonical"]
                ?.takeIf { it !is JsonNull }
                ?: throw SourceException("ZDF show ${ref.key} not found")

        val first = page(0, 0)
        val episodes = mutableListOf<Episode>()
        val movie = first["video"]
        if (movie != null && movie !is JsonNull) mapVideo(movie)?.let { episodes += it }

        val total = first["seasons"]["totalCount"].int ?: 0
        for (i in 0 until minOf(total, maxSeasons)) {
            var node = if (i == 0) first else page(i, 0)
            var season = node["seasons"]["nodes"][0]
            val count = season["countEpisodes"].int ?: 0
            var loaded = 0
            while (true) {
                val batch = season["episodes"]["nodes"].list
                episodes += batch.mapNotNull(::mapVideo)
                loaded += batch.size
                if (batch.isEmpty() || loaded >= count) break
                node = page(i, loaded)
                season = node["seasons"]["nodes"][0]
            }
        }
        val show = mapShow(first, ref).copy(episodeCount = episodes.size.takeIf { it > 0 })
        val sorted = episodes.sortedWith(
            compareByDescending<Episode> { it.publishedAt }
                .thenByDescending { it.season ?: 0 }
                .thenByDescending { it.episodeNumber ?: 0 },
        )
        return show to sorted
    }

    /** A single episode by its canonical, e.g. after an app restart when no show page was loaded. */
    suspend fun episode(ref: EpisodeRef): Episode {
        val video = graphQl(ZdfQueries.VIDEO_BY_CANONICAL, mapOf("c" to JsonPrimitive(ref.key)))["videoByCanonical"]
        return mapVideo(video ?: throw SourceException("ZDF episode ${ref.key} not found"))
            ?: throw SourceException("ZDF episode ${ref.key} not found")
    }

    /** Teaser picture (768x432) of a video by its canonical, or null when it has none. */
    suspend fun videoImage(canonical: String): String? {
        val video = graphQl(ZdfQueries.VIDEO_IMAGE, mapOf("c" to JsonPrimitive(canonical)))["videoByCanonical"]
        return image(video["teaser"])
    }

    override suspend fun resolve(ref: EpisodeRef): StreamInfo {
        val video = graphQl(ZdfQueries.VIDEO_BY_CANONICAL, mapOf("c" to JsonPrimitive(ref.key)))["videoByCanonical"]
        val template = video["currentMedia"]["nodes"].list.firstNotNullOfOrNull { it["ptmdTemplate"].nonBlank }
            ?: throw SourceException("ZDF episode ${ref.key} has no ptmd template")
        val path = template.replace("{playerId}", PLAYER_ID)
        val text = authed { c ->
            http.fetchText(if (path.startsWith("http")) path else apiBase + path) {
                header("Api-Auth", "Bearer ${c.apiToken}")
                header("Zdf-App-Id", c.appId)
            }
        }
        return parsePtmd(parseJson(text, "ZDF ptmd"), ref)
    }

    internal fun parsePtmd(root: JsonElement, ref: EpisodeRef): StreamInfo {
        class Candidate(val mime: String, val height: Int, val url: String)
        val candidates = root["priorityList"].list.flatMap { it["formitaeten"].list }.flatMap { f ->
            val mime = f["mimeType"].string ?: return@flatMap emptyList()
            f["qualities"].list.mapNotNull { q ->
                val tracks = q["audio"]["tracks"].list
                val main = tracks.firstOrNull { it["class"].string == "main" } ?: tracks.firstOrNull()
                val uri = main["uri"].nonBlank ?: return@mapNotNull null
                Candidate(mime, q["highestVerticalResolution"].int ?: 0, uri)
            }
        }
        if (candidates.isEmpty()) throw SourceException("ZDF episode ${ref.key} has no streams")
        // Prefer plain MP4, then HLS; WebM (VP9) last. Within a format the highest resolution wins.
        fun rank(c: Candidate) = when {
            c.mime == "video/mp4" -> 2
            c.mime.contains("mpegurl", ignoreCase = true) -> 1
            else -> 0
        }
        val best = candidates.sortedWith(compareByDescending<Candidate> { rank(it) }.thenByDescending { it.height }).first()
        val audio = root["priorityList"].list.flatMap { it["formitaeten"].list }
            .flatMap { it["qualities"].list }.flatMap { it["audio"]["tracks"].list }
            .mapNotNull { it["class"].string }.distinct()
        return StreamInfo(
            url = best.url,
            mimeType = best.mime,
            height = best.height.takeIf { it > 0 },
            subtitles = parseCaptions(root["captions"]),
            audioTracks = audio,
            // Only progressive MP4 can be swapped by URL; HLS and WebM stay on the automatic choice.
            alternatives = if (best.mime == "video/mp4") {
                candidates.filter { it.mime == "video/mp4" }.map { StreamVariant(it.height, it.url, it.mime) }
                    .distinctByHeightDesc()
            } else emptyList(),
        )
    }

    private fun parseCaptions(captions: JsonElement?): List<SubtitleTrack> {
        val all = captions.list.mapNotNull { c ->
            val uri = c["uri"].nonBlank ?: return@mapNotNull null
            val format = when {
                c["format"].string == "webvtt" -> SubtitleFormat.WEBVTT
                c["format"].string?.startsWith("ebu-tt") == true -> SubtitleFormat.EBU_TT
                else -> return@mapNotNull null
            }
            Triple(c["language"].string, format, uri)
        }
        // One track per language; WebVTT beats EBU-TT.
        return all.groupBy { it.first }.map { (lang, list) ->
            val pick = list.firstOrNull { it.second == SubtitleFormat.WEBVTT } ?: list.first()
            SubtitleTrack(pick.third, pick.second, lang)
        }
    }

    private fun mapTeaser(item: JsonElement): Teaser? {
        val type = item["__typename"].string ?: return null
        return when {
            type == "Video" -> mapVideo(item)?.let { Teaser.OfEpisode(it) }
            type.endsWith("SmartCollection") -> mapShow(item, null).takeIf { item["canonical"].string != null }
                ?.let { Teaser.OfShow(it) }
            else -> null // MetaCollection, ExternalLink, ZappingChannel, Catalog, ...
        }
    }

    private fun mapShow(node: JsonElement, ref: ShowRef?): Show {
        val canonical = ref?.key ?: node["canonical"].string.orEmpty()
        val owner = node.path("contentOwner", "title").nonBlank
        return Show(
            ref = ref ?: ShowRef(SourceId.ZDF, canonical),
            title = node["title"].nonBlank ?: node["teaser"]["title"].nonBlank ?: canonical,
            broadcaster = broadcasterOf(owner),
            channelLabel = owner ?: "ZDF",
            description = node["teaser"]["description"].nonBlank,
            imageUrl = image(node["teaser"]),
        )
    }

    private fun mapVideo(v: JsonElement): Episode? {
        val canonical = v["canonical"].nonBlank ?: return null
        val title = v["title"].nonBlank ?: v["teaser"]["title"].nonBlank ?: return null
        val collection = v["smartCollection"]
        val vod = v["currentMedia"]["nodes"].list.firstOrNull { it["duration"] != null }
        val variants = buildSet {
            add(Variant.STANDARD)
            if (v["teaser"]["enableAudioDescription"].bool == true) add(Variant.AUDIO_DESCRIPTION)
        }
        return Episode(
            ref = EpisodeRef(SourceId.ZDF, canonical),
            showRef = collection["canonical"].nonBlank?.let { ShowRef(SourceId.ZDF, it) },
            showTitle = collection["title"].nonBlank ?: "",
            title = title,
            broadcaster = Broadcaster.ZDF,
            channelLabel = "ZDF",
            description = v["teaser"]["description"].nonBlank,
            imageUrl = image(v["teaser"]),
            durationSeconds = vod["duration"].int ?: 0,
            publishedAt = parseInstantOrNull(v["editorialDate"].string) ?: Instant.fromEpochSeconds(0),
            season = v["episodeInfo"]["seasonNumber"].int,
            episodeNumber = v["episodeInfo"]["episodeNumber"].int,
            hasSubtitles = v["teaser"]["enableSubtitle"].bool == true,
            variants = variants,
        )
    }

    private fun image(teaser: JsonElement?): String? = teaser.path("imageWithoutLogo", "layouts", "dim768X432").nonBlank

    private fun broadcasterOf(owner: String?) =
        if (owner.equals("3sat", ignoreCase = true)) Broadcaster.THREESAT else Broadcaster.ZDF

    private companion object {
        const val PLAYER_ID = "ngplayer_2_5"
    }
}
