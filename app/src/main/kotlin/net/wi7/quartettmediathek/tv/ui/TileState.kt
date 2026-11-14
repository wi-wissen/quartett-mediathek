package net.wi7.quartettmediathek.tv.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.library.ProgressIndex
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
    /** History only: when the episode was last played; the tile then shows this instead of the publication age. */
    val playedAt: Instant? = null,
) {
    val key: String get() = teaser.key()
}

/** Snapshot of the library used to decorate teasers coming from the catalog. */
@Immutable
class LibraryOverlay(
    private val bookmarkKeys: Set<String> = emptySet(),
    private val progress: ProgressIndex = ProgressIndex.Empty,
    private val newEpisodeKeys: Set<String> = emptySet(),
    private val newCountByShow: Map<String, Int> = emptyMap(),
    private val subscribedShows: Set<String> = emptySet(),
) {
    fun decorate(teaser: Teaser): TileState = when (teaser) {
        is Teaser.OfEpisode -> {
            val p = progress.find(teaser.episode)
            TileState(
                teaser = teaser,
                isNew = teaser.episode.ref.key in newEpisodeKeys,
                bookmarked = teaser.key() in bookmarkKeys,
                subscribed = isSubscribed(teaser.episode.showRef, teaser.episode.showTitle),
                progressSeconds = p?.takeIf { !it.watched && it.positionSeconds > 0 }?.positionSeconds,
                watched = p?.watched == true,
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
    fun progressOf(episode: Episode): Progress? = progress.find(episode)
}

fun LibraryRepository.overlay(): Flow<LibraryOverlay> =
    combine(bookmarks(), allProgress(), feed(), subscriptions()) { bookmarks, progress, feed, subs ->
        val fresh = feed.filter { it.isNew }
        LibraryOverlay(
            bookmarkKeys = bookmarks.map { it.key() }.toSet(),
            progress = ProgressIndex(progress),
            newEpisodeKeys = fresh.map { it.episode.ref.key }.toSet(),
            newCountByShow = fresh.groupingBy { it.episode.showRef?.key.orEmpty() }.eachCount(),
            subscribedShows = subs.flatMap { listOf(it.id) + it.topics.map { t -> t.lowercase() } }.toSet(),
        )
    }
