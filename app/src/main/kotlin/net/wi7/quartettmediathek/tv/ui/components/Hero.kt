package net.wi7.quartettmediathek.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/** Full-width image area with a left and bottom scrim; [content] sits at the bottom left. */
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
    Box(modifier.fillMaxWidth().height(height)) {
        Artwork(imageUrl, artTitle, Modifier.fillMaxSize(), seed = seed)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to bg.copy(alpha = 0.96f), 0.45f to bg.copy(alpha = 0.75f), 0.8f to bg.copy(alpha = 0.1f), 1f to Color.Transparent,
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to bg)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 28.dp).widthIn(max = contentWidth), content = content)
    }
}

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
