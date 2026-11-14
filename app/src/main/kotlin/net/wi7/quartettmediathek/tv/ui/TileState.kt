package net.wi7.quartettmediathek.tv.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.library.LibraryKeys
import net.wi7.quartettmediathek.subscription.SubscriptionFactory
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.Progress

/** A teaser plus everything the tile shows from the user's library. */
@Immutable
data class TileState(
    val teaser: Teaser,
    /** Episode is new in a subscription. */
    val isNew: Boolean = false,
    /** Show: number of new episodes. */
    val newCount: Int = 0,
    val bookmarked: Boolean = false,
    val subscribed: Boolean = false,
    val progressSeconds: Long? = null,
    val watched: Boolean = false,
) {
    val key: String get() = teaser.key()
}

/** Snapshot of the library used to decorate teasers coming from the catalog. */
@Immutable
class LibraryOverlay(
    private val bookmarkKeys: Set<String> = emptySet(),
    private val progress: Map<String, Progress> = emptyMap(),
    private val newEpisodeKeys: Set<String> = emptySet(),
    private val newCountByShow: Map<String, Int> = emptyMap(),
    private val subscribedShows: Set<String> = emptySet(),
    private val watchedKeys: Set<String> = emptySet(),
) {
    fun decorate(teaser: Teaser): TileState = when (teaser) {
        is Teaser.OfEpisode -> {
            val p = progress[teaser.episode.ref.key]
            TileState(
                teaser = teaser,
                isNew = teaser.episode.ref.key in newEpisodeKeys,
                bookmarked = teaser.key() in bookmarkKeys,
                subscribed = isSubscribed(teaser.episode.showRef, teaser.episode.showTitle),
                progressSeconds = p?.takeIf { !it.watched && it.positionSeconds > 0 }?.positionSeconds,
                watched = p?.watched == true || LibraryKeys.episode(teaser.episode.ref) in watchedKeys,
            )
        }
        is Teaser.OfShow -> TileState(
            teaser = teaser,
            newCount = newCountByShow[teaser.show.ref.key] ?: 0,
            bookmarked = teaser.key() in bookmarkKeys,
            subscribed = isSubscribed(teaser.show.ref, teaser.show.title),
        )
    }

    /** A subscription belongs to a show when its id is the show's id or one of its topics is the show title. */
    private fun isSubscribed(showRef: ShowRef?, title: String) =
        (showRef != null && SubscriptionFactory.idFor(showRef) in subscribedShows) || title.lowercase() in subscribedShows

    fun decorate(episode: Episode) = decorate(Teaser.OfEpisode(episode))
    fun decorate(show: Show) = decorate(Teaser.OfShow(show))
    fun progressOf(episodeKey: String): Progress? = progress[episodeKey]
}

fun LibraryRepository.overlay(): Flow<LibraryOverlay> =
    combine(bookmarks(), continueWatching(), feed(), subscriptions(), watchedKeys()) { bookmarks, progress, feed, subs, watched ->
        val fresh = feed.filter { it.isNew }
        LibraryOverlay(
            bookmarkKeys = bookmarks.map { it.key() }.toSet(),
            progress = progress.associateBy { it.episode.ref.key },
            newEpisodeKeys = fresh.map { it.episode.ref.key }.toSet(),
            newCountByShow = fresh.groupingBy { it.episode.showRef?.key.orEmpty() }.eachCount(),
            subscribedShows = subs.flatMap { listOf(it.id) + it.topics.map { t -> t.lowercase() } }.toSet(),
            watchedKeys = watched,
        )
    }
