package net.wi7.quartettmediathek.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

@Composable
fun QIcon(@DrawableRes id: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/**
 * Pill button. Primary = white fill (dark text); secondary = [QColors.Surface2] with a white ring when focused.
 * The label never wraps.
 */
@Composable
fun QButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean = false,
) {
    FocusBox(modifier = modifier, onClick = onClick) { focused ->
        val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(120), label = "btnScale")
        val bg = if (primary) QColors.Foreground else QColors.Surface2
        val fg = if (primary) QColors.Background else QColors.Foreground
        val ring = when {
            !focused -> Color.Transparent
            primary -> Color.White.copy(alpha = 0.55f)
            else -> QColors.Focus
        }
        Row(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(if (primary && focused) QColors.Focus else bg)
                .border(2.dp, ring, CircleShape)
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) QIcon(icon, fg)
            Text(text, style = QType.button, color = fg, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    }
}

/** Filter chip (broadcasters on Discover). [selected] = current filter, focus = white fill and ring. */
@Composable
fun QChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusBox(modifier = modifier, onClick = onClick) { focused ->
        val bg = when {
            focused -> QColors.Focus
            selected -> Color(0xFFE8EDF0)
            else -> QColors.Surface2
        }
        val fg = if (focused || selected) QColors.Background else Color(0xFFD7DEE2)
        Text(
            text, style = QType.chip, color = fg, maxLines = 1, softWrap = false,
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .border(2.dp, if (focused) QColors.Focus else Color.Transparent, CircleShape)
                .padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}
