package net.wi7.quartettmediathek.tv.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.di.ViewModelFactory
import net.wi7.quartettmediathek.tv.player.PlayerScreen
import net.wi7.quartettmediathek.tv.ui.components.HeaderState
import net.wi7.quartettmediathek.tv.ui.components.LocalHeaderState
import net.wi7.quartettmediathek.tv.ui.components.LocalTileActions
import net.wi7.quartettmediathek.tv.ui.components.TileActions
import net.wi7.quartettmediathek.tv.ui.focus.FocusMemory
import net.wi7.quartettmediathek.tv.ui.focus.LocalContentEntry
import net.wi7.quartettmediathek.tv.ui.focus.LocalFocusMemory
import net.wi7.quartettmediathek.tv.ui.menu.TileMenuDialog
import net.wi7.quartettmediathek.tv.ui.menu.TileMenuViewModel
import net.wi7.quartettmediathek.tv.ui.screens.AppHeader
import net.wi7.quartettmediathek.tv.ui.screens.ShellUiState
import net.wi7.quartettmediathek.tv.ui.screens.AppRail
import net.wi7.quartettmediathek.tv.ui.screens.DiscoverScreen
import net.wi7.quartettmediathek.tv.ui.screens.EpisodeScreen
import net.wi7.quartettmediathek.tv.ui.screens.HistoryScreen
import net.wi7.quartettmediathek.tv.ui.screens.HomeScreen
import net.wi7.quartettmediathek.tv.ui.screens.SearchScreen
import net.wi7.quartettmediathek.tv.ui.screens.SettingsScreen
import net.wi7.quartettmediathek.tv.ui.screens.ShellViewModel
import net.wi7.quartettmediathek.tv.ui.screens.ShowScreen
import net.wi7.quartettmediathek.tv.ui.screens.SubscriptionsScreen
import net.wi7.quartettmediathek.tv.ui.screens.WatchlistScreen
import net.wi7.quartettmediathek.tv.ui.theme.QColors

internal val RailWidth = 60.dp

/**
 * App shell: NavHost with all routes, left rail, fixed header and the tile context menu. The player is full screen
 * (no rail, no header). [startRoute] comes from `SettingsRepository.startScreen()`.
 */
@Composable
fun AppShell(startRoute: String) {
    val container = LocalAppContainer.current
    val factory = remember(container) { ViewModelFactory(container) }
    val nav = rememberNavController()
    val shellVm: ShellViewModel = viewModel(factory = factory)
    val menuVm: TileMenuViewModel = viewModel(factory = factory)
    val shell by shellVm.state.collectAsStateWithLifecycle()
    val menu by menuVm.state.collectAsStateWithLifecycle()

    val headerState = remember { HeaderState() }
    val focusMemory = remember { FocusMemory() }
    val contentEntry = remember { FocusRequester() }
    val railRequesters = remember { Routes.topLevel.associateWith { FocusRequester() } }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isPlayer = route == Routes.PLAYER_PATTERN
    // The player stays composed for a moment after Back; the rail and header wait until it has left composition.
    var playersComposed by remember { mutableIntStateOf(0) }
    val showChrome = shellChromeVisible(isPlayer, playersComposed)
    val isTopLevel = route in Routes.topLevel

    // Rail highlights the top-level screen the current page belongs to (Show/Episode keep their parent highlighted).
    var lastTopLevel by remember { mutableStateOf(startRoute) }
    if (isTopLevel && route != null && route != lastTopLevel) lastTopLevel = route
    var railFocused by remember { mutableStateOf(false) }

    val actions = remember(nav, menuVm) {
        TileActions(
            openShow = { nav.navigate(Routes.show(it)) },
            openEpisode = { nav.navigate(Routes.episode(it)) },
            play = { ref, start -> nav.navigate(Routes.player(ref, start)) },
            openMenu = { menuVm.open(it) },
        )
    }

    // Back inside a top-level screen moves focus to the rail; Back on the rail leaves the app (default).
    BackHandler(enabled = isTopLevel && !railFocused && menu == null) {
        runCatching { railRequesters.getValue(lastTopLevel).requestFocus() }
    }

    CompositionLocalProvider(
        LocalFocusMemory provides focusMemory,
        LocalContentEntry provides contentEntry,
        LocalTileActions provides actions,
        LocalHeaderState provides headerState,
    ) {
        Box(Modifier.fillMaxSize().background(QColors.Background)) {
            NavHost(
                navController = nav,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize(),
                // No transitions: the previous screen must leave composition at once so focus requesters stay unique.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                railDestination(Routes.HOME) { HomeScreen() }
                railDestination(Routes.DISCOVER) { DiscoverScreen() }
                railDestination(Routes.SEARCH) { SearchScreen() }
                railDestination(Routes.SUBSCRIPTIONS) { SubscriptionsScreen() }
                railDestination(Routes.BOOKMARKS) { WatchlistScreen() }
                railDestination(Routes.HISTORY) { HistoryScreen() }
                railDestination(Routes.SETTINGS) { SettingsScreen() }
                railDestination(Routes.SHOW_PATTERN) { e -> ShowScreen(Routes.showRef(e.arg("source"), e.arg("key"))) }
                railDestination(Routes.EPISODE_PATTERN) { e -> EpisodeScreen(Routes.episodeRef(e.arg("source"), e.arg("key"))) }
                playerDestination(
                    onComposed = { playersComposed++ },
                    onDisposed = { playersComposed-- },
                ) { e ->
                    val start = e.arguments?.getLong("start") ?: -1L
                    PlayerScreen(
                        episode = Routes.episodeRef(e.arg("source"), e.arg("key")),
                        startPositionSeconds = start.takeIf { it >= 0 },
                        onExit = { nav.popBackStack() },
                    )
                }
            }
            if (showChrome) {
                ShellHeader(shell, onRefresh = shellVm::refresh, modifier = Modifier.align(Alignment.TopEnd))
                AppRail(
                    activeRoute = lastTopLevel,
                    requesters = railRequesters,
                    contentEntry = contentEntry,
                    onNavigate = { target ->
                        if (target == lastTopLevel && isTopLevel) {
                            runCatching { contentEntry.requestFocus() }
                        } else {
                            nav.navigate(target) {
                                popUpTo(startRoute) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onFocusChange = { railFocused = it },
                )
            }
            menu?.let { state ->
                TileMenuDialog(
                    state = state,
                    onDismiss = menuVm::dismiss,
                    onPlay = { nav.navigate(Routes.player(it, null)) },
                    onToggleSubscribe = menuVm::toggleSubscribe,
                    onToggleBookmark = menuVm::toggleBookmark,
                    onToggleWatched = menuVm::toggleWatched,
                    onRemoveFromHistory = menuVm::removeFromHistory,
                    onGoToShow = { nav.navigate(Routes.show(it)) },
                )
            }
        }
    }
}

/** Pure rule: rail and header are shown only when the player is not the current page and no player is still composed. */
internal fun shellChromeVisible(isPlayer: Boolean, playersComposed: Int): Boolean = !isPlayer && playersComposed == 0

/** A destination that sits right of the rail; the padding is part of the destination, so the player never gets it. */
internal fun NavGraphBuilder.railDestination(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { e -> Box(Modifier.fillMaxSize().padding(start = RailWidth)) { content(e) } }
}

/** The full-screen player destination (no padding). [onComposed]/[onDisposed] report when it enters/leaves composition. */
internal fun NavGraphBuilder.playerDestination(
    onComposed: () -> Unit = {},
    onDisposed: () -> Unit = {},
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        Routes.PLAYER_PATTERN,
        arguments = listOf(navArgument("start") { type = NavType.LongType }),
    ) { e ->
        DisposableEffect(Unit) {
            onComposed()
            onDispose(onDisposed)
        }
        Box(Modifier.fillMaxSize()) { content(e) }
    }
}

private fun NavBackStackEntry.arg(name: String): String? = arguments?.getString(name)

/**
 * Top fade and header, faded/slid in and out depending on [LocalHeaderState]. A hidden header leaves composition,
 * so the refresh button cannot take focus then.
 */
@Composable
internal fun ShellHeader(
    state: ShellUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = LocalHeaderState.current.visible,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { -it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 },
    ) {
        Box(Modifier.fillMaxWidth()) {
            TopFade()
            AppHeader(state, onRefresh = onRefresh, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
internal fun TopFade() {
    val bg = QColors.Background
    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .background(Brush.verticalGradient(0f to bg, 0.62f to bg, 1f to Color.Transparent)),
    )
}
