package net.wi7.quartettmediathek.tv.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.key

data class TileMenuState(
    val teaser: Teaser,
    val showRef: ShowRef?,
    val subscribed: Boolean,
    val bookmarked: Boolean,
    /** Episodes only; null for shows. */
    val watched: Boolean?,
    /** Episode has been played (in progress or watched), so it can be removed from the history. */
    val inHistory: Boolean = false,
)

private fun Teaser.showRef(): ShowRef? = when (this) {
    is Teaser.OfShow -> show.ref
    is Teaser.OfEpisode -> episode.showRef
}

/** Context menu of a tile (long OK). One instance for the whole app; the shell shows the dialog above any screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class TileMenuViewModel(private val catalog: CatalogRepository, private val library: LibraryRepository) : ViewModel() {
    private val target = MutableStateFlow<Teaser?>(null)

    val state: StateFlow<TileMenuState?> = target.flatMapLatest { teaser ->
        if (teaser == null) {
            flowOf(null)
        } else {
            val showRef = teaser.showRef()
            val subscribed = showRef?.let { r -> library.subscriptionFor(r).map { it != null } } ?: flowOf(false)
            val watched = (teaser as? Teaser.OfEpisode)?.let { t -> library.progress(t.episode.ref).map { it?.watched == true } } ?: flowOf(null)
            val inHistory = (teaser as? Teaser.OfEpisode)?.let { t ->
                library.progress(t.episode.ref).map { p -> p != null && (p.watched || p.positionSeconds > 0) }
            } ?: flowOf(false)
            combine(library.isBookmarked(teaser.key()), subscribed, watched, inHistory) { bookmarked, sub, w, h ->
                TileMenuState(teaser, showRef, sub, bookmarked, w, h)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun open(teaser: Teaser) { target.value = teaser }
    fun dismiss() { target.value = null }

    fun toggleSubscribe() = act { s ->
        val ref = s.showRef ?: return@act
        if (s.subscribed) {
            library.subscriptionFor(ref).first()?.let { library.unsubscribe(it.id) }
        } else {
            val show = (s.teaser as? Teaser.OfShow)?.show ?: catalog.show(ref)
            library.subscribe(show)
        }
    }

    fun toggleBookmark() = act { library.toggleBookmark(it.teaser) }

    fun toggleWatched() = act { s ->
        val episode = (s.teaser as? Teaser.OfEpisode)?.episode ?: return@act
        library.setWatched(episode, s.watched != true)
    }

    fun removeFromHistory() = act { s ->
        val episode = (s.teaser as? Teaser.OfEpisode)?.episode ?: return@act
        library.removeFromHistory(episode.ref)
    }

    private fun act(block: suspend (TileMenuState) -> Unit) {
        val s = state.value ?: return
        dismiss()
        viewModelScope.launch { block(s) }
    }
}

/** The menu: Play, Subscribe/Unsubscribe, Watchlist, Mark watched, Go to show. */
@Composable
fun TileMenuDialog(
    state: TileMenuState,
    onDismiss: () -> Unit,
    onPlay: (EpisodeRef) -> Unit,
    onToggleSubscribe: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleWatched: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onGoToShow: (ShowRef) -> Unit,
) {
    val first = remember { FocusRequester() }
    val teaser = state.teaser
    val (title, subtitle) = when (teaser) {
        is Teaser.OfEpisode -> teaser.episode.title to
            listOfNotNull(teaser.episode.showTitle, teaser.episode.broadcaster.displayName).joinToString(" · ")
        is Teaser.OfShow -> teaser.show.title to
            listOfNotNull(teaser.show.genre, teaser.show.broadcaster.displayName).joinToString(" · ")
    }
    QDialog(title = title, subtitle = subtitle, onDismiss = onDismiss, initialFocus = first) {
        var firstItem = true
        @Composable
        fun entry(label: String, icon: Int, onClick: () -> Unit) {
            val mod = if (firstItem) Modifier.focusRequester(first) else Modifier
            firstItem = false
            MenuRow(title = label, icon = icon, onClick = onClick, modifier = mod)
        }
        if (teaser is Teaser.OfEpisode) {
            entry(stringResource(R.string.action_play), R.drawable.ic_play_arrow_fill) { onDismiss(); onPlay(teaser.episode.ref) }
        }
        if (state.showRef != null) {
            entry(
                stringResource(if (state.subscribed) R.string.menu_unsubscribe else R.string.menu_subscribe),
                if (state.subscribed) R.drawable.ic_check else R.drawable.ic_notifications,
                onToggleSubscribe,
            )
        }
        entry(
            stringResource(
                when {
                    state.bookmarked -> R.string.menu_remove_bookmark
                    teaser is Teaser.OfEpisode -> R.string.menu_bookmark_episode
                    else -> R.string.menu_bookmark_show
                },
            ),
            if (state.bookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
            onToggleBookmark,
        )
        if (state.watched != null) {
            entry(
                stringResource(if (state.watched) R.string.menu_mark_unwatched else R.string.menu_mark_watched),
                R.drawable.ic_visibility, onToggleWatched,
            )
        }
        if (state.inHistory) {
            entry(stringResource(R.string.menu_remove_history), R.drawable.ic_history, onRemoveFromHistory)
        }
        if (state.showRef != null) {
            entry(stringResource(R.string.action_to_show), R.drawable.ic_tv) { onDismiss(); onGoToShow(state.showRef) }
        }
    }
}
