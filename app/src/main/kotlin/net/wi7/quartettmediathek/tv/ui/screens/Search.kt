package net.wi7.quartettmediathek.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.components.GridTileWidth
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QIcon
import net.wi7.quartettmediathek.tv.ui.components.TeaserTile
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.key
import net.wi7.quartettmediathek.tv.ui.overlay
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<TileState> = emptyList(),
    /** True while the query is blank: [results] then show all shows from Discover. */
    val browsing: Boolean = true,
)

/** Search: on-screen keyboard edits [SearchViewModel.state]'s query, results come from [CatalogRepository.search]. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val catalog: CatalogRepository, library: LibraryRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    private data class Found(val query: String, val teasers: List<Teaser>)

    private val found = query.debounce(300).distinctUntilChanged().mapLatest { q ->
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            Found(trimmed, emptyList())
        } else {
            try {
                Found(trimmed, catalog.search(trimmed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Found(trimmed, emptyList())
            }
        }
    }

    val state: StateFlow<SearchUiState> = combine(query, found, library.overlay(), catalog.discoverRows()) { q, f, overlay, rows ->
        val browsing = q.isBlank()
        val teasers = if (browsing) {
            rows.flatMap { it.items }.filterIsInstance<Teaser.OfShow>().distinctBy { it.key() }
        } else f.teasers
        SearchUiState(
            query = q,
            searching = !browsing && f.query != q.trim(),
            results = teasers.map { overlay.decorate(it) },
            browsing = browsing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun type(text: String) { query.value = (query.value + text).take(60) }
    fun backspace() { query.value = query.value.dropLast(1) }
}

@Composable
fun SearchScreen() {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    SearchContent(state, onType = vm::type, onBackspace = vm::backspace)
}

private val letters = "abcdefghijklmnopqrstuvwxyzäöüß".map { it.toString() }
private const val KeySpace = "key:space"
private const val KeyDelete = "key:delete"
private val KeySize = 35.5.dp
private val KeyGap = 5.dp

@Composable
fun SearchContent(state: SearchUiState, onType: (String) -> Unit, onBackspace: () -> Unit) {
    val keyRows = letters.chunked(7).let { chunks ->
        // Last row of letters is filled up with space (3 columns) and delete (2 columns).
        chunks.dropLast(1).map { r -> r.map { KbKey(it, 1) } } +
            listOf(chunks.last().map { KbKey(it, 1) } + listOf(KbKey(KeySpace, 3), KbKey(KeyDelete, 7 - chunks.last().size - 3)))
    }
    val sf = rememberScreenFocus("search", listOf(keyRows.flatten().map { "key:${it.id}" }))
    InitialFocusEffect(sf, null)
    Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 60.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(280.dp)) {
            QueryBox(state.query)
            Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                keyRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                        row.forEach { k ->
                            val label = when (k.id) {
                                KeySpace -> stringResource(R.string.key_space)
                                KeyDelete -> stringResource(R.string.key_delete)
                                else -> k.id
                            }
                            KeyboardKey(
                                label = label, span = k.span, modifier = Modifier.tracked(sf, "key:${k.id}"),
                                onClick = {
                                    when (k.id) {
                                        KeySpace -> onType(" ")
                                        KeyDelete -> onBackspace()
                                        else -> onType(k.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.search_hint), style = QType.menuSmall.copy(fontSize = 10.5.sp), color = QColors.Muted,
                modifier = Modifier.padding(top = 13.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            val title = when {
                state.browsing -> stringResource(R.string.search_all_shows)
                else -> stringResource(R.string.search_results_for, state.query.trim())
            }
            Text(title, style = QType.rowTitle, color = QColors.Foreground, maxLines = 1, modifier = Modifier.padding(top = 3.dp, bottom = 11.dp))
            when {
                state.results.isEmpty() && state.searching -> Text(stringResource(R.string.search_searching), style = QType.body, color = QColors.Muted)
                state.results.isEmpty() -> Text(stringResource(R.string.search_empty), style = QType.body.copy(fontSize = 14.sp), color = QColors.Muted)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.results, key = { it.key }) { tile ->
                        TeaserTile(tile, Modifier.tracked(sf, "res:${tile.key}"), width = SearchTileWidth, singleLine = true)
                    }
                }
            }
        }
    }
}

private val SearchTileWidth = 176.dp

private data class KbKey(val id: String, val span: Int)

@Composable
private fun QueryBox(query: String) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(7.dp)).background(QColors.Surface).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QIcon(R.drawable.ic_search, QColors.Muted, size = 17.dp)
        Text(query, style = QType.menuTitle.copy(fontSize = 16.sp), color = QColors.Foreground, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
        val blink = rememberInfiniteTransition(label = "cursor")
        val a by blink.animateFloat(
            1f, 0f,
            infiniteRepeatable(keyframes { durationMillis = 1000; 1f at 0; 1f at 499; 0f at 500; 0f at 999 }, RepeatMode.Restart),
            label = "cursorAlpha",
        )
        Box(Modifier.alpha(a).width(1.5.dp).height(20.dp).background(QColors.Accent))
    }
}

@Composable
private fun KeyboardKey(label: String, span: Int, modifier: Modifier, onClick: () -> Unit) {
    val width: Dp = KeySize * span + KeyGap * (span - 1)
    FocusBox(modifier = modifier.width(width).height(KeySize), onClick = onClick, contentAlignment = Alignment.Center) { focused ->
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)).background(if (focused) QColors.Focus else QColors.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = if (span > 1) QType.key.copy(fontSize = 11.sp) else QType.key,
                color = if (focused) QColors.Background else QColors.Foreground, maxLines = 1, softWrap = false,
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun SearchPreview() {
    val overlay = remember { LibraryOverlay() }
    val state = remember { SearchUiState("nacht", false, FakeData.shows.take(6).map { overlay.decorate(it) }, false) }
    PreviewHost { SearchContent(state, {}, {}) }
}
