package net.wi7.quartettmediathek.tv.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.repository.SettingsRepository
import net.wi7.quartettmediathek.repository.StartScreen
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.ui.components.PreviewHost
import net.wi7.quartettmediathek.tv.ui.components.QIcon
import net.wi7.quartettmediathek.tv.ui.components.RowHeader
import net.wi7.quartettmediathek.tv.ui.components.ScreenColumn
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.focus.InitialFocusEffect
import net.wi7.quartettmediathek.tv.ui.focus.rememberScreenFocus
import net.wi7.quartettmediathek.tv.ui.focus.tracked
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

data class SettingsUiState(
    val loaded: Boolean = false,
    val startScreen: StartScreen = StartScreen.HOME,
    val cacheCleared: Boolean = false,
)

/** Settings: start screen (radio list) and clear cache. */
class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val cleared = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.startScreen(), cleared) { start, c ->
        SettingsUiState(loaded = true, startScreen = start, cacheCleared = c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectStart(screen: StartScreen) {
        viewModelScope.launch { settings.setStartScreen(screen) }
    }

    fun clearCache() {
        viewModelScope.launch {
            settings.clearCache()
            cleared.value = true
            delay(3_000)
            cleared.value = false
        }
    }
}

@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsContent(state, onSelectStart = vm::selectStart, onClearCache = vm::clearCache)
}

private class StartOption(val screen: StartScreen, @StringRes val title: Int, @StringRes val description: Int)

private val startOptions = listOf(
    StartOption(StartScreen.HOME, R.string.start_home, R.string.start_home_desc),
    StartOption(StartScreen.SUBSCRIPTIONS, R.string.start_subscriptions, R.string.start_subscriptions_desc),
    StartOption(StartScreen.BOOKMARKS, R.string.start_bookmarks, R.string.start_bookmarks_desc),
)

private const val KeyCache = "opt:cache"

@Composable
fun SettingsContent(state: SettingsUiState, onSelectStart: (StartScreen) -> Unit, onClearCache: () -> Unit) {
    val rows = listOf(emptyList<String>()) + startOptions.map { listOf("opt:${it.screen.name}") } + listOf(emptyList<String>(), listOf(KeyCache))
    val sf = rememberScreenFocus("settings", if (state.loaded) rows else emptyList())
    val listState = rememberLazyListState()
    InitialFocusEffect(sf, listState)
    ScreenColumn(listState, snapRows = false) {
        item(key = "h-start") { RowHeader(stringResource(R.string.settings_start_title)) }
        startOptions.forEach { opt ->
            item(key = opt.screen.name) {
                SettingOption(
                    title = stringResource(opt.title), description = stringResource(opt.description),
                    selected = state.startScreen == opt.screen, icon = null,
                    modifier = Modifier.tracked(sf, "opt:${opt.screen.name}"), onClick = { onSelectStart(opt.screen) },
                )
            }
        }
        item(key = "h-data") { RowHeader(stringResource(R.string.settings_data_title), modifier = Modifier.padding(top = 20.dp)) }
        item(key = "cache") {
            SettingOption(
                title = stringResource(R.string.settings_clear_cache),
                description = stringResource(if (state.cacheCleared) R.string.settings_cache_cleared else R.string.settings_clear_cache_desc),
                selected = null, icon = R.drawable.ic_refresh,
                modifier = Modifier.tracked(sf, KeyCache), onClick = onClearCache,
            )
        }
        item(key = "note") {
            Text(
                stringResource(R.string.settings_note), style = QType.body.copy(fontSize = 11.sp), color = QColors.Muted,
                modifier = Modifier.padding(start = 24.dp, top = 15.dp).width(420.dp),
            )
        }
    }
}

/** Radio-style option row ([selected] non-null) or plain action row ([icon]). */
@Composable
private fun SettingOption(
    title: String,
    description: String,
    selected: Boolean?,
    @DrawableRes icon: Int?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FocusBox(modifier = modifier.padding(start = 24.dp, top = 5.dp, bottom = 2.dp).width(550.dp), onClick = onClick) { focused ->
        val fg = if (focused) QColors.Background else QColors.Foreground
        val sub = if (focused) Color3B4650 else QColors.Muted
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(if (focused) QColors.Focus else QColors.Surface)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (selected != null) {
                val ring = if (focused) QColors.Background else if (selected) QColors.Accent else QColors.Muted
                Box(Modifier.size(17.dp).border(1.5.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                    if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(if (focused) QColors.Background else QColors.Accent))
                }
            } else if (icon != null) {
                QIcon(icon, fg, size = 17.dp)
            }
            Column {
                Text(title, style = QType.rowTitle.copy(fontSize = 15.sp), color = fg)
                Text(description, style = QType.rowSmall, color = sub)
            }
        }
    }
}

private val Color3B4650 = androidx.compose.ui.graphics.Color(0xFF3B4650)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun SettingsPreview() {
    PreviewHost { SettingsContent(SettingsUiState(true, StartScreen.SUBSCRIPTIONS), {}, {}) }
}
