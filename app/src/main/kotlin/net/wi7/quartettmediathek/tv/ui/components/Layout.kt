package net.wi7.quartettmediathek.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.ui.focus.NoBringIntoViewSpec
import net.wi7.quartettmediathek.tv.ui.focus.ScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.MarginBringIntoViewSpec
import net.wi7.quartettmediathek.tv.ui.focus.rememberMarginSpec
import net.wi7.quartettmediathek.tv.ui.focus.snapRowOnFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme

/** Height reserved for the header at the top of a screen that is scrolled to the top. */
val HeaderHeight = 60.dp

/** Distance between the top edge and a row that was snapped to the top while the header is hidden. */
val SnapTopMargin = 22.dp

/** Scroll offset up to which the screen still counts as "at the top" (header visible). */
private val AtTopTolerance = 8.dp

/**
 * Shell-level state: whether the header (and the top fade) is shown. Screens report their scroll position through
 * [ScreenColumn]; the header is visible only while the screen is scrolled to the top.
 */
@Stable
class HeaderState {
    var visible by mutableStateOf(true)

    /** True while a full-bleed hero image sits behind the header (show/episode page): the top fade is then only a light shade. */
    var overImage by mutableStateOf(false)
}

val LocalHeaderState = staticCompositionLocalOf { HeaderState() }

/** Extra scroll (px, may be negative) applied when snapping a row (index > 0) to the top; set by [ScreenColumn]. */
val LocalRowSnapInset = compositionLocalOf { 0 }

/** Pure rule: header visible only at the very top of the list. */
internal fun isAtTop(firstVisibleIndex: Int, firstVisibleOffset: Int, tolerancePx: Int): Boolean =
    firstVisibleIndex == 0 && firstVisibleOffset <= tolerancePx

/** Reports [state] to the shell's [HeaderState] and shows the header again when the screen leaves. */
@Composable
private fun ReportHeaderVisibility(state: LazyListState) {
    val header = LocalHeaderState.current
    val tolerance = with(LocalDensity.current) { AtTopTolerance.roundToPx() }
    LaunchedEffect(state, header) {
        snapshotFlow { isAtTop(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, tolerance) }
            .collect { header.visible = it }
    }
    DisposableEffect(header) { onDispose { header.visible = true } }
}

/**
 * Like [MarginBringIntoViewSpec], but while the list is close to its top (within the header zone) it scrolls fully
 * to the top so the header can come back instead of stopping a few dp short.
 */
@OptIn(ExperimentalFoundationApi::class)
private class GridBringIntoViewSpec(
    private val state: LazyListState,
    lead: Float,
    trail: Float,
    private val topPadding: Float,
) : BringIntoViewSpec {
    private val margin = MarginBringIntoViewSpec(lead, trail)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val d = margin.calculateScrollDistance(offset, size, containerSize)
        if (d < 0f && state.firstVisibleItemIndex == 0) {
            val scrolled = state.firstVisibleItemScrollOffset.toFloat()
            if (scrolled + d <= topPadding) return -scrolled
        }
        return d
    }
}

/**
 * Vertical column of rows, one lazy item per row (the index equals the row index in [ScreenFocus]).
 * With [snapRows] the screen scrolls rows to the top itself ([snapRowOnFocus]); without it, the focused item is kept
 * within a margin (used by grids).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenColumn(
    state: LazyListState,
    modifier: Modifier = Modifier,
    topPadding: Dp = HeaderHeight,
    snapRows: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val spec: BringIntoViewSpec = if (snapRows) NoBringIntoViewSpec else remember(state, density, topPadding) {
        GridBringIntoViewSpec(state, with(density) { SnapTopMargin.toPx() }, with(density) { 40.dp.toPx() }, with(density) { topPadding.toPx() })
    }
    val snapInset = with(density) { (topPadding - SnapTopMargin).roundToPx() }
    ReportHeaderVisibility(state)
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, LocalRowSnapInset provides snapInset) {
        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = 200.dp),
        ) { content() }
    }
}

@Composable
fun RowHeader(title: String, small: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 24.dp, top = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = QType.rowTitle, color = QColors.Foreground, maxLines = 1)
        if (small != null) Text(small, style = QType.rowSmall, color = QColors.Muted, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** A titled, horizontally scrolling row of focusable items. Keys must be unique within the screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ContentRow(
    rowIndex: Int,
    sf: ScreenFocus,
    listState: LazyListState,
    title: String,
    items: List<T>,
    keyOf: (T) -> String,
    modifier: Modifier = Modifier,
    small: String? = null,
    item: @Composable (T, Modifier) -> Unit,
) {
    Column(modifier.snapRowOnFocus(listState, rowIndex)) {
        RowHeader(title, small)
        HorizontalItems(rowIndex = rowIndex, sf = sf, items = items, keyOf = keyOf, item = item)
    }
}

/** The scrolling part of a row, without title (used under custom headers such as the avatars on Subscriptions). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> HorizontalItems(
    rowIndex: Int,
    sf: ScreenFocus,
    items: List<T>,
    keyOf: (T) -> String,
    contentPadding: PaddingValues = PaddingValues(start = 24.dp, end = 40.dp, top = 11.dp, bottom = 17.dp),
    spacing: Dp = 15.dp,
    item: @Composable (T, Modifier) -> Unit,
) {
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = sf.initialItem(rowIndex))
    val spec = rememberMarginSpec(24.dp, 40.dp, LocalDensity.current)
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(state = rowState, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            items(items, key = keyOf) { value -> item(value, Modifier.tracked(sf, keyOf(value))) }
        }
    }
}

/** Dark background host for previews. */
@Composable
fun PreviewHost(content: @Composable () -> Unit) {
    QuartettTheme {
        Box(Modifier.fillMaxSize().background(QColors.Background)) { content() }
    }
}
