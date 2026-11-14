package net.wi7.quartettmediathek.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.broadcaster
import net.wi7.quartettmediathek.tv.ui.components.HorizontalItems
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QChip
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.components.TileSection
import net.wi7.quartettmediathek.tv.ui.components.tileSections
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.snapRowOnFocus
import net.wi7.quartettmediathek.tv.ui.overlay

data class DiscoverUiState(
    val loaded: Boolean = false,
    /** null = all broadcasters. */
    val filter: Broadcaster? = null,
    val sections: List<DiscoverSectionData> = emptyList(),
)

/** Discover: broadcaster filter plus the unified category rows from [CatalogRepository.discoverRows]. */
class DiscoverViewModel(catalog: CatalogRepository, library: LibraryRepository) : ViewModel() {
    private val filter = MutableStateFlow<Broadcaster?>(null)

    val state: StateFlow<DiscoverUiState> = combine(catalog.discoverRows(), library.overlay(), filter) { rows, overlay, f ->
        DiscoverUiState(
            loaded = true,
            filter = f,
            sections = rows.map { row ->
                DiscoverSectionData(row.category, row.items.filter { f == null || it.broadcaster() == f }.map { overlay.decorate(it) })
            }.filter { it.tiles.isNotEmpty() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState(filter = filter.value))

    fun select(broadcaster: Broadcaster?) { filter.value = broadcaster }
}

@Composable
fun DiscoverScreen() {
    val container = LocalAppContainer.current
    val vm: DiscoverViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    DiscoverContent(state, onSelect = vm::select)
}

private fun chipKey(b: Broadcaster?) = "chip:${b?.name ?: "ALL"}"

@Composable
fun DiscoverContent(state: DiscoverUiState, onSelect: (Broadcaster?) -> Unit) {
    val chips = listOf<Broadcaster?>(null) + Broadcaster.entries
    val sections = state.sections.map { s ->
        val names = s.tiles.map { it.teaser.broadcaster() }.distinct().joinToString(", ") { it.displayName }
        TileSection("discover-${s.category.name}", s.category.title(), names, s.tiles)
    }
    val rows = listOf(chips.map { chipKey(it) }) + sections.map { it.focusKeys() }
    val sf = rememberScreenFocus("discover", if (state.loaded) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    ScreenColumn(listState) {
        item(key = "chips") {
            Column(Modifier.snapRowOnFocus(listState, 0)) {
                HorizontalItems(
                    rowIndex = 0, sf = sf, items = chips, keyOf = { chipKey(it) },
                    contentPadding = PaddingValues(start = 24.dp, end = 40.dp, top = 8.dp, bottom = 8.dp), spacing = 8.dp,
                ) { b, mod ->
                    QChip(
                        text = b?.displayName ?: stringResource(R.string.filter_all_broadcasters),
                        selected = state.filter == b, onClick = { onSelect(b) }, modifier = mod,
                    )
                }
            }
        }
        tileSections(sections, 1, sf, listState)
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun DiscoverPreview() {
    val overlay = remember { LibraryOverlay() }
    val state = remember {
        DiscoverUiState(true, null, FakeData.discoverRows.map { r -> DiscoverSectionData(r.category, r.items.map { overlay.decorate(it) }) })
    }
    PreviewHost { DiscoverContent(state) {} }
}
