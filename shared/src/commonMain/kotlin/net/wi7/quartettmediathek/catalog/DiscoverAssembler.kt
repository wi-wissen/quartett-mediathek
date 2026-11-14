package net.wi7.quartettmediathek.catalog

import net.wi7.quartettmediathek.feed.normalizeText
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.DiscoverCategory
import net.wi7.quartettmediathek.model.DiscoverRow
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.Teaser

/** Merges the rows of all broadcasters into unified [DiscoverRow]s. */
object DiscoverAssembler {
    /**
     * One row per non-empty category in `DiscoverCategory` order. Within a category the teasers of the
     * broadcasters are interleaved (ARD, ZDF, 3sat, arte, ARD, ...), duplicates removed. Skipped rows are dropped.
     *
     * @param broadcasters when not null only rows of these broadcasters are used
     */
    fun assemble(rows: List<SourceRow>, broadcasters: Set<Broadcaster>? = null): List<DiscoverRow> {
        val usable = rows.filter { broadcasters == null || it.broadcaster in broadcasters }
        val byCategory = usable.mapNotNull { row -> CategoryMapper.categorize(row)?.let { it to row } }
            .groupBy({ it.first }, { it.second })
        return DiscoverCategory.entries.mapNotNull { category ->
            val categoryRows = byCategory[category] ?: return@mapNotNull null
            val items = interleave(categoryRows)
            if (items.isEmpty()) null else DiscoverRow(category, items)
        }
    }

    private fun interleave(rows: List<SourceRow>): List<Teaser> {
        // One queue per broadcaster (its rows concatenated in delivery order), served round robin.
        val queues = Broadcaster.entries.map { b -> rows.filter { it.broadcaster == b }.flatMap { it.items } }
            .filter { it.isNotEmpty() }
        val seen = HashSet<String>()
        val out = ArrayList<Teaser>()
        val max = queues.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) {
            for (q in queues) {
                val t = q.getOrNull(i) ?: continue
                if (seen.add(dedupeKey(t))) out.add(t)
            }
        }
        return out
    }

    /** Same ref, or same broadcaster + normalised title, is the same teaser. */
    internal fun dedupeKey(teaser: Teaser): String = when (teaser) {
        is Teaser.OfShow -> "show:" + teaser.show.broadcaster.name + ":" + normalizeText(teaser.show.title)
        is Teaser.OfEpisode -> "episode:" + teaser.episode.ref.source.name + ":" + teaser.episode.ref.key
    }
}
