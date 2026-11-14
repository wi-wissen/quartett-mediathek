package net.wi7.quartettmediathek.tv.data.fake

import net.wi7.quartettmediathek.library.LibraryKeys
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.wi7.quartettmediathek.model.DiscoverRow
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.FeedEntry
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository
import net.wi7.quartettmediathek.repository.Progress
import net.wi7.quartettmediathek.repository.SettingsRepository
import net.wi7.quartettmediathek.repository.StartScreen
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.tv.ui.key

class FakeCatalogRepository : CatalogRepository {
    private val updated = MutableStateFlow<Instant?>(FakeData.now - 14.hours)
    private val refreshing = MutableStateFlow(false)

    override fun discoverRows(): Flow<List<DiscoverRow>> = MutableStateFlow(FakeData.discoverRows)

    override suspend fun show(ref: ShowRef): Show = FakeData.show(ref.key)

    override suspend fun episodes(ref: ShowRef): List<Episode> = FakeData.episodesByShow[ref.key].orEmpty()

    override suspend fun episode(ref: EpisodeRef): Episode = FakeData.episode(ref.key)

    override suspend fun search(query: String): List<Teaser> {
        delay(200)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val shows = FakeData.shows.filter { q in it.title.lowercase() || q in (it.genre ?: "").lowercase() }
        val episodes = FakeData.allEpisodes.filter { q in it.title.lowercase() && (it.showRef?.let { r -> (FakeData.episodesByShow[r.key]?.size ?: 0) > 1 } == true) }
        return shows.map { Teaser.OfShow(it) } + episodes.take(9).map { Teaser.OfEpisode(it) }
    }

    override fun lastUpdated(): Flow<Instant?> = updated
    override fun isRefreshing(): Flow<Boolean> = refreshing

    override suspend fun refresh(force: Boolean) {
        refreshing.value = true
        delay(1500)
        updated.value = FakeData.now
        refreshing.value = false
    }
}

/** In-memory library. Subscription id = show key, episode progress keyed by episode key. */
class FakeLibraryRepository : LibraryRepository {
    private val subs = MutableStateFlow(
        FakeData.initialSubscriptionSeenDaysAgo.map { (id, daysAgo) ->
            val show = FakeData.show(id)
            Subscription(
                id = id, title = show.title, topics = listOf(show.title), broadcasters = setOf(show.broadcaster),
                seenUntilEpochMs = (FakeData.now - daysAgo.days).toEpochMilliseconds(),
            )
        },
    )
    private val bookmarks = MutableStateFlow(FakeData.initialBookmarks)
    private val progress = MutableStateFlow(FakeData.sampleProgress().associateBy { it.episode.ref.key })

    override fun subscriptions(): Flow<List<Subscription>> = subs

    override fun feed(): Flow<List<FeedEntry>> = combine(subs, progress) { subscriptions, prog ->
        subscriptions.flatMap { sub ->
            FakeData.episodesByShow[sub.id].orEmpty().filter { it.durationSeconds >= sub.minDurationMinutes * 60 }.map { ep ->
                val fresh = ep.publishedAt.toEpochMilliseconds() > sub.seenUntilEpochMs && prog[ep.ref.key] == null
                FeedEntry(ep, sub.id, fresh)
            }
        }.sortedByDescending { it.episode.publishedAt }
    }

    override suspend fun subscribe(show: Show) {
        subs.update { list ->
            if (list.any { it.id == show.ref.key }) list else list + Subscription(
                id = show.ref.key, title = show.title, topics = listOf(show.title), broadcasters = setOf(show.broadcaster),
                seenUntilEpochMs = FakeData.now.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        subs.update { list -> list.map { if (it.id == subscription.id) subscription else it } }
    }

    override suspend fun unsubscribe(subscriptionId: String) {
        subs.update { list -> list.filterNot { it.id == subscriptionId } }
    }

    override fun subscriptionFor(show: ShowRef): Flow<Subscription?> = subs.map { list -> list.firstOrNull { it.id == show.key } }

    override fun bookmarks(): Flow<List<Teaser>> = bookmarks
    override fun isBookmarked(key: String): Flow<Boolean> = bookmarks.map { list -> list.any { it.key() == key } }

    override suspend fun toggleBookmark(teaser: Teaser) {
        bookmarks.update { list -> if (list.any { it.key() == teaser.key() }) list.filterNot { it.key() == teaser.key() } else listOf(teaser) + list }
    }

    override fun watchedKeys(): Flow<Set<String>> =
        progress.map { all -> all.values.filter { it.watched }.map { LibraryKeys.episode(it.episode.ref) }.toSet() }

    override fun continueWatching(): Flow<List<Progress>> = progress.map { it.values.sortedByDescending { p -> p.updatedAt } }
    override fun history(): Flow<List<Progress>> =
        progress.map { it.values.filter { p -> p.watched || p.positionSeconds > 0 }.sortedByDescending { p -> p.updatedAt } }

    override fun allProgress(): Flow<List<Progress>> = progress.map { it.values.toList() }

    override suspend fun removeFromHistory(ref: EpisodeRef) {
        progress.update { it - ref.key }
    }

    override fun progress(ref: EpisodeRef): Flow<Progress?> = progress.map { it[ref.key] }

    override suspend fun saveProgress(episode: Episode, positionSeconds: Long) {
        progress.update { it + (episode.ref.key to Progress(episode, positionSeconds, false, FakeData.now)) }
    }

    override suspend fun setWatched(episode: Episode, watched: Boolean) {
        progress.update {
            if (watched) it + (episode.ref.key to Progress(episode, episode.durationSeconds.toLong(), true, FakeData.now))
            else it - episode.ref.key
        }
    }
}

class FakePlaybackRepository : PlaybackRepository {
    override suspend fun stream(ref: EpisodeRef, variant: Variant): StreamInfo =
        StreamInfo(url = "https://example.invalid/sample.mp4", mimeType = "video/mp4", height = 720)
}

class FakeSettingsRepository : SettingsRepository {
    private val start = MutableStateFlow(StartScreen.HOME)
    override fun startScreen(): Flow<StartScreen> = start
    override suspend fun setStartScreen(screen: StartScreen) { start.value = screen }
    override suspend fun clearCache() { delay(300) }
}
