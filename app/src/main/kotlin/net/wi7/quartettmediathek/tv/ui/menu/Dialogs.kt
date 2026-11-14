package net.wi7.quartettmediathek.tv.ui.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.ui.components.QIcon
import net.wi7.quartettmediathek.tv.ui.focus.FocusBox
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme

/**
 * Modal card on a dark scrim. Back dismisses. The first [MenuRow] that gets [initialFocus] takes focus when the
 * dialog opens (a dialog is its own window, so focus has to be requested explicitly).
 */
@Composable
fun QDialog(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    width: Dp = 320.dp,
    initialFocus: FocusRequester,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        QuartettTheme {
            Box(Modifier.fillMaxSize().background(Color(0xB8050709)), contentAlignment = Alignment.Center) {
                Column(Modifier.width(width).clip(RoundedCornerShape(11.dp)).background(QColors.Surface).padding(15.dp)) {
                    Text(title, style = QType.menuTitle, color = QColors.Foreground, maxLines = 2)
                    if (subtitle != null) {
                        Text(subtitle, style = QType.rowSmall.copy(fontSize = 10.5.sp), color = QColors.Muted, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                    Column(Modifier.padding(top = 10.dp)) { content() }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        // The dialog content is composed in its own window; give it a frame before focusing.
        androidx.compose.runtime.withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }
}

/** Menu entry: icon + text, optionally with a description and a toggle on the right. */
@Composable
fun MenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    description: String? = null,
    toggle: Boolean? = null,
) {
    FocusBox(modifier = modifier.fillMaxWidth().padding(vertical = 1.dp), onClick = onClick) { focused ->
        val fg = if (focused) QColors.Background else QColors.Foreground
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(if (focused) QColors.Focus else Color.Transparent)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (icon != null) QIcon(icon, fg, size = 15.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = QType.menuItem, color = fg, maxLines = 2)
                if (description != null) {
                    Text(description, style = QType.menuSmall, color = if (focused) Color(0xFF3B4650) else QColors.Muted, maxLines = 2)
                }
            }
            if (toggle != null) QToggle(toggle)
        }
    }
}

@Composable
fun QToggle(on: Boolean) {
    Box(
        Modifier.width(33.dp).height(19.dp).clip(CircleShape).background(if (on) QColors.Accent else QColors.Line),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(2.dp).size(15.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFFCFD7DC)))
    }
}
