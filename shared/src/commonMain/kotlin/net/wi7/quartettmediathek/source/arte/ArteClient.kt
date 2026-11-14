package net.wi7.quartettmediathek.source.arte

import io.ktor.client.HttpClient
import kotlin.time.Instant
import kotlinx.serialization.json.JsonElement
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Teaser
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
import net.wi7.quartettmediathek.source.SourceException
import net.wi7.quartettmediathek.source.StreamResolver

/** Metadata of one programme from the arte player config. */
data class ArteProgram(
    val programId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val imageUrl: String?,
    val durationSeconds: Int?,
    val websiteUrl: String?,
)

/** arte EMAC + player client (SPEC 2.4). */
class ArteClient(
    private val http: HttpClient,
    private val language: String = "de",
    private val emacBase: String = "https://api.arte.tv/api/emac/v4",
    private val playerBase: String = "https://api.arte.tv/api/player/v2",
    private val imageSize: String = "940x530",
) : RecommendationSource, StreamResolver {

    override suspend fun startPageRows(): List<SourceRow> {
        val root = parseJson(http.fetchText("$emacBase/$language/web/pages/HOME/"), "arte HOME")
        return root["zones"].list.mapNotNull { zone ->
            val title = zone["title"].nonBlank ?: return@mapNotNull null
            val items = zone["content"]["data"].list.mapNotNull(::mapTeaser)
            if (items.isEmpty()) null else SourceRow(Broadcaster.ARTE, title, items)
        }
    }

    override suspend fun resolve(ref: EpisodeRef): StreamInfo {
        val root = parseJson(http.fetchText("$playerBase/config/$language/${ref.key}"), "arte player config")
        return parseStream(root, ref)
    }

    /** Title, description, image and duration of a programme. */
    suspend fun program(programId: String): ArteProgram {
        val root = parseJson(http.fetchText("$playerBase/config/$language/$programId"), "arte player config")
        return parseProgram(root, programId)
    }

    /** The programme as [Episode]. The player config has no broadcast date, so `publishedAt` is the epoch. */
    suspend fun episode(ref: EpisodeRef): Episode {
        val p = program(ref.key)
        return Episode(
            ref = ref,
            showRef = null,
            showTitle = p.title,
            title = p.subtitle ?: p.title,
            broadcaster = Broadcaster.ARTE,
            channelLabel = "arte",
            description = p.description,
            imageUrl = p.imageUrl,
            durationSeconds = p.durationSeconds ?: 0,
            publishedAt = Instant.fromEpochSeconds(0),
            websiteUrl = p.websiteUrl,
        )
    }

    internal fun parseProgram(root: JsonElement, programId: String): ArteProgram {
        val meta = root.path("data", "attributes", "metadata")
            ?: throw SourceException("arte $programId: no metadata")
        return ArteProgram(
            programId = meta["providerId"].nonBlank ?: programId,
            title = meta["title"].nonBlank ?: throw SourceException("arte $programId: no title"),
            subtitle = meta["subtitle"].nonBlank,
            description = meta["description"].nonBlank,
            imageUrl = meta["images"][0]["url"].nonBlank,
            durationSeconds = meta["duration"]["seconds"].int,
            websiteUrl = meta["link"]["url"].nonBlank,
        )
    }

    internal fun parseStream(root: JsonElement, ref: EpisodeRef): StreamInfo {
        val attributes = root.path("data", "attributes")
        val streams = attributes["streams"].list.filter {
            it["protocol"].string?.startsWith("API_HLS") == true && it["url"].nonBlank != null
        }
        if (streams.isEmpty()) {
            val reason = attributes["error"]["title"].string ?: attributes["restriction"]["code"].string
            throw SourceException("arte ${ref.key} has no HLS stream" + (reason?.let { " ($it)" } ?: ""))
        }
        val wanted = language.uppercase()
        val stream = streams.firstOrNull { s -> s["versions"].list.any { it["shortLabel"].string == wanted } } ?: streams.first()
        return StreamInfo(
            url = stream["url"].string!!,
            mimeType = "application/x-mpegURL",
            height = null,
            audioTracks = stream["versions"].list.mapNotNull { it["shortLabel"].string },
        )
    }

    private fun mapTeaser(t: JsonElement): Teaser? {
        if (t["type"].string != "teaser") return null
        val programId = t["programId"].nonBlank ?: return null
        val kind = t["kind"]["code"].string
        if (kind == "EXTERNAL") return null
        if (t.path("availability", "hasVideoStreams").bool == false) return null
        val title = t["title"].nonBlank ?: return null
        val image = t["mainImage"]["url"].string?.replace("__SIZE__", imageSize)
        val description = t["shortDescription"].nonBlank ?: t["teaserText"].nonBlank
        if (t["kind"]["isCollection"].bool == true) {
            return Teaser.OfShow(
                Show(
                    ref = ShowRef(SourceId.ARTE, programId),
                    title = title,
                    broadcaster = Broadcaster.ARTE,
                    channelLabel = "arte",
                    genre = t["genre"]["label"].nonBlank,
                    description = description,
                    imageUrl = image,
                ),
            )
        }
        return Teaser.OfEpisode(
            Episode(
                ref = EpisodeRef(SourceId.ARTE, programId),
                showRef = null,
                showTitle = title,
                title = t["subtitle"].nonBlank ?: title,
                broadcaster = Broadcaster.ARTE,
                channelLabel = "arte",
                description = description,
                imageUrl = image,
                durationSeconds = t["duration"].int ?: 0,
                publishedAt = parseInstantOrNull(t.path("availability", "start").string) ?: Instant.fromEpochSeconds(0),
                availableUntil = parseInstantOrNull(t.path("availability", "end").string),
                hasSubtitles = t["audioVersions"].list.any { it["code"].string?.startsWith("ST") == true },
                websiteUrl = t["url"].nonBlank,
            ),
        )
    }
}
