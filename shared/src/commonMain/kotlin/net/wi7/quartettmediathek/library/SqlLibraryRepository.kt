package net.wi7.quartettmediathek.library

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.feed.FeedBuilder
import net.wi7.quartettmediathek.feed.FeedCandidate
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.FeedEntry
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.Progress
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.subscription.SubscriptionFactory

/**
 * The seam to the network layer: loads the raw episode candidates (e.g. MediathekViewWeb hits mapped to
 * [FeedCandidate]) for one subscription. Should be cached by the caller (see `CacheStore`); it is called again
 * whenever subscriptions or watched state change.
 */
typealias FeedCandidateProvider = suspend (Subscription) -> List<FeedCandidate>

/** Percentage of the duration from which an episode counts as watched. */
const val WATCHED_THRESHOLD_PERCENT = 95

/** `LibraryRepository` on top of the SQLDelight database. */
class SqlLibraryRepository(
    private val db: QuartettDatabase,
    private val feedCandidates: FeedCandidateProvider = { emptyList() },
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val continueWatchingLimit: Int = 20,
) : LibraryRepository {
    private val q get() = db.libraryQueries

    // Subscriptions

    override fun subscriptions(): Flow<List<Subscription>> =
        q.allSubscriptions().asFlow().mapToList(dispatcher).map { rows ->
            rows.mapNotNull { runCatching { LibraryJson.decodeFromString(Subscription.serializer(), it.json) }.getOrNull() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun feed(): Flow<List<FeedEntry>> =
        combine(subscriptions(), watchedKeys()) { subs, watched -> subs to watched }
            .mapLatest { (subs, watched) ->
                val loaded = coroutineScope {
                    subs.map { s -> async { s to loadCandidates(s) } }.awaitAll()
                }
                FeedBuilder.build(loaded) { LibraryKeys.episode(it) in watched }
            }

    private suspend fun loadCandidates(s: Subscription): List<FeedCandidate> = try {
        feedCandidates(s)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun watchedKeys(): Flow<Set<String>> =
        q.watchedKeys().asFlow().mapToList(dispatcher).map { it.toSet() }

    override suspend fun subscribe(show: Show) {
        val id = SubscriptionFactory.idFor(show.ref)
        if (q.subscriptionById(id).executeAsOneOrNull() != null) return
        val sub = SubscriptionFactory.forShow(show, clock.now())
        q.upsertSubscription(sub.id, encode(sub), sub.seenUntilEpochMs, clock.now().toEpochMilliseconds())
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        val existing = q.subscriptionById(subscription.id).executeAsOneOrNull() ?: return
        q.upsertSubscription(subscription.id, encode(subscription), subscription.seenUntilEpochMs, existing.created_at_epoch_ms)
    }

    override suspend fun unsubscribe(subscriptionId: String) {
        q.deleteSubscription(subscriptionId)
    }

    override fun subscriptionFor(show: ShowRef): Flow<Subscription?> =
        q.subscriptionById(SubscriptionFactory.idFor(show)).asFlow().mapToOneOrNull(dispatcher).map { row ->
            row?.let { runCatching { LibraryJson.decodeFromString(Subscription.serializer(), it.json) }.getOrNull() }
        }

    private fun encode(sub: Subscription) = LibraryJson.encodeToString(Subscription.serializer(), sub)

    // Bookmarks

    override fun bookmarks(): Flow<List<Teaser>> =
        q.allBookmarks().asFlow().mapToList(dispatcher).map { rows -> rows.mapNotNull { decodeTeaser(it.snapshot_json) } }

    /** [key] as produced by [LibraryKeys.bookmark]. */
    override fun isBookmarked(key: String): Flow<Boolean> =
        q.bookmarkByKey(key).asFlow().mapToOneOrNull(dispatcher).map { it != null }

    override suspend fun toggleBookmark(teaser: Teaser) {
        val key = LibraryKeys.bookmark(teaser)
        db.transaction {
            if (q.bookmarkByKey(key).executeAsOneOrNull() != null) {
                q.deleteBookmark(key)
            } else {
                q.upsertBookmark(key, teaser.toJson(), clock.now().toEpochMilliseconds())
            }
        }
    }

    // Progress

    override fun continueWatching(): Flow<List<Progress>> =
        q.continueWatching(continueWatchingLimit.toLong()).asFlow().mapToList(dispatcher).map { rows ->
            rows.mapNotNull { r ->
                decodeEpisode(r.snapshot_json)?.let {
                    Progress(it, r.position_seconds, r.watched != 0L, Instant.fromEpochMilliseconds(r.updated_at_epoch_ms))
                }
            }
        }

    override fun progress(ref: EpisodeRef): Flow<Progress?> =
        q.progressFor(LibraryKeys.episode(ref)).asFlow().mapToOneOrNull(dispatcher).map { r ->
            r?.let { row ->
                decodeEpisode(row.snapshot_json)?.let {
                    Progress(it, row.position_seconds, row.watched != 0L, Instant.fromEpochMilliseconds(row.updated_at_epoch_ms))
                }
            }
        }

    override suspend fun saveProgress(episode: Episode, positionSeconds: Long) {
        val duration = episode.durationSeconds.toLong()
        val watched = duration > 0 && positionSeconds * 100 >= duration * WATCHED_THRESHOLD_PERCENT
        write(episode, positionSeconds, watched)
    }

    override suspend fun setWatched(episode: Episode, watched: Boolean) {
        write(episode, if (watched) episode.durationSeconds.toLong() else 0L, watched)
    }

    private fun write(episode: Episode, position: Long, watched: Boolean) {
        q.upsertProgress(
            LibraryKeys.episode(episode.ref),
            episode.toJson(),
            position,
            episode.durationSeconds.toLong(),
            if (watched) 1L else 0L,
            clock.now().toEpochMilliseconds(),
        )
    }
}
