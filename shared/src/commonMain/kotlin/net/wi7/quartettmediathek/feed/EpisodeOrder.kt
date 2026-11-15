package net.wi7.quartettmediathek.feed

import net.wi7.quartettmediathek.model.Episode

/** The one order for episode lists: newest first, and among episodes published together the higher season/episode. */
object EpisodeOrder {
    /** Whole seasons are often published at the same moment; then only the numbers tell the order. */
    val newestFirst: Comparator<Episode> =
        compareByDescending<Episode> { it.publishedAt }
            .thenByDescending { it.season ?: 0 }
            .thenByDescending { it.episodeNumber ?: 0 }

    /** Season numbers above this are years ("Staffel 2025") and not worth showing. */
    private const val MAX_SHOWN_SEASON = 99

    /** The season to show next to the episode number, or null for shows without real seasons. */
    fun displaySeason(episode: Episode): Int? = episode.season?.takeIf { it in 1..MAX_SHOWN_SEASON }

    /** A series is watched from the start, a news or magazine show from the newest episode. */
    private fun isSeries(episodes: List<Episode>) =
        episodes.size > 1 && episodes.all { displaySeason(it) != null && it.episodeNumber != null }

    /**
     * What the show page's play button starts when nothing is in progress: for a series the first unwatched episode
     * after the last watched one (S1E1 for a new series), otherwise the newest unwatched episode.
     */
    fun nextUp(episodes: List<Episode>, isWatched: (Episode) -> Boolean): Episode? {
        if (!isSeries(episodes)) return episodes.sortedWith(newestFirst).firstOrNull { !isWatched(it) } ?: episodes.firstOrNull()
        val inOrder = episodes.sortedWith(compareBy<Episode> { it.season }.thenBy { it.episodeNumber })
        val lastWatched = inOrder.indexOfLast(isWatched)
        return inOrder.drop(lastWatched + 1).firstOrNull { !isWatched(it) }
            ?: inOrder.firstOrNull { !isWatched(it) }
            ?: inOrder.first()
    }
}
