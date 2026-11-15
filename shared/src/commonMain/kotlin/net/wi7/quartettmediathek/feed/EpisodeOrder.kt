package net.wi7.quartettmediathek.feed

import net.wi7.quartettmediathek.model.Episode

/** The one order for episode lists: newest first, and among episodes published together the higher season/episode. */
object EpisodeOrder {
    /** Whole seasons are often published at the same moment; then only the numbers tell the order. */
    val newestFirst: Comparator<Episode> =
        compareByDescending<Episode> { it.publishedAt }
            .thenByDescending { it.season ?: 0 }
            .thenByDescending { it.episodeNumber ?: 0 }

    /** Season numbers above this are years ("S2026/E340" for news and magazines), not seasons of a series. */
    private const val MAX_SERIES_SEASON = 99

    /** The season to show next to the episode number (also a year like 2026), or null when there is none. */
    fun displaySeason(episode: Episode): Int? = episode.season?.takeIf { it >= 1 }

    /** A series is watched from the start, a news or magazine show (seasons are years) from the newest episode. */
    private fun isSeries(episodes: List<Episode>) =
        episodes.size > 1 && episodes.all { (it.season ?: 0) in 1..MAX_SERIES_SEASON && it.episodeNumber != null }

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
