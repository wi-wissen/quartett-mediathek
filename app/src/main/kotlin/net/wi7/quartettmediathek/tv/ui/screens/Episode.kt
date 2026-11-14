package net.wi7.quartettmediathek.tv.ui.screens

import net.wi7.quartettmediathek.library.LibraryKeys
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.Progress
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.components.ContentRow
import net.wi7.quartettmediathek.tv.ui.components.FactBadge
import net.wi7.quartettmediathek.tv.ui.components.Hero
import net.wi7.quartettmediathek.tv.ui.components.HeroKicker
import net.wi7.quartettmediathek.tv.ui.components.LocalTileActions
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QButton
import net.wi7.quartettmediathek.tv.ui.components.QIcon
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.components.TeaserTile
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.snapRowOnFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.formatDate
import net.wi7.quartettmediathek.tv.ui.minutesLeft
import net.wi7.quartettmediathek.tv.ui.overlay
import net.wi7.quartettmediathek.tv.ui.progressFraction
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import java.time.format.FormatStyle

data class EpisodeUiState(
    val loaded: Boolean = false,
    val episode: Episode? = null,
    val failed: Boolean = false,
    val progress: Progress? = null,
    val bookmarked: Boolean = false,
    val moreEpisodes: List<TileState> = emptyList(),
)

/** Episode page: [CatalogRepository.episode] plus progress, bookmark state and the other episodes of the show. */
class EpisodeViewModel(
    private val ref: EpisodeRef,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private class Loaded(val episode: Episode?, val others: List<Episode>)

    private val loaded = MutableStateFlow<Loaded?>(null)

    init {
        viewModelScope.launch {
            loaded.value = try {
                val episode = catalog.episode(ref)
                val others = episode.showRef?.let { catalog.episodes(it) }.orEmpty().filter { it.ref != ref }
                Loaded(episode, others)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(null, emptyList())
            }
        }
    }

    val state: StateFlow<EpisodeUiState> = combine(
        loaded, library.overlay(), library.progress(ref), library.isBookmarked(LibraryKeys.bookmark(ref)),
    ) { l, overlay, progress, bookmarked ->
        if (l == null) EpisodeUiState() else EpisodeUiState(
            loaded = true, episode = l.episode, failed = l.episode == null, progress = progress, bookmarked = bookmarked,
            moreEpisodes = l.others.map { overlay.decorate(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeUiState())

    fun toggleBookmark() {
        val episode = state.value.episode ?: return
        viewModelScope.launch { library.toggleBookmark(Teaser.OfEpisode(episode)) }
    }
}

@Composable
fun EpisodeScreen(ref: EpisodeRef) {
    val container = LocalAppContainer.current
    val vm: EpisodeViewModel = viewModel(key = "episode-${ref.source}-${ref.key}", factory = ViewModelFactory(container, episodeRef = ref))
    val state by vm.state.collectAsStateWithLifecycle()
    EpisodeContent(state, "episode-${ref.source}-${ref.key}", vm::toggleBookmark)
}

private const val KeyPlay = "hero:play"
private const val KeyFromStart = "hero:from-start"
private const val KeyBookmark = "hero:bookmark"
private const val KeyShow = "hero:show"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeContent(state: EpisodeUiState, focusId: String, onToggleBookmark: () -> Unit) {
    val episode = state.episode
    val actions = LocalTileActions.current
    val progress = state.progress
    val position = progress?.takeIf { !it.watched && it.positionSeconds > 0 }?.positionSeconds
    val watched = progress?.watched == true
    val heroKeys = buildList {
        add(KeyPlay)
        if (position != null || watched) add(KeyFromStart)
        add(KeyBookmark)
        if (episode?.showRef != null) add(KeyShow)
    }
    val rows = listOf(heroKeys, state.moreEpisodes.map { "more:${it.key}" })
    val sf = rememberScreenFocus(focusId, if (state.loaded && episode != null) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    if (state.loaded && episode == null) {
        Text(stringResource(R.string.error_load), style = QType.body, color = QColors.Muted, modifier = Modifier.padding(start = 24.dp, top = 80.dp))
        return
    }
    if (episode == null) return
    ScreenColumn(listState, topPadding = 0.dp) {
        item(key = "hero") {
            Hero(390.dp, episode.imageUrl, episode.showTitle, seed = episode.ref.key.hashCode(), modifier = Modifier.snapRowOnFocus(listState, 0), contentWidth = 520.dp) {
                val kicker = listOfNotNull(
                    episode.channelLabel, episode.showTitle,
                    episode.episodeNumber?.let { stringResource(R.string.episode_number, it) },
                ).joinToString(" · ")
                HeroKicker(episode.broadcaster, kicker)
                Text(
                    episode.title, style = QType.episodeHeroTitle, color = QColors.Foreground, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp, bottom = 9.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(bottom = 11.dp),
                ) {
                    val factColor = Color(0xFFCFD7DC)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QIcon(R.drawable.ic_schedule, factColor, size = 14.dp)
                        Text(stringResource(R.string.duration_minutes, (episode.durationSeconds + 30) / 60), style = QType.facts, color = factColor)
                    }
                    Text(formatDate(episode.publishedAt, FormatStyle.LONG), style = QType.facts, color = factColor)
                    episode.availableUntil?.let {
                        Text(stringResource(R.string.available_until, formatDate(it)), style = QType.facts, color = factColor)
                    }
                    if ((episode.maxHeight ?: 0) >= 720) FactBadge(stringResource(R.string.badge_hd))
                    if (episode.hasSubtitles) FactBadge(stringResource(R.string.badge_subtitles))
                    if (Variant.AUDIO_DESCRIPTION in episode.variants) FactBadge(icon = R.drawable.ic_audio_description)
                    if (Variant.SIGN_LANGUAGE in episode.variants) FactBadge(icon = R.drawable.ic_sign_language)
                    if (watched) FactBadge(stringResource(R.string.badge_seen_lower))
                }
                episode.description?.let {
                    Text(it, style = QType.body, color = factColorLight, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (position != null) {
                    Box(Modifier.width(280.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(progressFraction(position, episode.durationSeconds))
                                .clip(RoundedCornerShape(2.dp)).background(QColors.Accent),
                        )
                    }
                    Text(
                        stringResource(R.string.minutes_left, minutesLeft(episode.durationSeconds, position)),
                        style = QType.rowSmall.copy(fontSize = 10.5.sp), color = QColors.Muted, modifier = Modifier.padding(top = 5.dp, bottom = 13.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QButton(
                        text = if (position != null) stringResource(R.string.action_resume_at, net.wi7.quartettmediathek.tv.ui.formatClock(position)) else stringResource(R.string.action_play),
                        onClick = { actions.play(episode.ref, null) }, modifier = Modifier.tracked(sf, KeyPlay),
                        icon = R.drawable.ic_play_arrow_fill, primary = true,
                    )
                    if (position != null || watched) {
                        QButton(stringResource(R.string.action_from_start), onClick = { actions.play(episode.ref, 0) }, modifier = Modifier.tracked(sf, KeyFromStart), icon = R.drawable.ic_replay)
                    }
                    QButton(
                        stringResource(if (state.bookmarked) R.string.action_bookmarked else R.string.action_bookmark),
                        onClick = onToggleBookmark, modifier = Modifier.tracked(sf, KeyBookmark),
                        icon = if (state.bookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
                    )
                    episode.showRef?.let { showRef ->
                        QButton(stringResource(R.string.action_to_show), onClick = { actions.openShow(showRef) }, modifier = Modifier.tracked(sf, KeyShow), icon = R.drawable.ic_tv)
                    }
                }
            }
        }
        if (state.moreEpisodes.isNotEmpty()) {
            item(key = "more") {
                ContentRow(1, sf, listState, stringResource(R.string.row_more_episodes), state.moreEpisodes, { "more:${it.key}" }, small = episode.showTitle) { t, m ->
                    TeaserTile(t, m)
                }
            }
        }
    }
}

private val factColorLight = Color(0xFFCFD7DC)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun EpisodePreview() {
    val overlay = remember { LibraryOverlay() }
    val state = remember {
        val ep = FakeData.episode("abgruende-3")
        EpisodeUiState(
            true, ep, false, Progress(ep, 2210, false, FakeData.now), true,
            FakeData.episodesByShow.getValue("abgruende").filter { it.ref != ep.ref }.map { overlay.decorate(it) },
        )
    }
    PreviewHost { EpisodeContent(state, "preview") {} }
}
