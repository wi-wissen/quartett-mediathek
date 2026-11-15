package net.wi7.quartettmediathek.tv.ui.screens

import net.wi7.quartettmediathek.feed.EpisodeOrder
import net.wi7.quartettmediathek.tv.ui.episodeLabel
import net.wi7.quartettmediathek.library.LibraryKeys
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.components.ContentRow
import net.wi7.quartettmediathek.tv.ui.components.Hero
import net.wi7.quartettmediathek.tv.ui.components.HeroKicker
import net.wi7.quartettmediathek.tv.ui.components.LocalTileActions
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QButton
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.components.TeaserTile
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.snapRowOnFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.formatClock
import net.wi7.quartettmediathek.tv.ui.key
import net.wi7.quartettmediathek.tv.ui.menu.SubscriptionDialog
import net.wi7.quartettmediathek.tv.ui.overlay
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.components.LoadError
import net.wi7.quartettmediathek.tv.ui.theme.QType

data class ShowUiState(
    val loaded: Boolean = false,
    val show: Show? = null,
    val failed: Boolean = false,
    val episodes: List<TileState> = emptyList(),
    val subscription: Subscription? = null,
    val bookmarked: Boolean = false,
    /** Episode the primary button plays: in progress, else newest unwatched, else newest. */
    val resumeEpisode: Episode? = null,
    val resumePosition: Long? = null,
    val moreFromBroadcaster: List<TileState> = emptyList(),
    val error: String? = null,
)

/** Show page: [CatalogRepository.show] / [CatalogRepository.episodes] plus subscription, bookmark and progress state. */
class ShowViewModel(
    private val ref: ShowRef,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private class Loaded(val show: Show?, val episodes: List<Episode>, val more: List<Teaser>, val failed: Boolean, val error: String? = null)

    private val loaded = MutableStateFlow<Loaded?>(null)

    init { load() }

    fun retry() = load()

    private fun load() {
        loaded.value = null
        viewModelScope.launch {
            loaded.value = try {
                val show = catalog.show(ref)
                val episodes = catalog.episodes(ref)
                val more = catalog.discoverRows().first().flatMap { it.items }
                    .filterIsInstance<Teaser.OfShow>().filter { it.show.broadcaster == show.broadcaster && it.show.ref != ref }
                    .distinctBy { it.key() }
                Loaded(show, episodes, more, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(null, emptyList(), emptyList(), true, e.message ?: e::class.simpleName)
            }
        }
    }

    val state: StateFlow<ShowUiState> = combine(
        loaded, library.overlay(), library.subscriptionFor(ref), library.isBookmarked(LibraryKeys.bookmark(ref)),
    ) { l, overlay, subscription, bookmarked ->
        if (l == null) ShowUiState() else {
            val tiles = l.episodes.map { overlay.decorate(it) }
            val watched = tiles.filter { it.watched }.mapNotNull { (it.teaser as? Teaser.OfEpisode)?.episode?.ref }.toSet()
            val next = EpisodeOrder.nextUp(l.episodes) { it.ref in watched }
            val resume = tiles.firstOrNull { it.progressSeconds != null }
                ?: tiles.firstOrNull { (it.teaser as? Teaser.OfEpisode)?.episode?.ref == next?.ref } ?: tiles.firstOrNull()
            ShowUiState(
                loaded = true, show = l.show, failed = l.failed, error = l.error, episodes = tiles, subscription = subscription, bookmarked = bookmarked,
                resumeEpisode = (resume?.teaser as? Teaser.OfEpisode)?.episode, resumePosition = resume?.progressSeconds,
                moreFromBroadcaster = l.more.map { overlay.decorate(it) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShowUiState())

    fun toggleSubscribe() {
        val s = state.value
        val show = s.show ?: return
        viewModelScope.launch {
            val sub = s.subscription
            if (sub != null) library.unsubscribe(sub.id) else library.subscribe(show)
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch { library.updateSubscription(subscription) }
    }

    fun toggleBookmark() {
        val show = state.value.show ?: return
        viewModelScope.launch { library.toggleBookmark(Teaser.OfShow(show)) }
    }
}

@Composable
fun ShowScreen(ref: ShowRef) {
    val container = LocalAppContainer.current
    val vm: ShowViewModel = viewModel(key = "show-${ref.source}-${ref.key}", factory = ViewModelFactory(container, showRef = ref))
    val state by vm.state.collectAsStateWithLifecycle()
    ShowContent(state, "show-${ref.source}-${ref.key}", vm::toggleSubscribe, vm::toggleBookmark, vm::updateSubscription, onRetry = vm::retry)
}

private const val KeyPlay = "hero:play"
private const val KeySubscribe = "hero:subscribe"
private const val KeyAdjust = "hero:adjust"
private const val KeyBookmark = "hero:bookmark"

@Composable
fun ShowContent(
    state: ShowUiState,
    focusId: String,
    onToggleSubscribe: () -> Unit,
    onToggleBookmark: () -> Unit,
    onUpdateSubscription: (Subscription) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    onRetry: () -> Unit = {},
) {
    val show = state.show
    val actions = LocalTileActions.current
    var adjusting by remember { mutableStateOf(false) }
    val heroKeys = listOf(KeyPlay, KeySubscribe) + (if (state.subscription != null) listOf(KeyAdjust) else emptyList()) + KeyBookmark
    val rows = listOf(heroKeys, state.episodes.map { "episodes:${it.key}" }, state.moreFromBroadcaster.map { "more:${it.key}" })
    val sf = rememberScreenFocus(focusId, if (state.loaded && show != null) rows else emptyList())
    InitialFocusEffect(sf, listState)
    if (state.loaded && show == null) {
        LoadError(state.error, onRetry)
        return
    }
    if (show == null) return
    ScreenColumn(listState, topPadding = 0.dp) {
        item(key = "hero") {
            Hero(280.dp, show.imageUrl, show.title, seed = 3, modifier = Modifier.snapRowOnFocus(listState, 0)) {
                val meta = listOfNotNull(
                    show.channelLabel, show.genre,
                    show.episodeCount?.takeIf { it > 1 }?.let { stringResource(R.string.episode_count, it) },
                ).joinToString(" · ")
                HeroKicker(show.broadcaster, meta)
                Text(
                    show.title, style = QType.heroTitle, color = QColors.Foreground, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp, bottom = 9.dp),
                )
                show.description?.let { description ->
                    Text(
                        description, style = QType.body.copy(fontSize = 13.sp), color = androidx.compose.ui.graphics.Color(0xFFCFD7DC),
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 15.dp),
                    )
                }
                // Buttons never wrap or truncate: the row may grow past the hero's text width.
                Row(Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    val resume = state.resumeEpisode
                    if (resume != null) {
                        val label = when {
                            resume.episodeNumber == null -> stringResource(R.string.action_play_duration, formatClock(resume.durationSeconds.toLong()))
                            state.resumePosition != null -> stringResource(R.string.action_continue_episode, episodeLabel(resume)!!)
                            else -> stringResource(R.string.action_play_episode, episodeLabel(resume)!!)
                        }
                        QButton(label, onClick = { actions.play(resume.ref, null) }, modifier = Modifier.tracked(sf, KeyPlay), icon = R.drawable.ic_play_arrow_fill, primary = true)
                    }
                    val subscribed = state.subscription != null
                    QButton(
                        stringResource(if (subscribed) R.string.action_subscribed else R.string.action_subscribe),
                        onClick = onToggleSubscribe, modifier = Modifier.tracked(sf, KeySubscribe),
                        icon = if (subscribed) R.drawable.ic_check else R.drawable.ic_notifications,
                    )
                    if (subscribed) {
                        QButton(
                            stringResource(R.string.action_adjust_subscription), onClick = { adjusting = true },
                            modifier = Modifier.tracked(sf, KeyAdjust), icon = R.drawable.ic_tune,
                        )
                    }
                    QButton(
                        stringResource(if (state.bookmarked) R.string.action_bookmarked else R.string.action_bookmark),
                        onClick = onToggleBookmark, modifier = Modifier.tracked(sf, KeyBookmark),
                        icon = if (state.bookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
                    )
                }
            }
        }
        if (state.episodes.isNotEmpty()) {
            item(key = "episodes") {
                ContentRow(1, sf, listState, stringResource(R.string.row_episodes), state.episodes, { "episodes:${it.key}" }, small = stringResource(R.string.newest_first)) { t, m ->
                    TeaserTile(t, m)
                }
            }
        }
        if (state.moreFromBroadcaster.isNotEmpty()) {
            item(key = "more") {
                ContentRow(2, sf, listState, stringResource(R.string.row_more_from, show.broadcaster.displayName), state.moreFromBroadcaster, { "more:${it.key}" }) { t, m ->
                    TeaserTile(t, m)
                }
            }
        }
    }
    val sub = state.subscription
    if (adjusting && sub != null) {
        SubscriptionDialog(sub, onChange = onUpdateSubscription, onDismiss = { adjusting = false })
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ShowPreview() {
    val overlay = remember { LibraryOverlay() }
    val state = remember {
        val eps = FakeData.episodesByShow.getValue("hafenrevier")
        ShowUiState(
            loaded = true, show = FakeData.show("hafenrevier"), episodes = eps.map { overlay.decorate(it) },
            subscription = null, resumeEpisode = eps.first(),
            moreFromBroadcaster = FakeData.shows.filter { it.broadcaster.name == "ARD" }.map { overlay.decorate(it) },
        )
    }
    PreviewHost { ShowContent(state, "preview", {}, {}, {}) }
}
