package net.wi7.quartettmediathek.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QuartettTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuartettTheme {
                // TODO(ui): replace with the app shell (rail + NavHost), see docs/SPEC.md "TV UI".
                Box(Modifier.fillMaxSize().background(QColors.Background), contentAlignment = Alignment.Center) {
                    Text(getString(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = QColors.Foreground)
                }
            }
        }
    }
}
