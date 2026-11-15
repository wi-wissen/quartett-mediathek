package net.wi7.quartettmediathek.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.theme.Body
import net.wi7.quartettmediathek.tv.ui.theme.Display
import net.wi7.quartettmediathek.tv.ui.theme.QColors

private val OsdMuted = Color(0xFFC9D2D7)

/** Pure display data of the OSD, so the composable has no player dependency. */
data class OsdModel(
    val showTitle: String,
    val channel: String,
    val infoRight: String,
    val episodeTitle: String,
    val subLine: String,
    val positionSeconds: Long,
    val durationSeconds: Long,
    /** Scrub target while a seek is in progress or just finished, with its label. */
    val seekTargetSeconds: Long?,
    val seekHint: String?,
    val playing: Boolean,
    val speedLabel: String,
    /** The version button is only shown when there is more than one version. */
    val versionAvailable: Boolean,
    val versionLabel: String,
    /** The quality button is only shown when there is more than one resolution. */
    val qualityAvailable: Boolean,
    val qualityLabel: String,
    val subtitlesAvailable: Boolean,
    val subtitlesLabel: String,
    val bookmarked: Boolean,
    val bookmarkLabel: String,
    val hasNext: Boolean,
    val nextLabel: String,
)

class OsdActions(
    val onPlayPause: () -> Unit,
    val onSpeed: () -> Unit,
    val onVersion: () -> Unit,
    val onQuality: () -> Unit,
    val onSubtitles: () -> Unit,
    val onBookmark: () -> Unit,
    val onNext: () -> Unit,
    val onBarFocus: (Boolean) -> Unit,
)

@Composable
fun PlayerShade(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.6f),
                0.4f to Color.Transparent,
                0.58f to Color.Black.copy(alpha = 0.2f),
                1f to Color.Black.copy(alpha = 0.92f),
            ),
        ),
    )
}

@Composable
fun PlayerOsd(model: OsdModel, actions: OsdActions, playPauseFocus: FocusRequester, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        TopRow(model, Modifier.align(Alignment.TopCenter))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 32.dp),
        ) {
            Text(
                model.episodeTitle,
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.subLine,
                style = TextStyle(fontFamily = Body, fontSize = 12.sp), color = OsdMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp, bottom = 17.dp),
            )
            ProgressBar(model, actions)
            Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val timeStyle = TextStyle(fontFamily = Body, fontSize = 11.sp)
                Text(PlayerLogic.formatTime(model.positionSeconds), style = timeStyle, color = OsdMuted)
                Text(
                    "-" + PlayerLogic.formatTime(model.durationSeconds - model.positionSeconds),
                    style = timeStyle, color = OsdMuted,
                )
            }
            Buttons(model, actions, playPauseFocus)
        }
    }
}

@Composable
private fun TopRow(model: OsdModel, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = TextStyle(fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Normal)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(model.showTitle) }
                append(" · ")
                append(model.channel)
            },
            style = style, color = OsdMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(model.infoRight, style = style, color = OsdMuted, maxLines = 1, modifier = Modifier.padding(start = 24.dp))
    }
}

@Composable
private fun ProgressBar(model: OsdModel, actions: OsdActions) {
    var focused by remember { mutableStateOf(false) }
    val duration = model.durationSeconds.coerceAtLeast(1)
    val fraction = (model.positionSeconds.toFloat() / duration).coerceIn(0f, 1f)
    val barHeight = if (focused) 8.dp else 5.dp
    val knob = if (focused) 17.dp else 13.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(34.dp)
            .onFocusChanged { focused = it.isFocused; actions.onBarFocus(it.isFocused) }
            .clickable(onClick = actions.onPlayPause),
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        val ring = if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(6.dp)) else Modifier
        Box(Modifier.fillMaxWidth().height(barHeight).then(ring).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(QColors.Accent))
        }
        Box(Modifier.offset(x = width * fraction - knob / 2).size(knob).clip(CircleShape).background(Color.White))
        model.seekTargetSeconds?.let { target ->
            val tf = (target.toFloat() / duration).coerceIn(0f, 1f)
            Box(Modifier.offset(x = width * tf - 1.5.dp).size(width = 3.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(QColors.Accent))
            model.seekHint?.let { hint ->
                Text(
                    hint,
                    style = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    color = QColors.Background, maxLines = 1, softWrap = false,
                    modifier = Modifier.hintPlacement(tf).clip(RoundedCornerShape(5.dp)).background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Places the label centred above the bar at [fraction], clamped into the parent width. */
private fun Modifier.hintPlacement(fraction: Float): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val parentWidth = constraints.maxWidth
    val x = ((parentWidth * fraction).toInt() - p.width / 2).coerceIn(0, (parentWidth - p.width).coerceAtLeast(0))
    layout(parentWidth, p.height) { p.place(x, -p.height / 2 - 34) }
}

@Composable
private fun Buttons(model: OsdModel, actions: OsdActions, playPauseFocus: FocusRequester) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            icon = if (model.playing) R.drawable.ic_pause_fill else R.drawable.ic_play_arrow_fill,
            label = null, onClick = actions.onPlayPause, modifier = Modifier.focusRequester(playPauseFocus),
        )
        Spacer(Modifier.weight(1f))
        if (model.versionAvailable) PillButton(R.drawable.ic_translate, model.versionLabel, actions.onVersion)
        if (model.qualityAvailable) PillButton(R.drawable.ic_hd, model.qualityLabel, actions.onQuality)
        PillButton(R.drawable.ic_speed, model.speedLabel, actions.onSpeed)
        if (model.subtitlesAvailable) {
            PillButton(R.drawable.ic_closed_caption, model.subtitlesLabel, actions.onSubtitles)
        }
        PillButton(
            if (model.bookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
            model.bookmarkLabel, actions.onBookmark,
        )
        if (model.hasNext) PillButton(R.drawable.ic_skip_next_fill, model.nextLabel, actions.onNext)
    }
}

@Composable
private fun PillButton(icon: Int, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val content = if (focused) QColors.Background else Color.White
    Row(
        modifier.height(38.dp).widthIn(min = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = content, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(
                label, color = content, maxLines = 1, softWrap = false,
                style = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
            )
        }
    }
}
