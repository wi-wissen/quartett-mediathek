package net.wi7.quartettmediathek.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.components.GridTileWidth
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.RowHeader
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.components.TeaserTile
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.overlay
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

data class WatchlistUiState(val loaded: Boolean = false, val tiles: List<TileState> = emptyList())

/** Watchlist: saved shows and episodes ([LibraryRepository.bookmarks]). */
class WatchlistViewModel(library: LibraryRepository) : ViewModel() {
    val state: StateFlow<WatchlistUiState> = combine(library.bookmarks(), library.overlay()) { bookmarks, overlay ->
        WatchlistUiState(loaded = true, tiles = bookmarks.map { overlay.decorate(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchlistUiState())
}

@Composable
fun WatchlistScreen() {
    val container = LocalAppContainer.current
    val vm: WatchlistViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    WatchlistContent(state)
}

@Composable
fun WatchlistContent(state: WatchlistUiState) {
    val gridRows = state.tiles.chunked(4)
    val rows = listOf(emptyList<String>()) + gridRows.map { r -> r.map { "w:${it.key}" } }
    val sf = rememberScreenFocus("watchlist", if (state.loaded) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    ScreenColumn(listState, snapRows = false) {
        item(key = "header") {
            RowHeader(stringResource(R.string.nav_bookmarks), stringResource(R.string.watchlist_count, state.tiles.size))
        }
        if (state.loaded && state.tiles.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.watchlist_empty), style = QType.body.copy(fontSize = 14.sp), color = QColors.Muted,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp).width(380.dp),
                )
            }
        }
        gridRows.forEachIndexed { i, line ->
            item(key = "grid-$i-${line.first().key}") {
                Row(Modifier.padding(start = 24.dp, top = 11.dp, bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    line.forEach { tile -> TeaserTile(tile, Modifier.tracked(sf, "w:${tile.key}"), GridTileWidth, singleLine = true) }
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun WatchlistPreview() {
    val overlay = remember { LibraryOverlay() }
    val state = remember { WatchlistUiState(true, FakeData.initialBookmarks.map { overlay.decorate(it) }) }
    PreviewHost { WatchlistContent(state) }
}
