package net.wi7.quartettmediathek.library

import net.wi7.quartettmediathek.feed.TitleParser
import net.wi7.quartettmediathek.feed.normalizeText
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.repository.Progress

/**
 * Finds the playback progress that belongs to an episode shown anywhere in the app.
 *
 * The same broadcast can appear under different refs (a ZDF canonical on the show page, a MediathekViewWeb id in the
 * subscription feed or search). Progress is stored under the ref that was played, so a plain ref lookup would miss it.
 * The index therefore matches by ref first and then by a content fingerprint (broadcaster, show, title).
 */
class ProgressIndex(entries: List<Progress>) {
    private val byRef: Map<String, Progress> = entries.associateBy { LibraryKeys.episode(it.episode.ref) }
    private val byFingerprint: Map<String, Progress> =
        entries.sortedBy { it.updatedAt }.associateBy { fingerprint(it.episode) } // newest wins

    fun find(episode: Episode): Progress? {
        byRef[LibraryKeys.episode(episode.ref)]?.let { return it }
        val other = byFingerprint[fingerprint(episode)] ?: return null
        // A different broadcast with the same title but a clearly different length is not the same episode.
        return other.takeIf { sameLength(it.episode.durationSeconds, episode.durationSeconds) }
    }

    companion object {
        val Empty = ProgressIndex(emptyList())

        fun fingerprint(e: Episode): String =
            listOf(
                e.broadcaster.name,
                normalizeText(TitleParser.cleanShowName(e.showTitle)),
                normalizeText(TitleParser.parse(e.title).title),
            ).joinToString("|")

        private fun sameLength(a: Int, b: Int): Boolean =
            a <= 0 || b <= 0 || kotlin.math.abs(a - b) <= maxOf(a, b) / 5
    }
}
