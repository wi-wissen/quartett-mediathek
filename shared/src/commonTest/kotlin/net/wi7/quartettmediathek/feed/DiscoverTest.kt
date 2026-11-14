package net.wi7.quartettmediathek.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.wi7.quartettmediathek.catalog.CategoryMapper
import net.wi7.quartettmediathek.catalog.DiscoverAssembler
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.DiscoverCategory
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.Teaser

class DiscoverTest {
    @Test
    fun mapsRealRowTitles() {
        val expected = mapOf(
            "Unsere Top-Serien" to DiscoverCategory.SERIES,
            "Film-Empfehlungen" to DiscoverCategory.MOVIES,
            "Spannende Dokus und Reportagen" to DiscoverCategory.DOCUMENTARIES,
            "Unsere besten Dokuserien" to DiscoverCategory.DOCUMENTARIES,
            "Top-Serien zum Streamen" to DiscoverCategory.SERIES,
            "Packende Krimis" to DiscoverCategory.SERIES,
            "Dokus zum Vertiefen: Geschichte und Wissenschaft" to DiscoverCategory.DOCUMENTARIES,
            "Internationale Spielfilm-Highlights" to DiscoverCategory.MOVIES,
            "Wissen und Kultur" to DiscoverCategory.KNOWLEDGE_CULTURE,
            "Neu in der Mediathek" to DiscoverCategory.HIGHLIGHTS,
        )
        for ((title, category) in expected) assertEquals(category, CategoryMapper.categorize(title), title)
    }

    @Test
    fun skipsNewsLiveAndSport() {
        for (t in listOf("Nachrichten", "Livestreams", "Sport", "tagesschau", "Sportschau live", "Nachrichten und Politik")) {
            assertNull(CategoryMapper.categorize(t), t)
        }
    }

    private fun showTeaser(title: String, b: Broadcaster) = Teaser.OfShow(show(title, b, key = "${b.name}-$title"))

    @Test
    fun assemblesInCategoryOrderInterleavedAndDeduped() {
        val rows = listOf(
            SourceRow(Broadcaster.ARD, "Unsere Top-Serien", listOf(showTeaser("A1", Broadcaster.ARD), showTeaser("A2", Broadcaster.ARD), showTeaser("A3", Broadcaster.ARD))),
            SourceRow(Broadcaster.ARD, "Krimis", listOf(showTeaser("A1", Broadcaster.ARD))),
            SourceRow(Broadcaster.ZDF, "Top-Serien zum Streamen", listOf(showTeaser("Z1", Broadcaster.ZDF), showTeaser("Z2", Broadcaster.ZDF))),
            SourceRow(Broadcaster.ZDF, "Film-Tipps", listOf(showTeaser("Z3", Broadcaster.ZDF))),
            SourceRow(Broadcaster.ARTE, "Entdeckungen", listOf(showTeaser("T1", Broadcaster.ARTE))),
            SourceRow(Broadcaster.ARD, "Nachrichten", listOf(showTeaser("N", Broadcaster.ARD))),
        )
        val result = DiscoverAssembler.assemble(rows)
        assertEquals(
            listOf(DiscoverCategory.HIGHLIGHTS, DiscoverCategory.SERIES, DiscoverCategory.MOVIES),
            result.map { it.category },
        )
        val titles = result[1].items.map { (it as Teaser.OfShow).show.title }
        assertEquals(listOf("A1", "Z1", "A2", "Z2", "A3"), titles)
    }

    @Test
    fun broadcasterFilterAndEmptyCategories() {
        val rows = listOf(
            SourceRow(Broadcaster.ARD, "Unsere Top-Serien", listOf(showTeaser("A1", Broadcaster.ARD))),
            SourceRow(Broadcaster.ZDF, "Top-Serien", listOf(showTeaser("Z1", Broadcaster.ZDF))),
        )
        val zdfOnly = DiscoverAssembler.assemble(rows, setOf(Broadcaster.ZDF))
        assertEquals(1, zdfOnly.size)
        assertEquals(1, zdfOnly[0].items.size)
        assertEquals(emptyList(), DiscoverAssembler.assemble(rows, setOf(Broadcaster.ARTE)))
    }
}
