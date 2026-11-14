package net.wi7.quartettmediathek.tv.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.navigation.Routes
import net.wi7.quartettmediathek.tv.ui.UpdatedLabel
import net.wi7.quartettmediathek.tv.ui.clockText
import net.wi7.quartettmediathek.tv.ui.components.HeaderHeight
import net.wi7.quartettmediathek.tv.ui.components.QIcon
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.label
import net.wi7.quartettmediathek.tv.ui.rememberNow
import net.wi7.quartettmediathek.tv.ui.theme.Display
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.updatedLabel

data class ShellUiState(val updated: Instant? = null, val refreshing: Boolean = false)

/** Header state: when the catalog was last updated and whether a refresh runs ([CatalogRepository]). */
class ShellViewModel(private val catalog: CatalogRepository) : ViewModel() {
    val state: StateFlow<ShellUiState> = combine(catalog.lastUpdated(), catalog.isRefreshing()) { u, r -> ShellUiState(u, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState())

    fun refresh() {
        viewModelScope.launch { catalog.refresh(force = true) }
    }
}

/** Fixed top-right header: secondary refresh button ("Updated: ...") and the clock. Does not scroll with content. */
@Composable
fun AppHeader(state: ShellUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Row(
        modifier.height(HeaderHeight).padding(end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        RefreshButton(state, now, onRefresh)
        Text(clockText(now), style = QType.rowTitle.copy(fontSize = 14.sp), color = QColors.Muted, maxLines = 1)
    }
}

@Composable
private fun RefreshButton(state: ShellUiState, now: Instant, onClick: () -> Unit) {
    FocusBox(onClick = onClick) { focused ->
        val spin = rememberInfiniteTransition(label = "refresh")
        val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "angle")
        val text = if (state.refreshing) stringResource(R.string.refreshing)
        else stringResource(R.string.refresh_state, updatedLabel(state.updated, now, ZoneId.systemDefault()).label())
        Row(
            Modifier.clip(CircleShape).background(QColors.Surface2)
                .border(2.dp, if (focused) QColors.Focus else Color.Transparent, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QIcon(R.drawable.ic_refresh, QColors.Foreground, Modifier.rotate(if (state.refreshing) angle else 0f), size = 13.dp)
            Text(text, style = QType.chip.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = QColors.Foreground, maxLines = 1, softWrap = false)
        }
    }
}

private class RailItem(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

private val railItems = listOf(
    RailItem(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
    RailItem(Routes.DISCOVER, R.string.nav_discover, R.drawable.ic_explore),
    RailItem(Routes.SEARCH, R.string.nav_search, R.drawable.ic_search),
    RailItem(Routes.SUBSCRIPTIONS, R.string.nav_subscriptions, R.drawable.ic_subscriptions),
    RailItem(Routes.BOOKMARKS, R.string.nav_bookmarks, R.drawable.ic_bookmark),
)
private val settingsItem = RailItem(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings)

/**
 * Left rail. Collapsed it shows icons; while one of its items has focus it expands with labels and logo name.
 * [activeRoute] is the top-level destination the current screen belongs to; [requesters] lets the shell focus it (Back).
 * D-pad right leaves the rail towards [contentEntry].
 */
@Composable
fun AppRail(
    activeRoute: String?,
    requesters: Map<String, FocusRequester>,
    contentEntry: FocusRequester,
    onNavigate: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (open) 180.dp else 60.dp, tween(180), label = "railWidth")
    val bg = QColors.Background
    Box(
        modifier.fillMaxHeight().width(width)
            .background(if (open) Brush.horizontalGradient(0f to bg, 0.72f to bg, 1f to Color.Transparent) else Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Transparent))
            .onFocusChanged { open = it.hasFocus; onFocusChange(it.hasFocus) },
    ) {
        Row(Modifier.padding(start = 19.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.app_name), style = QType.menuTitle.copy(fontFamily = Display, fontSize = 19.sp),
                color = QColors.Foreground, maxLines = 1, softWrap = false, modifier = Modifier.alpha(if (open) 1f else 0f),
            )
        }
        Column(Modifier.fillMaxHeight().padding(start = 11.dp, top = 70.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            railItems.forEach { RailEntry(it, open, it.route == activeRoute, requesters.getValue(it.route), contentEntry, onNavigate) }
            Spacer(Modifier.weight(1f))
            RailEntry(settingsItem, open, settingsItem.route == activeRoute, requesters.getValue(settingsItem.route), contentEntry, onNavigate)
        }
    }
}

@Composable
private fun RailEntry(
    item: RailItem,
    open: Boolean,
    active: Boolean,
    requester: FocusRequester,
    contentEntry: FocusRequester,
    onNavigate: (String) -> Unit,
) {
    FocusBox(
        modifier = Modifier.focusRequester(requester).focusProperties { right = contentEntry },
        onClick = { onNavigate(item.route) },
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        val w by animateDpAsState(if (open) 145.dp else 38.dp, tween(180), label = "itemWidth")
        val fg by animateColorAsState(if (focused) QColors.Background else if (active) QColors.Foreground else QColors.Muted, label = "itemFg")
        Row(
            Modifier.width(w).height(34.dp).clip(CircleShape).background(if (focused) QColors.Focus else Color.Transparent).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            QIcon(item.icon, fg, size = 17.dp)
            Text(stringResource(item.label), style = QType.railLabel, color = fg, maxLines = 1, softWrap = false, modifier = Modifier.alpha(if (open) 1f else 0f))
        }
        if (active && !open) {
            Box(Modifier.offset(x = (-7).dp).width(2.5.dp).height(17.dp).clip(RoundedCornerShape(2.dp)).background(QColors.Accent))
        }
    }
}
