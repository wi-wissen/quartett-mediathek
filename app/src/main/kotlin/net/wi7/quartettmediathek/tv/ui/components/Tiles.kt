package net.wi7.quartettmediathek.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.TileState
import net.wi7.quartettmediathek.tv.ui.episodeMetaSuffix
import net.wi7.quartettmediathek.tv.ui.formatClock
import net.wi7.quartettmediathek.tv.ui.progressFraction
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.rememberNow
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.theme.color

/** What happens when the user acts on a tile; provided by the app shell, which owns the NavController. */
@Immutable
class TileActions(
    val openShow: (ShowRef) -> Unit,
    val openEpisode: (EpisodeRef) -> Unit,
    /** Start position: null = resume from saved progress, 0 = from the beginning. */
    val play: (EpisodeRef, Long?) -> Unit,
    val openMenu: (Teaser) -> Unit,
) {
    companion object {
        val None = TileActions({}, {}, { _, _ -> }, {})
    }
}

val LocalTileActions = staticCompositionLocalOf { TileActions.None }

val RowTileWidth = 208.dp
val GridTileWidth = 185.dp

/**
 * Tile for a show or an episode. OK opens the show / episode page, long OK opens the context menu,
 * Play/Pause plays an episode directly. [singleLine] is used in the 4-column grid (title on one line).
 */
@Composable
fun TeaserTile(
    state: TileState,
    modifier: Modifier = Modifier,
    width: Dp = RowTileWidth,
    singleLine: Boolean = false,
) {
    val actions = LocalTileActions.current
    val teaser = state.teaser
    MediaTile(
        state = state,
        modifier = modifier,
        width = width,
        singleLine = singleLine,
        onClick = {
            when (teaser) {
                is Teaser.OfEpisode -> actions.openEpisode(teaser.episode.ref)
                is Teaser.OfShow -> actions.openShow(teaser.show.ref)
            }
        },
        onLongClick = { actions.openMenu(teaser) },
        onPlayPause = (teaser as? Teaser.OfEpisode)?.let { { actions.play(it.episode.ref, null) } },
    )
}

@Composable
fun MediaTile(
    state: TileState,
    modifier: Modifier = Modifier,
    width: Dp = RowTileWidth,
    singleLine: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPlayPause: (() -> Unit)? = null,
) {
    FocusBox(modifier = modifier.width(width), onClick = onClick, onLongClick = onLongClick, onPlayPause = onPlayPause) { focused ->
        val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "tileScale")
        Column(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            when (val t = state.teaser) {
                is Teaser.OfEpisode -> EpisodeTileBody(t.episode, state, focused, singleLine)
                is Teaser.OfShow -> ShowTileBody(t.show, state, focused, singleLine)
            }
        }
    }
}

private val ThumbShape = RoundedCornerShape(7.dp)

@Composable
private fun EpisodeTileBody(episode: Episode, state: TileState, focused: Boolean, singleLine: Boolean) {
    Thumb(focused) {
        Artwork(episode.imageUrl, episode.showTitle, Modifier.fillMaxSize(), seed = episode.ref.key.hashCode())
        TileOverlays(
            channel = episode.channelLabel, color = episode.broadcaster.color(),
            duration = formatClock(episode.durationSeconds.toLong()),
            newText = if (state.isNew) stringResource(R.string.badge_new) else null,
            bookmarked = state.bookmarked,
            progress = state.progressSeconds?.let { progressFraction(it, episode.durationSeconds) },
            watched = state.watched,
        )
    }
    TileTitle(episode.title, focused, singleLine)
    val suffix = episodeMetaSuffix(episode, state.progressSeconds, rememberNow(), state.playedAt)
    MetaLine(left = episode.showTitle, fixedRight = " · $suffix")
}

@Composable
private fun ShowTileBody(show: Show, state: TileState, focused: Boolean, singleLine: Boolean) {
    Thumb(focused) {
        Artwork(show.imageUrl, show.title, Modifier.fillMaxSize(), seed = 3)
        TileOverlays(
            channel = show.channelLabel, color = show.broadcaster.color(), duration = null,
            newText = if (state.newCount > 0) stringResource(R.string.badge_new_count, state.newCount) else null,
            bookmarked = state.bookmarked, progress = null, watched = false,
        )
    }
    TileTitle(show.title, focused, singleLine)
    val parts = buildList {
        show.genre?.let { add(it) }
        show.episodeCount?.takeIf { it > 1 }?.let { add(stringResource(R.string.episode_count, it)) }
        if (state.subscribed) add(stringResource(R.string.meta_subscribed))
    }
    MetaLine(left = parts.joinToString(" · "), fixedRight = "")
}

@Composable
private fun Thumb(focused: Boolean, content: @Composable () -> Unit) {
    val ring = 2.5.dp
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .drawBehind {
                if (focused) {
                    val r = ring.toPx()
                    drawRoundRect(
                        QColors.Focus, topLeft = Offset(-r, -r), size = Size(size.width + 2 * r, size.height + 2 * r),
                        cornerRadius = CornerRadius(7.dp.toPx() + r),
                    )
                }
            }
            .clip(ThumbShape)
            .background(Color(0xFF222222)),
    ) { content() }
}

@Composable
private fun TileTitle(text: String, focused: Boolean, singleLine: Boolean) {
    Text(
        text,
        style = QType.tileTitle,
        color = if (focused) Color.White else Color(0xFFD7DEE2),
        maxLines = if (singleLine) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
    )
}

/** Left part truncates, the right part (episode number, age) is measured first and never truncated. */
@Composable
private fun MetaLine(left: String, fixedRight: String) {
    Row(Modifier.padding(start = 2.dp, end = 2.dp, top = 3.dp)) {
        Text(
            left, style = QType.tileMeta, color = QColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (fixedRight.isNotEmpty()) {
            Text(fixedRight, style = QType.tileMeta, color = QColors.Muted, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun TileOverlays(
    channel: String,
    color: Color,
    duration: String?,
    newText: String?,
    bookmarked: Boolean,
    progress: Float?,
    watched: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        if (watched) {
            // Dimmed thumbnail, like YouTube; the badge and the full bar below say "watched".
            Box(Modifier.fillMaxSize().background(QColors.Background.copy(alpha = 0.5f)))
        }
        if (watched) {
            Badge(
                stringResource(R.string.badge_seen), Color.Black.copy(alpha = 0.78f), Color.White,
                Modifier.align(Alignment.TopStart).padding(5.dp),
            )
        } else if (newText != null) {
            Badge(newText, QColors.Accent, QColors.AccentInk, Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        if (bookmarked) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(19.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) { QIcon(R.drawable.ic_bookmark_fill, Color.White, size = 11.dp) }
        }
        Text(
            channel, style = QType.channel, color = Color.White, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(3.5.dp)).background(color)
                .padding(horizontal = 4.5.dp, vertical = 1.5.dp),
        )
        if (duration != null) {
            Badge(duration, Color.Black.copy(alpha = 0.78f), Color.White, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        val bar = tileProgressBar(progress, watched)
        if (bar != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.5.dp).background(Color.White.copy(alpha = 0.28f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bar).background(QColors.Accent))
            }
        }
    }
}

/** Width of the bar at the bottom of an episode thumbnail: full for watched, partial for started, none otherwise. */
fun tileProgressBar(progress: Float?, watched: Boolean): Float? = when {
    watched -> 1f
    progress != null && progress > 0f -> progress.coerceIn(0.02f, 1f)
    else -> null
}

@Composable
fun Badge(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = QType.badge, color = textColor, maxLines = 1, softWrap = false,
        modifier = modifier.clip(RoundedCornerShape(3.5.dp)).background(background).padding(horizontal = 4.5.dp, vertical = 1.5.dp),
    )
}
