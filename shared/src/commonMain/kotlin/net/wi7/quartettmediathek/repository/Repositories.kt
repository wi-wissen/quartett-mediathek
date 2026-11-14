package net.wi7.quartettmediathek.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import net.wi7.quartettmediathek.model.DiscoverRow
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.subscription.Subscription

/*
 * Contract between the data layer (shared module) and the TV UI (app module).
 * The UI only talks to these interfaces. Real implementations live in this module;
 * the app also has fakes for previews and UI development.
 */

/** Catalog content from the broadcasters and MediathekViewWeb, cached on the device (see docs/SPEC.md "Cache"). */
interface CatalogRepository {
    /** Unified Discover rows (Highlights, Series, ...), emitted from cache first, then refreshed. */
    fun discoverRows(): Flow<List<DiscoverRow>>

    suspend fun show(ref: ShowRef): Show
    /** Episodes of a show, newest first, deduplicated. */
    suspend fun episodes(ref: ShowRef): List<Episode>
    suspend fun episode(ref: EpisodeRef): Episode

    /** Free-text search across all four broadcasters (MediathekViewWeb). Returns shows and episodes. */
    suspend fun search(query: String): List<Teaser>

    /** When catalog data was last loaded successfully, or null if never. */
    fun lastUpdated(): Flow<Instant?>
    fun isRefreshing(): Flow<Boolean>

    /** Reloads start pages and subscription feeds. Old data stays visible until new data arrived. */
    suspend fun refresh(force: Boolean)
}

/** One entry of the subscription feed: the episode in the preferred variant, plus whether it is new. */
data class FeedEntry(val episode: Episode, val subscriptionId: String, val isNew: Boolean)

/** Everything the user owns: subscriptions, watchlist, progress. Stored locally (SQLDelight). */
interface LibraryRepository {
    fun subscriptions(): Flow<List<Subscription>>
    /** All episodes of all subscriptions, newest first, duplicates and variants merged. */
    fun feed(): Flow<List<FeedEntry>>
    suspend fun subscribe(show: Show)
    suspend fun updateSubscription(subscription: Subscription)
    suspend fun unsubscribe(subscriptionId: String)
    fun subscriptionFor(show: ShowRef): Flow<Subscription?>

    fun bookmarks(): Flow<List<Teaser>>
    fun isBookmarked(key: String): Flow<Boolean>
    suspend fun toggleBookmark(teaser: Teaser)

    /** Episodes marked as watched, as `LibraryKeys.episode(ref)` ("SOURCE:key"). */
    fun watchedKeys(): Flow<Set<String>>

    fun continueWatching(): Flow<List<Progress>>
    /** Every episode that was played (in progress or watched), most recently played first, at most 200. */
    fun history(): Flow<List<Progress>>
    /** All played episodes without a limit; used to mark tiles (progress bar, "watched"). Order is unspecified. */
    fun allProgress(): Flow<List<Progress>>
    /** Forgets progress and watched state of an episode. */
    suspend fun removeFromHistory(ref: EpisodeRef)
    fun progress(ref: EpisodeRef): Flow<Progress?>
    suspend fun saveProgress(episode: Episode, positionSeconds: Long)
    suspend fun setWatched(episode: Episode, watched: Boolean)
}

data class Progress(val episode: Episode, val positionSeconds: Long, val watched: Boolean, val updatedAt: Instant)

interface PlaybackRepository {
    /** Resolves a fresh stream right before playback, honouring the preferred [variant]. */
    suspend fun stream(ref: EpisodeRef, variant: Variant = Variant.STANDARD): StreamInfo
}

enum class StartScreen { HOME, SUBSCRIPTIONS, BOOKMARKS }

interface SettingsRepository {
    fun startScreen(): Flow<StartScreen>
    suspend fun setStartScreen(screen: StartScreen)
    suspend fun clearCache()
}
