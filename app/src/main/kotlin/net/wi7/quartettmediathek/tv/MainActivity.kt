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
import kotlinx.coroutines.flow.first
import net.wi7.quartettmediathek.tv.di.LocalAppContainer
import net.wi7.quartettmediathek.tv.navigation.AppShell
import net.wi7.quartettmediathek.tv.navigation.Routes
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as QuartettApp).container
        setContent {
            QuartettTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    // The start destination is the user's start screen; wait for it before building the NavHost.
                    val start by produceState<String?>(null) {
                        value = Routes.forStartScreen(container.settings.startScreen().first())
                    }
                    Box(Modifier.fillMaxSize().background(QColors.Background)) {
                        start?.let { AppShell(it) }
                    }
                }
            }
        }
    }
}
