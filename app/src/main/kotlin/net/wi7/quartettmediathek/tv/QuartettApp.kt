package net.wi7.quartettmediathek.tv

import android.app.Application
import net.wi7.quartettmediathek.tv.di.AppContainer
import net.wi7.quartettmediathek.tv.di.DefaultAppContainer
import net.wi7.quartettmediathek.tv.work.RefreshWorker

class QuartettApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        RefreshWorker.schedule(this)
    }
}
