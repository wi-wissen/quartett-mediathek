package net.wi7.quartettmediathek.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

/**
 * Shown when a page could not be loaded. Takes focus itself (so focus never ends up in the rail, where OK would
 * navigate away) and shows the technical reason in small print, so it can be reported.
 */
@Composable
fun LoadError(detail: String?, onRetry: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(Modifier.padding(start = 24.dp, top = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.error_load), style = QType.body, color = QColors.Foreground)
        if (!detail.isNullOrBlank()) Text(detail, style = QType.rowSmall, color = QColors.Muted, maxLines = 3)
        QButton(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.focusRequester(focus), icon = R.drawable.ic_refresh)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
