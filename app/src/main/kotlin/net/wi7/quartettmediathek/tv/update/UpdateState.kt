package net.wi7.quartettmediathek.tv.update

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow
import net.wi7.quartettmediathek.update.ReleaseInfo

enum class UpdatePhase {
    Idle,
    Checking,
    UpToDate,
    CheckFailed,
    Downloading,
    /** "Install unknown apps" is not allowed yet; the system settings page was opened. */
    NeedsPermission,
    /** APK handed to the system installer; the system dialog takes over. */
    Installing,
    DownloadFailed,
    InstallFailed,
}

/** Everything the UI needs. [release] is set when a newer version exists. [progress] is 0..1 while downloading. */
data class UpdateUiState(
    val enabled: Boolean = false,
    val currentVersion: String = "",
    val lastCheckMs: Long = 0,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val release: ReleaseInfo? = null,
    val progress: Float = 0f,
    /** The start-up dialog was answered with "Later" recently. */
    val dialogDismissed: Boolean = false,
) {
    val busy get() = phase == UpdatePhase.Checking || phase == UpdatePhase.Downloading
}

/** What the UI may do; implemented by [AppUpdater]. */
interface UpdateController {
    val state: StateFlow<UpdateUiState>
    /** [manual] ignores the once-a-day limit. */
    fun check(manual: Boolean)
    fun install()
    fun dismissDialog()
}

val LocalUpdateController = staticCompositionLocalOf<UpdateController?> { null }
