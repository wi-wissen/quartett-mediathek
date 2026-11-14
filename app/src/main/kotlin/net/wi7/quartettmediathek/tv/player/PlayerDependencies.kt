package net.wi7.quartettmediathek.tv.player

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository

/** What the player needs from the data layer. Provide it via [LocalPlayerDependencies] from the AppContainer. */
class PlayerDependencies(
    val catalog: CatalogRepository,
    val library: LibraryRepository,
    val playback: PlaybackRepository,
    /**
     * Scope for writes that must outlive the player screen (progress on exit). Not tied to a ViewModel or the
     * composition: `viewModelScope` is cancelled as soon as the player leaves the back stack. Writes run one at a
     * time so a late periodic save can never overwrite the final one.
     */
    val saveScope: CoroutineScope = ProcessSaveScope,
)

@OptIn(ExperimentalCoroutinesApi::class)
private val ProcessSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

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
