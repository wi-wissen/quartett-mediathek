package net.wi7.quartettmediathek.tv.player

import net.wi7.quartettmediathek.library.LibraryKeys
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant

/** Everything the UI needs to start playing one episode. */
data class PlaybackSession(
    val episode: Episode,
    val stream: StreamInfo,
    /** The version that plays (audio description is selected as audio track when the stream has one). */
    val variant: Variant,
    val startPositionMs: Long,
    val next: Episode?,
    /** The ref the stream was resolved for; differs from [episode] after switching to another version's file. */
    val streamRef: EpisodeRef = episode.ref,
) {
    /** Versions the user can switch to; fewer than two means there is nothing to choose. */
    val versions: List<VersionOption> = PlayerLogic.versionOptions(episode.versionRefs, streamRef, stream)
}

enum class PlayerError { NOT_FOUND, STREAM, PLAYBACK }

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Error(val error: PlayerError) : PlayerUiState
    data class Ready(val session: PlaybackSession) : PlayerUiState
}

class PlayerViewModel(
    private val deps: PlayerDependencies,
    initialRef: EpisodeRef,
    initialStartSeconds: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state

    private val _bookmarked = MutableStateFlow(false)
    val bookmarked: StateFlow<Boolean> = _bookmarked

    private val _versionFailed = MutableStateFlow(false)
    val versionFailed: StateFlow<Boolean> = _versionFailed

    private var currentEpisode: Episode? = null
    private var lastPositionSeconds = 0L
    private var lastDurationSeconds = 0L

    init {
        load(initialRef, initialStartSeconds)
    }

    /** Loads an episode and resolves a fresh stream (also used for "next episode"). */
    fun load(ref: EpisodeRef, startSeconds: Long?) {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            val episode = runCatching { deps.catalog.episode(ref) }.getOrNull()
            if (episode == null) {
                _state.value = PlayerUiState.Error(PlayerError.NOT_FOUND)
                return@launch
            }
            currentEpisode = episode
            lastPositionSeconds = 0
            lastDurationSeconds = episode.durationSeconds.toLong()
            val saved = runCatching { deps.library.progress(ref).first() }.getOrNull()
            val subscriptionVariant = episode.showRef?.let { show ->
                runCatching { deps.library.subscriptionFor(show).first()?.preferredVariant }.getOrNull()
            }
            val requested = PlayerLogic.preferredVariant(subscriptionVariant, episode.variants)
            val stream = runCatching { deps.playback.stream(ref, requested) }.getOrNull()
            if (stream == null) {
                _state.value = PlayerUiState.Error(PlayerError.STREAM)
                return@launch
            }
            val variant = PlayerLogic.activeVersion(episode.versionRefs, ref, requested, stream)
            val next = episode.showRef?.let { show ->
                runCatching { PlayerLogic.nextEpisode(deps.catalog.episodes(show), ref) }.getOrNull()
            }
            val start = PlayerLogic.startPosition(
                startSeconds, saved?.positionSeconds, saved?.watched == true, episode.durationSeconds.toLong(),
            )
            _bookmarked.value = runCatching { deps.library.isBookmarked(bookmarkKey(ref)).first() }.getOrDefault(false)
            _state.value = PlayerUiState.Ready(PlaybackSession(episode, stream, variant, start * 1000, next))
        }
    }

    /**
     * Switches to another version at [positionMs]: resolves the version's own stream, or keeps the current one when
     * the version is an audio track of it. Progress stays attached to the episode the user started. On failure the
     * current version keeps playing and [versionFailed] is true for a moment.
     */
    fun switchVersion(option: VersionOption, positionMs: Long) {
        val session = (state.value as? PlayerUiState.Ready)?.session ?: return
        if (option.variant == session.variant) return
        viewModelScope.launch {
            val stream = if (option.audioTrack) session.stream
            else runCatching { deps.playback.stream(option.ref, option.variant) }.getOrNull()
            if (stream == null) {
                _versionFailed.value = true
                delay(VERSION_FAILED_MS)
                _versionFailed.value = false
                return@launch
            }
            // The user may have skipped to another episode while the stream was resolving.
            val now = (state.value as? PlayerUiState.Ready)?.session
            if (now == null || now.episode.ref != session.episode.ref) return@launch
            _state.value = PlayerUiState.Ready(
                now.copy(stream = stream, variant = option.variant, startPositionMs = positionMs, streamRef = option.ref),
            )
        }
    }

    fun onPlaybackError() {
        _state.value = PlayerUiState.Error(PlayerError.PLAYBACK)
    }

    /** Remembers the latest player position so [saveNow] without arguments works. */
    fun reportPosition(positionSeconds: Long, durationSeconds: Long) {
        lastPositionSeconds = positionSeconds
        if (durationSeconds > 0) lastDurationSeconds = durationSeconds
    }

    /** Persists progress (and the watched flag at >= 95 %). Safe to call on exit. */
    fun saveNow() {
        val episode = currentEpisode ?: return
        if (state.value !is PlayerUiState.Ready) return
        val position = lastPositionSeconds
        val watched = PlayerLogic.isWatched(position, lastDurationSeconds)
        // Not viewModelScope: it is cancelled when the player is popped, which would drop the final save.
        deps.saveScope.launch {
            runCatching {
                deps.library.saveProgress(episode, position)
                if (watched) deps.library.setWatched(episode, true)
            }
        }
    }

    fun toggleBookmark() {
        val episode = currentEpisode ?: return
        _bookmarked.update { !it }
        viewModelScope.launch {
            runCatching { deps.library.toggleBookmark(Teaser.OfEpisode(episode)) }
                .onFailure { _bookmarked.update { v -> !v } }
        }
    }

    companion object {
        /** Watchlist key of an episode; the rest of the UI must use the same format. */
        private const val VERSION_FAILED_MS = 3000L

        fun bookmarkKey(ref: EpisodeRef) = LibraryKeys.bookmark(ref)
    }
}
