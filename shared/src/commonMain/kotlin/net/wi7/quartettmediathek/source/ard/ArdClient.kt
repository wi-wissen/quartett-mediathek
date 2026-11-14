package net.wi7.quartettmediathek.source.ard

import io.ktor.client.HttpClient
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.SubtitleFormat
import net.wi7.quartettmediathek.model.SubtitleTrack
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
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

/** A show page: show metadata plus its episodes, newest first. */
data class ArdGrouping(val show: Show, val episodes: List<Episode>)

/** ARD page-gateway client (SPEC 2.2). */
class ArdClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://api.ardmediathek.de/page-gateway",
    private val imageWidth: Int = 960,
) : RecommendationSource, ShowSource, StreamResolver {

    override suspend fun startPageRows(): List<SourceRow> {
        val home = parseJson(http.fetchText("$baseUrl/pages/ard/home?embedded=false"), "ARD home")
        val widgets = home["widgets"].list.filter(::isUsableWidget)
        val gate = Semaphore(6)
        return coroutineScope {
            widgets.map { w ->
                async {
                    val teasers = w["teasers"].list.ifEmpty {
                        // A failing row must not take down the whole start page.
                        gate.withPermit { runCatching { widgetTeasers(w["id"].string.orEmpty()) }.getOrDefault(emptyList()) }
                    }
                    val items = teasers.mapNotNull(::mapTeaser)
                    if (items.isEmpty()) null else SourceRow(Broadcaster.ARD, w["title"].string.orEmpty(), items)
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Teasers of one start page row. */
    suspend fun widgetTeasers(widgetId: String): List<JsonElement> {
        val url = "$baseUrl/widgets/ard/editorials/${widgetId.replace(":", "%3A")}?pageNumber=0&pageSize=100"
        return parseJson(http.fetchText(url), "ARD widget")["teasers"].list
    }

    override suspend fun show(ref: ShowRef): Show = grouping(ref).show

    override suspend fun episodes(ref: ShowRef): List<Episode> = grouping(ref).episodes

    suspend fun grouping(ref: ShowRef): ArdGrouping {
        val root = parseJson(http.fetchText("$baseUrl/pages/ard/grouping/${ref.key}?embedded=true"), "ARD grouping")
        val title = root["title"].nonBlank ?: throw SourceException("ARD grouping ${ref.key} has no title")
        val episodes = root["widgets"].list
            .flatMap { it["teasers"].list }
            .filter { it["type"].string == "ondemand" }
            .mapNotNull { mapEpisode(it, ref) }
            .sortedByDescending { it.publishedAt }
        val show = Show(
            ref = ref,
            title = title,
            broadcaster = Broadcaster.ARD,
            channelLabel = root.path("publicationService", "name").nonBlank ?: "ARD",
            description = root["synopsis"].nonBlank,
            imageUrl = image(root["images"]["aspect16x9"]["src"].string ?: root["image"]["src"].string),
            episodeCount = episodes.size,
        )
        return ArdGrouping(show, episodes)
    }

    /** 16:9 picture of an item page (`widgets[].image.src`), or null. */
    suspend fun itemImage(itemId: String): String? {
        val root = parseJson(http.fetchText("$baseUrl/pages/ard/item/$itemId?devicetype=pc&embedded=true"), "ARD item")
        return parseItemImage(root)
    }

    internal fun parseItemImage(root: JsonElement): String? =
        root["widgets"].list.firstNotNullOfOrNull { it["image"]["src"].nonBlank }?.let { image(it) }

    override suspend fun resolve(ref: EpisodeRef): StreamInfo {
        val root = parseJson(http.fetchText("$baseUrl/pages/ard/item/${ref.key}?devicetype=pc&embedded=true"), "ARD item")
        val player = root["widgets"].list.firstOrNull { it["mediaCollection"] != null }
            ?: throw SourceException("ARD item ${ref.key} has no player")
        if (player["blockedByLoginOnly"].bool == true) throw SourceException("ARD item ${ref.key} requires a login")
        if (player["blockedByFsk"].bool == true) throw SourceException("ARD item ${ref.key} is blocked by age rating")
        return parseStream(player["mediaCollection"]["embedded"], ref)
    }

    internal fun parseStream(embedded: JsonElement?, ref: EpisodeRef): StreamInfo {
        val media = embedded["streams"].list.flatMap { it["media"].list }
        if (media.isEmpty()) {
            val geo = embedded["isGeoBlocked"].bool == true
            throw SourceException("ARD item ${ref.key} has no streams" + if (geo) " (geoblocked)" else "")
        }
        fun kinds(m: JsonElement) = m["audios"].list.mapNotNull { it["kind"].string }
        // The list mixes audio description, clear speech and standard sound: prefer the standard one.
        val standard = media.filter { m -> kinds(m).let { it.isEmpty() || "standard" in it } }.ifEmpty { media }
        fun height(m: JsonElement) = m["maxVResolutionPx"].int ?: 0
        val mp4 = standard.filter { it["mimeType"].string == "video/mp4" }.maxByOrNull(::height)
        val hls = standard.filter { it["mimeType"].string?.contains("mpegurl", ignoreCase = true) == true }.maxByOrNull(::height)
        val best = mp4 ?: hls ?: standard.first()
        return StreamInfo(
            url = best["url"].string ?: throw SourceException("ARD stream without url"),
            mimeType = best["mimeType"].string ?: "video/mp4",
            height = best["maxVResolutionPx"].int,
            subtitles = parseSubtitles(embedded["subtitles"]),
            audioTracks = media.flatMap(::kinds).distinct(),
        )
    }

    private fun parseSubtitles(subtitles: JsonElement?): List<SubtitleTrack> = subtitles.list.mapNotNull { sub ->
        val sources = sub["sources"].list
        val webvtt = sources.firstOrNull { it["kind"].string == "webvtt" }
        val chosen = webvtt ?: sources.firstOrNull { it["kind"].string == "ebutt" } ?: return@mapNotNull null
        val url = chosen["url"].string ?: return@mapNotNull null
        val format = if (webvtt != null) SubtitleFormat.WEBVTT else SubtitleFormat.EBU_TT
        SubtitleTrack(url, format, sub["languageCode"].string)
    }

    private fun isUsableWidget(w: JsonElement): Boolean {
        val type = w["compilationType"].string
        val title = w["title"].string
        return type != "continuousPlay" && type != "persoRecos" && !title.isNullOrBlank() && title != "Menü" &&
            w["id"].string != null
    }

    internal fun mapTeaser(t: JsonElement): Teaser? = when (t["type"].string) {
        "show" -> mapShow(t)?.let { Teaser.OfShow(it) }
        "ondemand" -> mapEpisode(t, null)?.let { Teaser.OfEpisode(it) }
        else -> null
    }

    private fun mapShow(t: JsonElement): Show? {
        val id = t.path("links", "target", "id").string ?: return null
        val raw = t["mediumTitle"].nonBlank ?: t["longTitle"].nonBlank ?: return null
        return Show(
            ref = ShowRef(SourceId.ARD, id),
            title = cleanArdShowTitle(raw),
            broadcaster = Broadcaster.ARD,
            channelLabel = t.path("publicationService", "name").nonBlank ?: "ARD",
            imageUrl = image(t["images"]["aspect16x9"]["src"].string),
        )
    }

    private fun mapEpisode(t: JsonElement, show: ShowRef?): Episode? {
        val id = t.path("links", "target", "id").string ?: t["id"].string ?: return null
        val title = t["mediumTitle"].nonBlank ?: t["longTitle"].nonBlank ?: return null
        val showId = show ?: t["show"]["self"]["id"].string?.let { ShowRef(SourceId.ARD, it) }
        val features = t["binaryFeatures"].list.mapNotNull { it.string }
        val variants = buildSet {
            if ("audiodeskription" in title.lowercase()) add(Variant.AUDIO_DESCRIPTION) else add(Variant.STANDARD)
            if ("AD" in features) add(Variant.AUDIO_DESCRIPTION)
            if ("DGS" in features) add(Variant.SIGN_LANGUAGE)
            if ("OV" in features) add(Variant.ORIGINAL_VERSION)
        }
        return Episode(
            ref = EpisodeRef(SourceId.ARD, id),
            showRef = showId,
            showTitle = t["show"]["title"].nonBlank?.let(::cleanArdShowTitle) ?: "",
            title = title,
            broadcaster = Broadcaster.ARD,
            channelLabel = t.path("publicationService", "name").nonBlank ?: "ARD",
            description = t["synopsis"].nonBlank,
            imageUrl = image(t["images"]["aspect16x9"]["src"].string),
            durationSeconds = t["duration"].int ?: 0,
            publishedAt = parseInstantOrNull(t["broadcastedOn"].string) ?: Instant.fromEpochSeconds(0),
            availableUntil = parseInstantOrNull(t["availableTo"].string),
            hasSubtitles = t["subtitled"].bool == true || "UT" in features,
            variants = variants,
        )
    }

    private fun image(template: String?): String? = template?.replace("{width}", imageWidth.toString())
}

/**
 * Cleans a raw ARD show name for display and for MVW queries: cuts the suffix after `|` or ` · `
 * ("Die Friesenjunkies | Impro-Dramedy-Serie" -> "Die Friesenjunkies") and trailing season markers.
 */
fun cleanArdShowTitle(raw: String): String {
    var s = raw.substringBefore('|').substringBefore(" · ").trim()
    s = s.replace(Regex("""\s*[-–:]?\s*\(?\s*Staffel\s*\d+\s*\)?\s*$""", RegexOption.IGNORE_CASE), "").trim()
    return s.ifEmpty { raw.trim() }
}
