package net.wi7.quartettmediathek.data

import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import net.wi7.quartettmediathek.cache.CachePolicy
import net.wi7.quartettmediathek.cache.CacheStore
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.network.fetchHead
import net.wi7.quartettmediathek.source.ard.ArdClient
import net.wi7.quartettmediathek.source.arte.ArteClient
import net.wi7.quartettmediathek.source.mvw.ardItemIdOf
import net.wi7.quartettmediathek.source.zdf.ZdfClient

/** How an MVW episode page is turned into a picture. Pure, see [classifyEpisodePage]. */
sealed interface ImageStrategy {
    data class Ard(val itemId: String) : ImageStrategy
    data class Zdf(val canonical: String) : ImageStrategy
    data class Arte(val programId: String) : ImageStrategy
    data object OgImage : ImageStrategy
}

/** Picks the lookup strategy from the broadcaster page URL (`MvwEntry.urlWebsite`). */
fun classifyEpisodePage(url: String): ImageStrategy {
    val host = url.substringAfter("://").substringBefore('/').lowercase()
    val segments = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
        .split('/').filter { it.isNotEmpty() }
    return when {
        host.endsWith("ardmediathek.de") -> ardItemIdOf(url)?.let { ImageStrategy.Ard(it) } ?: ImageStrategy.OgImage
        host == "www.zdf.de" || host == "zdf.de" ->
            segments.lastOrNull()?.removeSuffix(".html")?.takeIf { it.isNotEmpty() }
                ?.let { ImageStrategy.Zdf(it) } ?: ImageStrategy.OgImage
        host.endsWith("arte.tv") -> {
            val i = segments.indexOf("videos")
            segments.getOrNull(i + 1)?.takeIf { i >= 0 }?.let { ImageStrategy.Arte(it) } ?: ImageStrategy.OgImage
        }
        else -> ImageStrategy.OgImage
    }
}

private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
private val OG_PROPERTY = Regex("""(?:property|name)\s*=\s*["']og:image["']""", RegexOption.IGNORE_CASE)
private val CONTENT_ATTR = Regex("""content\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)

/** Value of the `og:image` meta tag in [html] (attribute order does not matter), made absolute against [pageUrl]. */
fun parseOgImage(html: String, pageUrl: String): String? {
    val raw = META_TAG.findAll(html).firstNotNullOfOrNull { tag ->
        if (!OG_PROPERTY.containsMatchIn(tag.value)) return@firstNotNullOfOrNull null
        CONTENT_ATTR.find(tag.value)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
    }?.replace("&amp;", "&")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> pageUrl.substringBefore("://") + "://" + pageUrl.substringAfter("://").substringBefore('/') + raw
        else -> null
    }
}

/**
 * Finds thumbnails for MediathekViewWeb episodes, which carry none themselves. Strategy by website: ARD item API,
 * ZDF GraphQL (falls back to `og:image`), arte player config, otherwise the `og:image` of the page (only the
 * start of the page is read). Results, including "no image", are cached for 30 days; failures for an hour.
 * A lookup never throws: no image is the worst case.
 */
class EpisodeImageResolver(
    private val http: HttpClient,
    private val ard: ArdClient,
    private val zdf: ZdfClient,
    private val arte: ArteClient,
    private val cache: CacheStore,
    maxConcurrency: Int = 6,
    private val lookupTimeoutMs: Long = 10_000,
    private val clock: Clock = Clock.System,
) {
    private val gate = Semaphore(maxConcurrency)

    /** Thumbnail URL of the page [websiteUrl], cached under [cacheKey]; null when there is none. */
    suspend fun imageFor(cacheKey: String, websiteUrl: String?): String? {
        if (websiteUrl.isNullOrBlank()) return null
        val key = "img:$cacheKey"
        cache.get(key)?.let { hit ->
            val policy = if (hit.body == FAILED) CachePolicy.IMAGE_RETRY else CachePolicy.IMAGE
            if (policy.isFresh(hit.fetchedAt, clock.now())) return decode(hit.body)
        }
        val outcome = gate.withPermit {
            try {
                withTimeoutOrNull(lookupTimeoutMs) { Found(lookup(websiteUrl)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val body = when {
            outcome == null -> FAILED
            outcome.url == null -> NONE
            else -> PREFIX + outcome.url
        }
        runCatching { cache.put(key, body) }
        return outcome?.url
    }

    /** Thumbnails for [episodes] (looked up in parallel, at most 6 at a time); episodes without result are absent. */
    suspend fun imagesFor(episodes: Collection<Episode>): Map<EpisodeRef, String> = coroutineScope {
        episodes.distinctBy { it.ref }
            .filter { it.imageUrl == null && it.websiteUrl != null }
            .map { e -> async { imageFor(e.ref.key, e.websiteUrl)?.let { e.ref to it } } }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    /** Fills `imageUrl` of the first [limit] episodes (in list order) that have none. */
    suspend fun fill(episodes: List<Episode>, limit: Int = 40): List<Episode> {
        val wanted = episodes.filter { it.imageUrl == null && it.websiteUrl != null }.take(limit)
        return episodes.withImages(imagesFor(wanted))
    }

    private suspend fun lookup(url: String): String? = when (val s = classifyEpisodePage(url)) {
        is ImageStrategy.Ard -> ard.itemImage(s.itemId)
        is ImageStrategy.Arte -> arte.program(s.programId).imageUrl
        is ImageStrategy.Zdf -> (try {
            zdf.videoImage(s.canonical)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }) ?: og(url)
        ImageStrategy.OgImage -> og(url)
    }

    private suspend fun og(url: String): String? = parseOgImage(http.fetchHead(url), url)

    private fun decode(body: String): String? = body.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    private class Found(val url: String?)

    private companion object {
        const val PREFIX = "ok:"
        const val NONE = "none"
        const val FAILED = "failed"
    }
}

/** Copies [images] into the matching episodes. */
fun List<Episode>.withImages(images: Map<EpisodeRef, String>): List<Episode> =
    if (images.isEmpty()) this else map { e -> images[e.ref]?.let { e.copy(imageUrl = it) } ?: e }
