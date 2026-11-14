package net.wi7.quartettmediathek.tv

import android.app.Application
import net.wi7.quartettmediathek.tv.di.AppContainer
import net.wi7.quartettmediathek.tv.di.DefaultAppContainer
import net.wi7.quartettmediathek.tv.update.AppUpdater
import net.wi7.quartettmediathek.tv.work.RefreshWorker

class QuartettApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Self-updater; disabled (no repository) unless the build got `-PupdateRepo=owner/name`. */
    lateinit var updater: AppUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        updater = AppUpdater(this, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        // Never let background scheduling break the app start.
        runCatching { RefreshWorker.schedule(this) }
    }
}
