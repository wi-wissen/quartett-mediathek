package net.wi7.quartettmediathek.tv.ui.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A focusable box that reacts to OK (click), long OK (long click) and Play/Pause.
 * A click only fires when the matching key-down was seen by this box, so the key-up that follows a long press
 * (which may arrive in a dialog opened by it) never triggers anything by accident.
 */
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPlayPause: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val playPause by rememberUpdatedState(onPlayPause)
    Box(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
            }
            .onKeyEvent { e ->
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when (e.type) {
                        KeyEventType.KeyDown -> {
                            val native = e.nativeKeyEvent
                            if (native.repeatCount == 0) {
                                pressed = true
                                longFired = false
                            } else if (pressed && !longFired && native.isLongPress) {
                                longClick?.let {
                                    longFired = true
                                    it()
                                }
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            val fire = pressed && !longFired
                            pressed = false
                            longFired = false
                            if (fire) click()
                            true
                        }
                        else -> false
                    }
                    Key.MediaPlayPause, Key.MediaPlay -> {
                        val action = playPause
                        if (action == null) false else {
                            if (e.type == KeyEventType.KeyUp) action()
                            true
                        }
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = contentAlignment,
    ) { content(focused) }
}

/** Remembers the last focused item key per screen so focus can be restored when a screen comes back. */
class FocusMemory {
    private val map = mutableMapOf<String, String>()
    operator fun get(screen: String): String? = map[screen]
    operator fun set(screen: String, key: String) { map[screen] = key }
}

val LocalFocusMemory = staticCompositionLocalOf { FocusMemory() }

/** Requester attached to the last focused item of the visible screen. The rail's "right" goes there. */
val LocalContentEntry = staticCompositionLocalOf { FocusRequester() }

/**
 * Focus bookkeeping of one screen whose content is a column of rows (one lazy item per row).
 * [rows] lists the focus keys of every row in visual order. The first time rows are non-empty the target is
 * resolved: the remembered key when it still exists, otherwise the first key of the first row.
 */
@Stable
class ScreenFocus(private val screen: String, private val memory: FocusMemory, val entry: FocusRequester) {
    var current: String? by mutableStateOf(null)
        private set
    var targetRow = 0
        private set
    var targetCol = 0
        private set
    var resolved by mutableStateOf(false)
        private set

    fun resolve(rows: List<List<String>>) {
        if (resolved) return
        if (rows.none { it.isNotEmpty() }) return
        val saved = memory[screen]
        for ((r, row) in rows.withIndex()) {
            val c = row.indexOf(saved)
            if (saved != null && c >= 0) {
                targetRow = r; targetCol = c; current = saved; resolved = true
                return
            }
        }
        targetRow = rows.indexOfFirst { it.isNotEmpty() }
        targetCol = 0
        current = rows[targetRow][0]
        resolved = true
    }

    /** Index of the first visible item of a horizontal row so the restored tile is composed immediately. */
    fun initialItem(row: Int) = if (row == targetRow) targetCol else 0

    fun onFocused(key: String) {
        current = key
        memory[screen] = key
    }
}

/** Attach to every focusable item of a screen: records focus and marks the current item as entry point. */
@Composable
fun Modifier.tracked(sf: ScreenFocus, key: String): Modifier {
    val isEntry by remember(sf, key) { derivedStateOf { sf.current == key } }
    return this
        .then(if (isEntry) Modifier.focusRequester(sf.entry) else Modifier)
        .onFocusChanged { if (it.isFocused) sf.onFocused(key) }
}

@Composable
fun rememberScreenFocus(screen: String, rows: List<List<String>>): ScreenFocus {
    val memory = LocalFocusMemory.current
    val entry = LocalContentEntry.current
    val sf = remember(screen) { ScreenFocus(screen, memory, entry) }
    sf.resolve(rows)
    return sf
}

/** Moves focus into the screen once its content is ready: scrolls the target row into composition, then focuses. */
@Composable
fun InitialFocusEffect(sf: ScreenFocus, listState: LazyListState?) {
    androidx.compose.runtime.LaunchedEffect(sf.resolved) {
        if (!sf.resolved) return@LaunchedEffect
        try {
            // Only scroll when the target row is not composed yet; needlessly scrolling would hide the header.
            if (listState != null) {
                androidx.compose.runtime.withFrameNanos { }
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == sf.targetRow }) listState.scrollToItem(sf.targetRow)
            }
            repeat(4) {
                androidx.compose.runtime.withFrameNanos { }
                if (runCatching { sf.entry.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        } catch (e: CancellationException) {
            throw e
        }
    }
}

/** Scrolls the lazy column so this row sits at the top whenever focus enters it (like the mockup's row anchors). */
@Composable
fun Modifier.snapRowOnFocus(listState: LazyListState, rowIndex: Int): Modifier {
    val scope = rememberCoroutineScope()
    // Row 0 sits at the content padding (header visible); later rows go to a small top margin (header hidden).
    val inset = if (rowIndex == 0) 0 else net.wi7.quartettmediathek.tv.ui.components.LocalRowSnapInset.current
    return this.onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(rowIndex, inset) } }
}

/** Bring-into-view behaviour: keeps the focused item at least [lead] from the start and [trail] from the end. */
@OptIn(ExperimentalFoundationApi::class)
class MarginBringIntoViewSpec(private val lead: Float, private val trail: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset < lead -> offset - lead
            end > containerSize - trail -> minOf(end - (containerSize - trail), offset - lead)
            else -> 0f
        }
    }
}

/** Disables automatic bring-into-view; used where the screen scrolls rows itself. */
@OptIn(ExperimentalFoundationApi::class)
object NoBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberMarginSpec(lead: Dp, trail: Dp, density: Density): BringIntoViewSpec =
    remember(lead, trail, density) { MarginBringIntoViewSpec(with(density) { lead.toPx() }, with(density) { trail.toPx() }) }
