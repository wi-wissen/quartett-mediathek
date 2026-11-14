package net.wi7.quartettmediathek.tv.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.PendingIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wi7.quartettmediathek.update.ReleaseInfo
import net.wi7.quartettmediathek.update.availableUpdate
import net.wi7.quartettmediathek.update.isCheckDue
import net.wi7.quartettmediathek.update.releasesApiUrl
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater: asks the GitHub releases API for the newest release of [repo] (set at build time, empty = disabled),
 * downloads `quartett-mediathek.apk` into the cache directory and hands it to the system installer through the
 * [PackageInstaller] session API. The result arrives in [UpdateInstallReceiver].
 */
class AppUpdater(
    context: Context,
    private val repo: String,
    private val installedCode: Int,
    installedName: String,
    private val now: () -> Long = System::currentTimeMillis,
) : UpdateController {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val apiUrl = releasesApiUrl(repo)
    private var job: Job? = null

    private val _state = MutableStateFlow(
        UpdateUiState(
            enabled = apiUrl != null,
            currentVersion = installedName,
            lastCheckMs = prefs.getLong(KeyLastCheck, 0),
            release = prefs.getString(KeyLatestJson, null)?.let { availableUpdate(it, installedCode) },
            dialogDismissed = !isCheckDue(prefs.getLong(KeyDialogDismissed, 0), now()),
        ),
    )
    override val state: StateFlow<UpdateUiState> = _state

    init {
        // A finished or abandoned download is never needed again.
        scope.launch(Dispatchers.IO) { runCatching { cacheDir().deleteRecursively() } }
    }

    override fun check(manual: Boolean) {
        val url = apiUrl ?: return
        val s = _state.value
        if (s.busy || s.phase == UpdatePhase.Installing) return
        if (!manual && !isCheckDue(s.lastCheckMs, now())) return
        _state.update { it.copy(phase = UpdatePhase.Checking) }
        job = scope.launch {
            val body = withContext(Dispatchers.IO) { runCatching { httpGet(url) }.getOrNull() }
            if (body == null) {
                _state.update { it.copy(phase = UpdatePhase.CheckFailed) }
                return@launch
            }
            val t = now()
            val release = availableUpdate(body, installedCode)
            prefs.edit().putLong(KeyLastCheck, t).putString(KeyLatestJson, body).apply()
            _state.update {
                it.copy(
                    lastCheckMs = t, release = release,
                    phase = if (release == null) UpdatePhase.UpToDate else UpdatePhase.Idle,
                )
            }
        }
    }

    override fun dismissDialog() {
        prefs.edit().putLong(KeyDialogDismissed, now()).apply()
        _state.update { it.copy(dialogDismissed = true) }
    }

    override fun install() {
        val release = _state.value.release ?: return
        if (_state.value.busy || _state.value.phase == UpdatePhase.Installing) return
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(phase = UpdatePhase.NeedsPermission) }
            openUnknownSourcesSettings()
            return
        }
        _state.update { it.copy(phase = UpdatePhase.Downloading, progress = 0f) }
        job = scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { download(release) { p -> _state.update { it.copy(progress = p) } } }.getOrNull()
            }
            if (file == null) {
                _state.update { it.copy(phase = UpdatePhase.DownloadFailed) }
                return@launch
            }
            val started = withContext(Dispatchers.IO) { runCatching { commitSession(file) }.isSuccess }
            _state.update { it.copy(phase = if (started) UpdatePhase.Installing else UpdatePhase.InstallFailed) }
        }
    }

    /** Called by [UpdateInstallReceiver] with the final [PackageInstaller] status. */
    fun onInstallStatus(status: Int) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit // the process is replaced; nothing to show
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.update { it.copy(phase = UpdatePhase.Idle) }
            else -> _state.update { it.copy(phase = UpdatePhase.InstallFailed) }
        }
    }

    private fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    private fun cacheDir() = File(app.cacheDir, "updates")

    private fun httpGet(url: String): String {
        val c = open(url).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true // github.com -> objects.githubusercontent.com (https to https)
        setRequestProperty("User-Agent", "QuartettMediathek-Updater")
    }

    private suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File {
        val dir = cacheDir().apply { mkdirs() }
        val part = File(dir, "quartett-mediathek.apk.part")
        val target = File(dir, "quartett-mediathek.apk")
        val c = open(release.apkUrl)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = if (release.apkSize > 0) release.apkSize else c.contentLengthLong
            var done = 0L
            var lastReported = -1
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != lastReported) {
                            lastReported = pct
                            onProgress(pct / 100f)
                        }
                    }
                }
            }
            if (release.apkSize > 0 && done != release.apkSize) error("incomplete download: $done of ${release.apkSize}")
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            c.disconnect()
        }
        target.delete()
        if (!part.renameTo(target)) error("cannot move download")
        return target
    }

    /** Streams [apk] into an install session and commits it; the result is broadcast to [UpdateInstallReceiver]. */
    private fun commitSession(apk: File) {
        val installer = app.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("package", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            // Explicit intent + FLAG_MUTABLE: the system adds the status extras to it (required for sessions).
            val intent = Intent(app, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.Action)
            val pending = PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    private companion object {
        const val PrefsName = "updater"
        const val KeyLastCheck = "last_check_ms"
        const val KeyLatestJson = "latest_json"
        const val KeyDialogDismissed = "dialog_dismissed_ms"
    }
}
