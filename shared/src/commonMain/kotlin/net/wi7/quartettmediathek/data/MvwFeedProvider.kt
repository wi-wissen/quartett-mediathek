package net.wi7.quartettmediathek.data

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.wi7.quartettmediathek.cache.CachePolicy
import net.wi7.quartettmediathek.cache.CacheStore
import net.wi7.quartettmediathek.feed.FeedCandidate
import net.wi7.quartettmediathek.network.parseJson
import net.wi7.quartettmediathek.source.mvw.MvwClause
import net.wi7.quartettmediathek.source.mvw.MvwClient
import net.wi7.quartettmediathek.source.mvw.MvwEntry
import net.wi7.quartettmediathek.source.mvw.MvwField
import net.wi7.quartettmediathek.source.mvw.MvwQuery
import net.wi7.quartettmediathek.source.mvw.toEpisode
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.subscription.SubscriptionFilter

/**
 * Feeds subscriptions from MediathekViewWeb: one query per topic, minimum duration applied server-side,
 * raw responses cached for a day ([CachePolicy.FEED]). Exact topic, broadcaster and variant filtering happen
 * afterwards in the library/feed logic.
 */
class MvwFeedProvider(
    private val mvw: MvwClient,
    private val cache: CacheStore,
    private val index: ContentIndex,
    private val images: EpisodeImageResolver? = null,
    private val pageSize: Int = 100,
    private val imageLimit: Int = 40,
) {
    @Volatile
    private var forceNext = false

    /** The next round of queries ignores the cache (manual refresh). */
    fun invalidate() {
        forceNext = true
    }

    suspend fun candidates(subscription: Subscription): List<FeedCandidate> = coroutineScope {
        val force = forceNext
        val entries = subscription.topics.distinct().map { topic ->
            async { topicEntries(topic, subscription.minDurationMinutes, force) }
        }.awaitAll().flatten()
        if (force) forceNext = false
        index.rememberMvw(entries)
        withImages(subscription, entries.mapNotNull { e -> e.toEpisode()?.let { FeedCandidate(it, e.urlVideo, e.size) } })
    }

    /**
     * Thumbnails for the newest [imageLimit] candidates that pass the subscription's filter (what the feed will
     * show); the rest stays without image. Lookups are cached, so later feed loads are instant.
     */
    private suspend fun withImages(subscription: Subscription, candidates: List<FeedCandidate>): List<FeedCandidate> {
        val resolver = images ?: return candidates
        val wanted = SubscriptionFilter.filter(subscription, candidates)
            .sortedByDescending { it.episode.publishedAt }
            .map { it.episode }
            .take(imageLimit)
        val found = resolver.imagesFor(wanted)
        if (found.isEmpty()) return candidates
        return candidates.map { c -> found[c.episode.ref]?.let { c.copy(episode = c.episode.copy(imageUrl = it)) } ?: c }
    }

    suspend fun query(query: MvwQuery, cacheKey: String, force: Boolean = false): List<MvwEntry> {
        val body = cache.getOrFetch(cacheKey, CachePolicy.FEED, force) { mvw.queryRaw(query) }
        return try {
            mvw.parseResult(parseJson(body, "cached MVW response")).entries.also { index.rememberMvw(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cache.remove(cacheKey)
            throw e
        }
    }

    private suspend fun topicEntries(topic: String, minMinutes: Int, force: Boolean): List<MvwEntry> {
        val q = MvwQuery(
            clauses = listOf(MvwClause(MvwField.TOPIC, topic)),
            size = pageSize,
            durationMinSeconds = minMinutes.takeIf { it > 0 }?.times(60),
        )
        return query(q, "mvw:feed:${topic.lowercase()}:$minMinutes", force)
    }
}
