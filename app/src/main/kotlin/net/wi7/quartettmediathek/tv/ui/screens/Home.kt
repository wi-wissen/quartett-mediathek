package net.wi7.quartettmediathek.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.wi7.quartettmediathek.model.DiscoverCategory
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.components.Artwork
import net.wi7.quartettmediathek.tv.ui.components.Badge
import net.wi7.quartettmediathek.tv.ui.components.LocalTileActions
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QButton
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.components.TileSection
import net.wi7.quartettmediathek.tv.ui.components.tileSections
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.snapRowOnFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.formatClock
import net.wi7.quartettmediathek.tv.ui.minutesLeft
import net.wi7.quartettmediathek.tv.ui.overlay
import net.wi7.quartettmediathek.tv.ui.progressFraction
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.theme.color

data class ResumeInfo(val episode: Episode, val positionSeconds: Long)

data class DiscoverSectionData(val category: DiscoverCategory, val tiles: List<TileState>)

data class HomeUiState(
    val loaded: Boolean = false,
    val lastWatched: ResumeInfo? = null,
    val continueWatching: List<TileState> = emptyList(),
    val newEpisodes: List<TileState> = emptyList(),
    val bookmarks: List<TileState> = emptyList(),
    val discover: List<DiscoverSectionData> = emptyList(),
)

/** Home: last watched block, continue watching, new episodes from subscriptions, watchlist and Discover rows. */
class HomeViewModel(catalog: CatalogRepository, library: LibraryRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        catalog.discoverRows(), library.overlay(), library.continueWatching(), library.feed(), library.bookmarks(),
    ) { discover, overlay, progress, feed, bookmarks ->
        val unfinished = progress.filter { !it.watched && it.positionSeconds > 0 }
        val last = unfinished.firstOrNull()
        HomeUiState(
            loaded = true,
            lastWatched = last?.let { ResumeInfo(it.episode, it.positionSeconds) },
            continueWatching = unfinished.filter { it !== last }.map { overlay.decorate(it.episode) },
            newEpisodes = feed.filter { it.isNew }.map { overlay.decorate(it.episode) },
            bookmarks = bookmarks.map { overlay.decorate(it) },
            discover = discover.map { row -> DiscoverSectionData(row.category, row.items.map { overlay.decorate(it) }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

@Composable
fun DiscoverCategory.title(): String = stringResource(
    when (this) {
        DiscoverCategory.HIGHLIGHTS -> R.string.category_highlights
        DiscoverCategory.SERIES -> R.string.category_series
        DiscoverCategory.DOCUMENTARIES -> R.string.category_documentaries
        DiscoverCategory.KNOWLEDGE_CULTURE -> R.string.category_knowledge_culture
        DiscoverCategory.MOVIES -> R.string.category_movies
    },
)

@Composable
fun HomeScreen() {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state)
}

private const val KeyResume = "resume"
private const val KeyResumeShow = "resume-show"

@Composable
fun HomeContent(state: HomeUiState) {
    val sections = buildList {
        add(TileSection("continue", stringResource(R.string.row_continue), null, state.continueWatching))
        add(
            TileSection(
                "new", stringResource(R.string.row_new_from_subscriptions),
                if (state.newEpisodes.isEmpty()) null else stringResource(R.string.new_episodes_count, state.newEpisodes.size),
                state.newEpisodes,
            ),
        )
        add(TileSection("bookmarks", stringResource(R.string.row_bookmarks), null, state.bookmarks))
        state.discover.forEach { add(TileSection("discover-${it.category.name}", it.category.title(), null, it.tiles)) }
    }.filter { it.tiles.isNotEmpty() }
    val resume = state.lastWatched
    val rows = buildList {
        if (resume != null) add(listOf(KeyResume, KeyResumeShow))
        sections.forEach { add(it.focusKeys()) }
    }
    val sf = rememberScreenFocus("home", if (state.loaded) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    val offset = if (resume != null) 1 else 0
    ScreenColumn(listState) {
        if (resume != null) {
            item(key = "resume") { ResumeBlock(resume, sf, listState) }
        }
        tileSections(sections, offset, sf, listState)
    }
}

@Composable
private fun ResumeBlock(resume: ResumeInfo, sf: net.wi7.quartettmediathek.tv.ui.focus.ScreenFocus, listState: androidx.compose.foundation.lazy.LazyListState) {
    val actions = LocalTileActions.current
    val episode = resume.episode
    Row(
        Modifier.snapRowOnFocus(listState, 0).padding(start = 24.dp, top = 2.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(310.dp).height(174.dp).clip(RoundedCornerShape(9.dp))) {
            Artwork(episode.imageUrl, episode.showTitle, Modifier.matchParentSize(), seed = episode.ref.key.hashCode())
            Badge(
                episode.channelLabel, episode.broadcaster.color(), androidx.compose.ui.graphics.Color.White,
                Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        Column(Modifier.width(300.dp)) {
            Text(
                stringResource(R.string.row_last_watched).uppercase(),
                style = QType.kicker, color = QColors.Accent,
            )
            Text(
                episode.title,
                style = QType.heroTitle.copy(fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
                color = QColors.Foreground, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 5.dp),
            )
            val meta = buildList {
                add(episode.showTitle)
                episode.episodeNumber?.let { add(stringResource(R.string.episode_number, it)) }
                add(stringResource(R.string.minutes_left, minutesLeft(episode.durationSeconds, resume.positionSeconds)))
            }.joinToString(" · ")
            Text(meta, style = QType.facts, color = QColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.padding(top = 12.dp, bottom = 14.dp).width(260.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(QColors.Surface2),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(progressFraction(resume.positionSeconds, episode.durationSeconds))
                        .clip(RoundedCornerShape(2.dp)).background(QColors.Accent),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                QButton(
                    stringResource(R.string.action_resume_at, formatClock(resume.positionSeconds)),
                    onClick = { actions.play(episode.ref, null) },
                    modifier = Modifier.tracked(sf, KeyResume),
                    icon = R.drawable.ic_play_arrow_fill, primary = true,
                )
                val showRef = episode.showRef
                if (showRef != null) {
                    QButton(
                        stringResource(R.string.action_to_show),
                        onClick = { actions.openShow(showRef) },
                        modifier = Modifier.tracked(sf, KeyResumeShow),
                        icon = R.drawable.ic_tv,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun HomePreview() {
    val state = remember { previewHomeState() }
    PreviewHost { HomeContent(state) }
}

internal fun previewHomeState(): HomeUiState {
    val overlay = LibraryOverlay(progress = net.wi7.quartettmediathek.library.ProgressIndex(net.wi7.quartettmediathek.tv.data.fake.FakeData.sampleProgress()))
    val data = net.wi7.quartettmediathek.tv.data.fake.FakeData
    val ep = data.episode("abgruende-3")
    return HomeUiState(
        loaded = true,
        lastWatched = ResumeInfo(ep, 2210),
        continueWatching = listOf(data.episode("tiefsee-4"), data.episode("nachtzug-3")).map { overlay.decorate(it) },
        newEpisodes = listOf(data.episode("werkstatt-5"), data.episode("hafenrevier-5")).map { overlay.decorate(it) },
        bookmarks = data.initialBookmarks.map { overlay.decorate(it) },
        discover = data.discoverRows.map { r -> DiscoverSectionData(r.category, r.items.map { overlay.decorate(it) }) },
    )
}
