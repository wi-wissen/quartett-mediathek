package net.wi7.quartettmediathek.catalog

import net.wi7.quartettmediathek.feed.normalizeText
import net.wi7.quartettmediathek.model.DiscoverCategory
import net.wi7.quartettmediathek.model.SourceRow

/** Maps the editorial row titles of the broadcasters to the unified Discover categories. */
object CategoryMapper {
    /** Row titles starting with one of these words are skipped (news, live, sport ...). Matched per word. */
    private val skipWordPrefixes = listOf(
        "nachricht", "livestream", "live", "sport", "tagesschau", "tagesthemen", "heute", "news", "wetter", "menue",
    )

    /**
     * Keyword table, checked in order, first hit wins. Documentary words come before series ("Dokuserien" is a
     * documentary row) and before knowledge ("Dokus zum Vertiefen: Geschichte und Wissenschaft").
     */
    private val keywords: List<Pair<DiscoverCategory, List<String>>> = listOf(
        DiscoverCategory.DOCUMENTARIES to listOf("doku", "reportage", "dokumentation"),
        DiscoverCategory.MOVIES to listOf("film", "kino"),
        DiscoverCategory.SERIES to listOf("serie", "krimi", "staffel"),
        DiscoverCategory.KNOWLEDGE_CULTURE to
            listOf("wissen", "kultur", "geschichte", "wissenschaft", "natur", "musik", "kunst", "reise"),
    )

    private val wordSplit = Regex("[^a-z0-9]+")

    /** True for rows that are not part of Discover (news, live streams, sport). */
    fun isSkipped(rowTitle: String): Boolean {
        val words = normalizeText(rowTitle).split(wordSplit).filter { it.isNotEmpty() }
        return words.any { w -> skipWordPrefixes.any { w.startsWith(it) } }
    }

    /** The category of a row title, or null if the row should be skipped. Unknown rows become HIGHLIGHTS. */
    fun categorize(rowTitle: String): DiscoverCategory? {
        if (isSkipped(rowTitle)) return null
        val text = normalizeText(rowTitle)
        return keywords.firstOrNull { (_, words) -> words.any { it in text } }?.first ?: DiscoverCategory.HIGHLIGHTS
    }

    fun categorize(row: SourceRow): DiscoverCategory? = categorize(row.title)
}
