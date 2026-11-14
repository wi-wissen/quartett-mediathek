package net.wi7.quartettmediathek.tv.player

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository

/** What the player needs from the data layer. Provide it via [LocalPlayerDependencies] from the AppContainer. */
class PlayerDependencies(
    val catalog: CatalogRepository,
    val library: LibraryRepository,
    val playback: PlaybackRepository,
)

val LocalPlayerDependencies = compositionLocalOf<PlayerDependencies> {
    error("LocalPlayerDependencies not provided")
}

internal class PlayerViewModelFactory(
    private val deps: PlayerDependencies,
    private val ref: EpisodeRef,
    private val startPositionSeconds: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PlayerViewModel(deps, ref, startPositionSeconds) as T
}
