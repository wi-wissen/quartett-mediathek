package net.wi7.quartettmediathek.tv.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.tv.ui.menu.TileMenuViewModel
import net.wi7.quartettmediathek.tv.ui.screens.DiscoverViewModel
import net.wi7.quartettmediathek.tv.ui.screens.EpisodeViewModel
import net.wi7.quartettmediathek.tv.ui.screens.HomeViewModel
import net.wi7.quartettmediathek.tv.ui.screens.SearchViewModel
import net.wi7.quartettmediathek.tv.ui.screens.SettingsViewModel
import net.wi7.quartettmediathek.tv.ui.screens.ShellViewModel
import net.wi7.quartettmediathek.tv.ui.screens.ShowViewModel
import net.wi7.quartettmediathek.tv.ui.screens.SubscriptionsViewModel
import net.wi7.quartettmediathek.tv.ui.screens.WatchlistViewModel

/** Creates the ViewModels from the [AppContainer]. [showRef] / [episodeRef] parametrize the detail screens. */
class ViewModelFactory(
    private val c: AppContainer,
    private val showRef: ShowRef? = null,
    private val episodeRef: EpisodeRef? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        ShellViewModel::class.java -> ShellViewModel(c.catalog)
        TileMenuViewModel::class.java -> TileMenuViewModel(c.catalog, c.library)
        HomeViewModel::class.java -> HomeViewModel(c.catalog, c.library)
        DiscoverViewModel::class.java -> DiscoverViewModel(c.catalog, c.library)
        SearchViewModel::class.java -> SearchViewModel(c.catalog, c.library)
        SubscriptionsViewModel::class.java -> SubscriptionsViewModel(c.library)
        WatchlistViewModel::class.java -> WatchlistViewModel(c.library)
        SettingsViewModel::class.java -> SettingsViewModel(c.settings)
        ShowViewModel::class.java -> ShowViewModel(requireNotNull(showRef), c.catalog, c.library)
        EpisodeViewModel::class.java -> EpisodeViewModel(requireNotNull(episodeRef), c.catalog, c.library)
        else -> error("Unknown ViewModel ${modelClass.name}")
    } as T
}
