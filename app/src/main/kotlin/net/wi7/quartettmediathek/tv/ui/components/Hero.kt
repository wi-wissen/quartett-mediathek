package net.wi7.quartettmediathek.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.theme.color

/**
 * Full-bleed backdrop (reaches the top edge, behind the header) anchored to the right. A smooth horizontal scrim from the
 * page background on the left keeps [content] readable; the bottom fades into the page background where the rows start.
 */
@Composable
fun Hero(
    height: Dp,
    imageUrl: String?,
    artTitle: String,
    seed: Int,
    modifier: Modifier = Modifier,
    contentWidth: Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = QColors.Background
    val header = LocalHeaderState.current
    // The shell then draws a light top shade instead of the opaque top fade, so the image stays visible under the header.
    DisposableEffect(header) {
        header.overImage = true
        onDispose { header.overImage = false }
    }
    Box(modifier.fillMaxWidth().height(height)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Artwork(imageUrl, artTitle, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(maxWidth * 0.88f), seed = seed)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(*HeroScrimStops.map { it.first to bg.copy(alpha = it.second) }.toTypedArray())))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*HeroBottomStops.map { it.first to bg.copy(alpha = it.second) }.toTypedArray())))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 28.dp).widthIn(max = contentWidth), content = content)
    }
}

/** Left-to-right scrim: (position, background alpha). Eased so no edge is visible; transparent from ~62 % width. */
internal val HeroScrimStops = listOf(0f to 1f, 0.12f to 0.97f, 0.25f to 0.88f, 0.38f to 0.68f, 0.5f to 0.42f, 0.62f to 0.15f, 0.75f to 0.03f, 1f to 0f)

/** Top-to-bottom fade into the page background (alpha), starting at mid height. */
internal val HeroBottomStops = listOf(0f to 0f, 0.5f to 0f, 0.65f to 0.2f, 0.8f to 0.55f, 0.92f to 0.9f, 1f to 1f)

/** "[ARD] NDR · Genre · 5 episodes" line above the hero title. */
@Composable
fun HeroKicker(broadcaster: Broadcaster, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            broadcaster.displayName, style = QType.channel.copy(fontSize = 9.sp), color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(broadcaster.color()).padding(horizontal = 5.dp, vertical = 1.5.dp),
        )
        Text(text.uppercase(), style = QType.kicker, color = QColors.Muted, maxLines = 1)
    }
}

/** Outlined fact badge such as HD / UT; either a [text] or an [icon]. */
@Composable
fun FactBadge(text: String? = null, @DrawableRes icon: Int? = null) {
    Row(
        Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(3.dp)).padding(horizontal = 4.5.dp, vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (text != null) Text(text, style = QType.factBadge, color = Color(0xFFCFD7DC), maxLines = 1)
        if (icon != null) QIcon(icon, Color(0xFFCFD7DC), size = 12.dp)
    }
}
