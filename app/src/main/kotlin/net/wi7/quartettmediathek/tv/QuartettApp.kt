package net.wi7.quartettmediathek.tv

import android.app.Application
import net.wi7.quartettmediathek.tv.di.AppContainer
import net.wi7.quartettmediathek.tv.di.DefaultAppContainer

class QuartettApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
