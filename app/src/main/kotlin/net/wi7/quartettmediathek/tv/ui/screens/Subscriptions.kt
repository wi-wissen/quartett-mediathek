package net.wi7.quartettmediathek.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.LibraryOverlay
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.components.Artwork
import net.wi7.quartettmediathek.tv.ui.components.GridTileWidth
import net.wi7.quartettmediathek.tv.ui.components.HorizontalItems
import net.wi7.quartettmediathek.tv.ui.components.LocalTileActions
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QButton
import net.wi7.quartettmediathek.tv.ui.components.RowHeader
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.components.TeaserTile
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.snapRowOnFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.overlay
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

data class AvatarState(val id: String, val title: String, val imageUrl: String?, val hasNew: Boolean, val show: Show?)

data class SubscriptionsUiState(
    val loaded: Boolean = false,
    val avatars: List<AvatarState> = emptyList(),
    /** Subscription id the feed is filtered by, null = all. */
    val filter: String? = null,
    val tiles: List<TileState> = emptyList(),
)

/** Subscriptions: avatars with new-dot (select = filter) and the feed grid, newest first. */
class SubscriptionsViewModel(library: LibraryRepository) : ViewModel() {
    private val filter = MutableStateFlow<String?>(null)

    val state: StateFlow<SubscriptionsUiState> = combine(library.subscriptions(), library.feed(), library.overlay(), filter) { subs, feed, overlay, f ->
        val active = f?.takeIf { id -> subs.any { it.id == id } }
        SubscriptionsUiState(
            loaded = true,
            filter = active,
            avatars = subs.map { sub ->
                val episodes = feed.filter { it.subscriptionId == sub.id }
                val latest = episodes.firstOrNull()?.episode
                AvatarState(
                    id = sub.id, title = sub.title, imageUrl = latest?.imageUrl, hasNew = episodes.any { it.isNew },
                    show = latest?.showRef?.let { Show(it, sub.title, latest.broadcaster, latest.channelLabel, imageUrl = latest.imageUrl) },
                )
            },
            tiles = feed.filter { active == null || it.subscriptionId == active }.map { overlay.decorate(it.episode).copy(isNew = it.isNew) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())

    fun select(subscriptionId: String?) { filter.value = subscriptionId }
}

@Composable
fun SubscriptionsScreen() {
    val container = LocalAppContainer.current
    val vm: SubscriptionsViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    SubscriptionsContent(state, onSelect = vm::select)
}

private const val AllAvatar = "av:all"
private const val GridColumns = 4
private const val KeyToShow = "btn:show"

@Composable
fun SubscriptionsContent(state: SubscriptionsUiState, onSelect: (String?) -> Unit) {
    val avatarKeys = listOf(AllAvatar) + state.avatars.map { "av:${it.id}" }
    val gridRows = state.tiles.chunked(GridColumns)
    val selectedShow = state.avatars.firstOrNull { it.id == state.filter }
    // Row 0 = avatars, row 1 = grid header (focusable only for the "Go to show" button), then one row per grid line.
    val headerKeys = if (selectedShow?.show != null) listOf(KeyToShow) else emptyList()
    val rows = listOf(avatarKeys, headerKeys) + gridRows.map { r -> r.map { "g:${it.key}" } }
    val sf = rememberScreenFocus("subscriptions", if (state.loaded) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    val actions = LocalTileActions.current
    ScreenColumn(listState, snapRows = false) {
        item(key = "avatars") {
            Column(Modifier.snapRowOnFocus(listState, 0)) {
                HorizontalItems(
                    rowIndex = 0, sf = sf, items = listOf<AvatarState?>(null) + state.avatars, keyOf = { "av:${it?.id ?: "all"}" },
                    contentPadding = PaddingValues(start = 24.dp, end = 40.dp, top = 9.dp, bottom = 13.dp), spacing = 17.dp,
                ) { avatar, mod ->
                    Avatar(
                        avatar = avatar, selected = state.filter == avatar?.id, modifier = mod,
                        onClick = { onSelect(avatar?.id) },
                        onLongClick = avatar?.show?.let { show -> { actions.openMenu(Teaser.OfShow(show)) } },
                    )
                }
            }
        }
        item(key = "header") {
            val title = selectedShow?.title ?: stringResource(R.string.subscriptions_all_episodes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowHeader(title, stringResource(R.string.subscriptions_newest_first), Modifier.weight(1f, fill = false))
                selectedShow?.show?.let { show ->
                    QButton(
                        stringResource(R.string.action_to_show), onClick = { actions.openShow(show.ref) },
                        modifier = Modifier.padding(start = 16.dp).tracked(sf, KeyToShow), icon = R.drawable.ic_tv,
                    )
                }
            }
        }
        if (state.avatars.isEmpty() && state.loaded) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.subscriptions_empty), style = QType.body.copy(fontSize = 14.sp), color = QColors.Muted,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp).width(380.dp),
                )
            }
        }
        gridRows.forEachIndexed { i, line ->
            item(key = "grid-$i-${line.first().key}") {
                Row(
                    Modifier.padding(start = 24.dp, top = 11.dp, bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    line.forEach { tile -> TeaserTile(tile, Modifier.tracked(sf, "g:${tile.key}"), GridTileWidth, singleLine = true) }
                }
            }
        }
    }
}

/** Round show avatar; [avatar] null = the "All" entry. */
@Composable
private fun Avatar(avatar: AvatarState?, selected: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    FocusBox(modifier = modifier.width(75.dp), onClick = onClick, onLongClick = onLongClick) { focused ->
        Column(Modifier.width(75.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(61.dp)
                    .drawBehind {
                        if (focused) drawCircle(QColors.Focus, radius = size.minDimension / 2 + 2.5.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                    }
                    .clip(CircleShape),
            ) {
                if (avatar == null) {
                    Box(Modifier.matchParentSize().background(QColors.Surface2), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.avatar_all), style = QType.button.copy(fontSize = 12.sp), color = QColors.Foreground)
                    }
                } else {
                    Artwork(avatar.imageUrl, avatar.title, Modifier.matchParentSize(), seed = 3)
                }
            }
            Text(
                avatar?.title ?: stringResource(R.string.avatar_all_subscriptions),
                style = QType.rowSmall.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (focused || selected) QColors.Foreground else QColors.Muted,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (avatar?.hasNew == true) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).size(12.dp).clip(CircleShape)
                    .background(QColors.Accent).border(2.dp, QColors.Background, CircleShape),
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun SubscriptionsPreview() {
    val overlay = remember { LibraryOverlay() }
    val state = remember {
        val eps = FakeData.allEpisodes.sortedByDescending { it.publishedAt }.take(12)
        SubscriptionsUiState(
            true,
            FakeData.shows.take(6).mapIndexed { i, s -> AvatarState(s.ref.key, s.title, null, i % 2 == 0, s) },
            null,
            eps.mapIndexed { i, e -> overlay.decorate(e).copy(isNew = i < 3) },
        )
    }
    PreviewHost { SubscriptionsContent(state) {} }
}
