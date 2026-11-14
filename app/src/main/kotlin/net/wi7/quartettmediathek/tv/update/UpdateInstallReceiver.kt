package net.wi7.quartettmediathek.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import net.wi7.quartettmediathek.tv.QuartettApp

/**
 * Receives the status of the install session committed by [AppUpdater]. [PackageInstaller.STATUS_PENDING_USER_ACTION]
 * carries the system confirmation screen as `EXTRA_INTENT`, which has to be started from here.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        (context.applicationContext as? QuartettApp)?.updater?.onInstallStatus(status)
    }

    companion object {
        const val Action = "net.wi7.quartettmediathek.tv.UPDATE_INSTALL_RESULT"
    }
}
