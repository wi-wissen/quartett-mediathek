package net.wi7.quartettmediathek.tv.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.components.QButton
import net.wi7.quartettmediathek.tv.ui.menu.QDialog
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType

/** Status line for the current phase, or null when there is nothing to say. Shared by Settings and the dialog. */
@Composable
fun updateStatusText(state: UpdateUiState): String? = when (state.phase) {
    UpdatePhase.Checking -> stringResource(R.string.update_checking)
    UpdatePhase.UpToDate -> stringResource(R.string.update_up_to_date)
    UpdatePhase.CheckFailed -> stringResource(R.string.update_check_failed)
    UpdatePhase.Downloading -> stringResource(
        R.string.update_downloading, state.release?.versionName.orEmpty(), (state.progress * 100).toInt(),
    )
    UpdatePhase.NeedsPermission -> stringResource(R.string.update_needs_permission)
    UpdatePhase.Installing -> stringResource(R.string.update_installing)
    UpdatePhase.DownloadFailed -> stringResource(R.string.update_download_failed)
    UpdatePhase.InstallFailed -> stringResource(R.string.update_install_failed)
    UpdatePhase.Idle -> null
}

@Composable
fun UpdateProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(QColors.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(QColors.Accent))
    }
}

/** Start-up notice: shown while a newer version is known and "Later" was not chosen within the last day. */
@Composable
fun UpdateDialogHost() {
    val controller = LocalUpdateController.current ?: return
    val state by controller.state.collectAsStateWithLifecycle()
    if (state.enabled && state.release != null && !state.dialogDismissed) {
        UpdateDialog(state, onInstall = controller::install, onLater = controller::dismissDialog)
    }
}

@Composable
fun UpdateDialog(state: UpdateUiState, onInstall: () -> Unit, onLater: () -> Unit) {
    val release = state.release ?: return
    val first = remember { FocusRequester() }
    val status = updateStatusText(state)
    QDialog(
        title = stringResource(R.string.update_dialog_title, release.versionName),
        subtitle = null, onDismiss = onLater, width = 420.dp, initialFocus = first,
    ) {
        Text(
            stringResource(R.string.update_dialog_text, state.currentVersion),
            style = QType.rowSmall.copy(fontSize = 11.sp), color = QColors.Muted,
        )
        if (status != null) {
            Text(status, style = QType.rowSmall.copy(fontSize = 11.sp), color = QColors.Foreground, modifier = Modifier.padding(top = 10.dp))
        }
        if (state.phase == UpdatePhase.Downloading) UpdateProgressBar(state.progress, Modifier.padding(top = 8.dp))
        if (state.phase != UpdatePhase.Downloading) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QButton(stringResource(R.string.update_dialog_install), onInstall, Modifier.focusRequester(first), primary = true)
                QButton(stringResource(R.string.update_dialog_later), onLater)
            }
        }
    }
}
