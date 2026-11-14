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
import net.wi7.quartettmediathek.library.ProgressIndex
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.Progress
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

data class HistoryUiState(val loaded: Boolean = false, val tiles: List<TileState> = emptyList())

/** Maps the history entries to tiles; the tile shows when the episode was played. Pure, unit tested. */
fun historyTiles(history: List<Progress>, overlay: LibraryOverlay): List<TileState> =
    history.map { overlay.decorate(it.episode).copy(playedAt = it.updatedAt) }

/** History ("Verlauf"): everything played, in progress or watched, most recent first ([LibraryRepository.history]). */
class HistoryViewModel(library: LibraryRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = combine(library.history(), library.overlay()) { history, overlay ->
        HistoryUiState(loaded = true, tiles = historyTiles(history, overlay))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@Composable
fun HistoryScreen() {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    HistoryContent(state)
}

@Composable
fun HistoryContent(state: HistoryUiState) {
    val gridRows = state.tiles.chunked(4)
    val rows = listOf(emptyList<String>()) + gridRows.map { r -> r.map { "h:${it.key}" } }
    val sf = rememberScreenFocus("history", if (state.loaded) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    ScreenColumn(listState, snapRows = false) {
        item(key = "header") {
            RowHeader(stringResource(R.string.nav_history), stringResource(R.string.history_count, state.tiles.size))
        }
        if (state.loaded && state.tiles.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.history_empty), style = QType.body.copy(fontSize = 14.sp), color = QColors.Muted,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp).width(380.dp),
                )
            }
        }
        gridRows.forEachIndexed { i, line ->
            item(key = "grid-$i-${line.first().key}") {
                Row(Modifier.padding(start = 24.dp, top = 11.dp, bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    line.forEach { tile -> TeaserTile(tile, Modifier.tracked(sf, "h:${tile.key}"), GridTileWidth, singleLine = true) }
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun HistoryPreview() {
    val state = remember {
        val sample = FakeData.sampleProgress()
        HistoryUiState(true, historyTiles(sample.sortedByDescending { it.updatedAt }, LibraryOverlay(progress = ProgressIndex(sample))))
    }
    PreviewHost { HistoryContent(state) }
}
