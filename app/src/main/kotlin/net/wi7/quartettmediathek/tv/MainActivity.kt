package net.wi7.quartettmediathek.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import net.wi7.quartettmediathek.tv.player.LocalPlayerDependencies
import net.wi7.quartettmediathek.tv.player.PlayerDependencies
import kotlinx.coroutines.flow.first
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.navigation.AppShell
import net.wi7.quartettmediathek.tv.navigation.Routes
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme
import net.wi7.quartettmediathek.tv.update.LocalUpdateController
import net.wi7.quartettmediathek.tv.update.UpdateDialogHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as QuartettApp).container
        val updater = (application as QuartettApp).updater
        // At most once a day (the updater checks its stored time); does nothing when no update repository is set.
        updater.check(manual = false)
        setContent {
            QuartettTheme {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalUpdateController provides updater,
                    LocalPlayerDependencies provides PlayerDependencies(container.catalog, container.library, container.playback),
                ) {
                    // The start destination is the user's start screen; wait for it before building the NavHost.
                    val start by produceState<String?>(null) {
                        value = Routes.forStartScreen(container.settings.startScreen().first())
                    }
                    Box(Modifier.fillMaxSize().background(QColors.Background)) {
                        start?.let { AppShell(it) }
                        UpdateDialogHost()
                    }
                }
            }
        }
    }
}
