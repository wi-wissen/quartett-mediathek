package net.wi7.quartettmediathek.feed

import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.Variant

/**
 * One raw result of a subscription query (typically one MediathekViewWeb hit mapped to an [Episode]).
 * [videoUrl] and [sizeBytes] are only used to recognise the same broadcast on several channels.
 */
data class FeedCandidate(val episode: Episode, val videoUrl: String? = null, val sizeBytes: Long? = null)

/**
 * One logical episode with all its variants. [episode] is the representation chosen for the preferred variant
 * (its `variants` holds every variant that exists, its `versionRefs` where each one lives); [representations] has the concrete episode per variant.
 */
data class MergedEpisode(val episode: Episode, val representations: Map<Variant, Episode>)

/** Duplicate and variant handling (docs/SPEC.md section 3). Pure functions. */
object EpisodeMerger {
    private val TIME_TOLERANCE_MS = 2.minutes.inWholeMilliseconds
    private const val DURATION_TOLERANCE_S = 5

    /**
     * Full pipeline: drop duplicate broadcasts, merge variants, choose the representation for [preferredVariant].
     * Result is newest first.
     */
    fun merge(
        candidates: List<FeedCandidate>,
        preferredVariant: Variant = Variant.STANDARD,
        broadcasterOrder: List<Broadcaster> = emptyList(),
    ): List<MergedEpisode> =
        mergeVariants(dedupeBroadcasts(candidates, broadcasterOrder), preferredVariant)

    /**
     * Keeps one candidate per broadcast. Same broadcast = same `videoUrl`, or (fallback) same size with
     * publication time within 2 minutes and duration within 5 seconds. Of a group the candidate whose broadcaster
     * comes first in [broadcasterOrder] wins (unlisted broadcasters last, then input order); missing description
     * and image are taken over from the others.
     */
    fun dedupeBroadcasts(
        candidates: List<FeedCandidate>,
        broadcasterOrder: List<Broadcaster> = emptyList(),
    ): List<FeedCandidate> {
        val n = candidates.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in 0 until n) for (j in i + 1 until n) {
            if (sameBroadcast(candidates[i], candidates[j])) parent[find(j)] = find(i)
        }
        val groups = LinkedHashMap<Int, MutableList<Int>>()
        for (i in 0 until n) groups.getOrPut(find(i)) { mutableListOf() }.add(i)

        fun rank(i: Int): Int =
            broadcasterOrder.indexOf(candidates[i].episode.broadcaster).let { if (it < 0) Int.MAX_VALUE else it }
        return groups.values.map { members ->
            val best = members.minWith(compareBy<Int>({ rank(it) }, { it }))
            val winner = candidates[best]
            val others = members.filter { it != best }.map { candidates[it] }
            val ep = winner.episode
            winner.copy(
                episode = ep.copy(
                    description = ep.description ?: others.firstNotNullOfOrNull { it.episode.description },
                    imageUrl = ep.imageUrl ?: others.firstNotNullOfOrNull { it.episode.imageUrl },
                ),
            )
        }
    }

    private fun sameBroadcast(a: FeedCandidate, b: FeedCandidate): Boolean {
        if (a.videoUrl != null && a.videoUrl == b.videoUrl) return true
        val sa = a.sizeBytes
        val sb = b.sizeBytes
        if (sa == null || sb == null || sa != sb) return false
        // MediathekViewWeb reports the same size for all versions of an episode (standard, audio description,
        // English). Different versions are never the same broadcast; mergeVariants() groups them instead.
        if (TitleParser.parse(a.episode.title).variant != TitleParser.parse(b.episode.title).variant) return false
        return abs(a.episode.publishedAt.toEpochMilliseconds() - b.episode.publishedAt.toEpochMilliseconds()) <= TIME_TOLERANCE_MS &&
            abs(a.episode.durationSeconds - b.episode.durationSeconds) <= DURATION_TOLERANCE_S
    }

    private class Item(val episode: Episode, val variants: Set<Variant>, val key: String)

    /**
     * Merges candidates with the same cleaned title, show and publication time (+-2 min) into one episode with
     * all variants. Titles are cleaned of variant and season markers; season/episode numbers found in the title
     * fill missing values. Result newest first.
     */
    fun mergeVariants(candidates: List<FeedCandidate>, preferredVariant: Variant = Variant.STANDARD): List<MergedEpisode> {
        val items = candidates.map { c ->
            val parsed = TitleParser.parse(c.episode.title)
            val variants = if (parsed.variant != Variant.STANDARD) setOf(parsed.variant) else c.episode.variants
            val cleaned = c.episode.copy(
                title = parsed.title,
                season = c.episode.season ?: parsed.season,
                episodeNumber = c.episode.episodeNumber ?: parsed.episode,
            )
            Item(cleaned, variants, normalizeText(c.episode.showTitle) + "\u0000" + normalizeText(parsed.title))
        }.sortedBy { it.episode.publishedAt }

        val clusters = mutableListOf<MutableList<Item>>()
        for (item in items) {
            val cluster = clusters.firstOrNull { cl ->
                cl[0].key == item.key &&
                    abs(cl[0].episode.publishedAt.toEpochMilliseconds() - item.episode.publishedAt.toEpochMilliseconds()) <= TIME_TOLERANCE_MS
            }
            if (cluster != null) cluster.add(item) else clusters.add(mutableListOf(item))
        }

        return clusters.map { cluster ->
            val reps = LinkedHashMap<Variant, Episode>()
            for (item in cluster) for (v in item.variants) reps.getOrPut(v) { item.episode }
            val all = reps.keys.toSet()
            val refs = reps.mapValues { (_, e) -> e.ref }
            val finalReps = reps.mapValues { (_, e) -> e.copy(variants = all, versionRefs = refs) }
            MergedEpisode(pick(finalReps, preferredVariant), finalReps)
        }.sortedByDescending { it.episode.publishedAt }
    }

    /** The preferred variant if it exists, otherwise standard, otherwise whatever there is. */
    fun pick(representations: Map<Variant, Episode>, preferred: Variant): Episode =
        representations[preferred] ?: representations[Variant.STANDARD] ?: representations.values.first()
}
