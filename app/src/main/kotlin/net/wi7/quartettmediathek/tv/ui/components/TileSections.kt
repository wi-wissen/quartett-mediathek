package net.wi7.quartettmediathek.tv.ui.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.focus.ScreenFocus

/** A titled row of tiles. [id] prefixes the focus keys so the same teaser may appear in several rows. */
@Immutable
data class TileSection(val id: String, val title: String, val small: String?, val tiles: List<TileState>) {
    fun focusKeys(): List<String> = tiles.map { "$id:${it.key}" }
}

/** Adds one lazy item per non-empty section. Row indexes start at [firstRowIndex]. */
fun LazyListScope.tileSections(
    sections: List<TileSection>,
    firstRowIndex: Int,
    sf: ScreenFocus,
    listState: LazyListState,
) {
    sections.forEachIndexed { i, section ->
        item(key = "section-${section.id}") {
            ContentRow(
                rowIndex = firstRowIndex + i,
                sf = sf,
                listState = listState,
                title = section.title,
                small = section.small,
                items = section.tiles,
                keyOf = { "${section.id}:${it.key}" },
            ) { tile, mod -> TeaserTile(tile, mod) }
        }
    }
}
