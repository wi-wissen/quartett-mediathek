package net.wi7.quartettmediathek.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.wi7.quartettmediathek.source.SourceException

/** Neutral user agent sent to all origins. Never add personal data here. */
const val USER_AGENT = "QuartettMediathek/0.1"

/** Shared Json configuration: tolerant towards new fields the origins may add. */
val QuartettJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Cause of a [SourceException] when the origin answered with a non-2xx status. */
class HttpStatusException(val status: Int, val url: String) : Exception("HTTP $status for $url")

/**
 * Performs a request and returns the response body as text. Network errors and non-2xx answers are
 * reported as [SourceException]; for a bad status the cause is an [HttpStatusException].
 */
suspend fun HttpClient.fetchText(url: String, configure: HttpRequestBuilder.() -> Unit = {}): String {
    val response = try {
        request(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            configure()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SourceException("Request failed: $url", e)
    }
    if (!response.status.isSuccess()) {
        throw SourceException("HTTP ${response.status.value} for $url", HttpStatusException(response.status.value, url))
    }
    return try {
        response.bodyAsText()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SourceException("Could not read response of $url", e)
    }
}

/** Parses [text] as JSON, reporting malformed documents as [SourceException]. */
fun parseJson(text: String, what: String = "response"): JsonElement =
    try {
        QuartettJson.parseToJsonElement(text)
    } catch (e: Exception) {
        throw SourceException("Malformed JSON in $what", e)
    }
