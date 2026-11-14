package net.wi7.quartettmediathek.cache

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.wi7.quartettmediathek.db.QuartettDatabase

/** How long cached data counts as fresh. */
data class CachePolicy(val maxAge: Duration) {
    fun isFresh(fetchedAt: Instant, now: Instant): Boolean = now - fetchedAt < maxAge

    companion object {
        /** Start pages and subscription feeds: once per day (docs/SPEC.md "Cache"). */
        val START_PAGE = CachePolicy(24.hours)
        val FEED = CachePolicy(24.hours)
    }
}

/**
 * A cache value. [isStale] = the value is older than the policy allows and a refresh is running; a fresh value
 * follows on the same flow.
 */
data class CacheResult(val body: String, val fetchedAt: Instant, val isStale: Boolean)

/** Key/value cache of response bodies on top of the `http_cache` table. */
class CacheStore(
    private val db: QuartettDatabase,
    private val clock: Clock = Clock.System,
) {
    private val q get() = db.libraryQueries

    fun get(key: String): CacheResult? =
        q.cached(key).executeAsOneOrNull()?.let { CacheResult(it.body, Instant.fromEpochMilliseconds(it.fetched_at_epoch_ms), false) }

    fun put(key: String, body: String) {
        q.putCache(key, body, clock.now().toEpochMilliseconds())
    }

    fun remove(key: String) = q.deleteCacheKey(key)

    fun clear() = q.clearCache()

    /** When [key] was fetched last, or null. */
    fun fetchedAt(key: String): Instant? =
        q.cacheFetchedAt(key).executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it) }

    /**
     * Returns the cached body if fresh, otherwise fetches, stores and returns it. If the fetch fails the stale
     * body is returned when there is one; otherwise the error propagates. [forceRefresh] ignores freshness.
     */
    suspend fun getOrFetch(
        key: String,
        policy: CachePolicy,
        forceRefresh: Boolean = false,
        fetch: suspend () -> String,
    ): String {
        val cached = get(key)
        if (cached != null && !forceRefresh && policy.isFresh(cached.fetchedAt, clock.now())) return cached.body
        return try {
            fetch().also { put(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.body ?: throw e
        }
    }

    /**
     * Stale-while-revalidate. Emits the cached value immediately (marked stale when it is too old), then the
     * freshly fetched value. A fresh cache emits once and does not fetch. When the refresh fails the cached
     * value stays; without any cached value the error is thrown.
     */
    fun load(
        key: String,
        policy: CachePolicy,
        forceRefresh: Boolean = false,
        fetch: suspend () -> String,
    ): Flow<CacheResult> = flow {
        val cached = get(key)
        if (cached != null) {
            val fresh = !forceRefresh && policy.isFresh(cached.fetchedAt, clock.now())
            emit(cached.copy(isStale = !fresh))
            if (fresh) return@flow
        }
        try {
            val body = fetch()
            put(key, body)
            emit(CacheResult(body, clock.now(), false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached == null) throw e
        }
    }
}
