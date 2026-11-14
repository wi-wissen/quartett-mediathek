package net.wi7.quartettmediathek.source.mvw

import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import net.wi7.quartettmediathek.network.fetchText
import net.wi7.quartettmediathek.network.get
import net.wi7.quartettmediathek.network.int
import net.wi7.quartettmediathek.network.list
import net.wi7.quartettmediathek.network.long
import net.wi7.quartettmediathek.network.parseJson
import net.wi7.quartettmediathek.network.path
import net.wi7.quartettmediathek.network.string
import net.wi7.quartettmediathek.source.SourceException

/** Raw MediathekViewWeb hit, all fields of SPEC 2.1. */
@Serializable
data class MvwEntry(
    val id: String,
    val channel: String,
    val topic: String,
    val title: String,
    val description: String?,
    val timestamp: Long,
    val duration: Long?,
    val size: Long?,
    val urlWebsite: String?,
    val urlSubtitle: String?,
    val urlVideo: String?,
    val urlVideoLow: String?,
    val urlVideoHd: String?,
)

data class MvwResult(val entries: List<MvwEntry>, val totalResults: Int)

class MvwClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://mediathekviewweb.de",
) {
    suspend fun query(query: MvwQuery): MvwResult = parseResult(parseJson(queryRaw(query), "MVW response"))

    /** The raw JSON response, e.g. to store it in the cache. */
    suspend fun queryRaw(query: MvwQuery): String =
        http.fetchText("$baseUrl/api/query") {
            method = HttpMethod.Post
            contentType(ContentType.Text.Plain)
            setBody(query.toJson())
        }

    internal fun parseResult(root: JsonElement): MvwResult {
        val result = root["result"] ?: throw SourceException("MVW answered without result: ${root["err"]}")
        val entries = result["results"].list.mapNotNull(::parseEntry)
        val total = result.path("queryInfo", "totalResults").int ?: entries.size
        return MvwResult(entries, total)
    }

    private fun parseEntry(e: JsonElement): MvwEntry? {
        val id = e["id"].string ?: return null
        fun s(key: String) = e[key].string?.takeIf { it.isNotBlank() }
        return MvwEntry(
            id = id,
            channel = e["channel"].string.orEmpty(),
            topic = e["topic"].string.orEmpty(),
            title = e["title"].string.orEmpty(),
            description = s("description"),
            timestamp = e["timestamp"].long ?: 0L,
            duration = e["duration"].long,
            size = e["size"].long,
            urlWebsite = s("url_website"),
            urlSubtitle = s("url_subtitle"),
            urlVideo = s("url_video"),
            urlVideoLow = s("url_video_low"),
            urlVideoHd = s("url_video_hd"),
        )
    }
}
