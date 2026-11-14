package net.wi7.quartettmediathek.tv.player

import androidx.compose.runtime.Composable
import net.wi7.quartettmediathek.model.EpisodeRef

/**
 * Full-screen player (Media3 ExoPlayer). Owned by the player work package.
 * Contract for navigation: open with an episode and an optional start position; [onExit] returns to the caller.
 * A [startPositionSeconds] of null means "resume from saved progress", 0 means "from the beginning".
 */
@Composable
fun PlayerScreen(episode: EpisodeRef, startPositionSeconds: Long?, onExit: () -> Unit) {
    TODO("Player work package")
}
