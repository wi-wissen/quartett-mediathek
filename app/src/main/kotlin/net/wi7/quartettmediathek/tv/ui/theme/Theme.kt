package net.wi7.quartettmediathek.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import net.wi7.quartettmediathek.tv.R

/** Colours from the mockup. Single dark look, as usual for TV apps. */
object QColors {
    val Background = Color(0xFF0C1013)
    val Surface = Color(0xFF151B20)
    val Surface2 = Color(0xFF1F272E)
    val Line = Color(0xFF2B353D)
    val Foreground = Color(0xFFEEF2F4)
    val Muted = Color(0xFF93A1AA)
    val Accent = Color(0xFFF4B63F)
    val AccentInk = Color(0xFF231A05)
    val Focus = Color(0xFFFFFFFF)
    val Ard = Color(0xFF4C7FE0)
    val Zdf = Color(0xFFEF8A2A)
    val ThreeSat = Color(0xFFC4478F)
    val Arte = Color(0xFFE0573E)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Display face: Bricolage Grotesque (OFL), body face: Figtree (OFL). Both bundled in res/font. */
val Display = FontFamily(variable(R.font.bricolage_grotesque, 600), variable(R.font.bricolage_grotesque, 700))
val Body = FontFamily(
    variable(R.font.figtree, 400), variable(R.font.figtree, 500),
    variable(R.font.figtree, 600), variable(R.font.figtree, 700),
)

private val typography = Typography(
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 42.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
)

@Composable
fun QuartettTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = QColors.Foreground,
            onPrimary = QColors.Background,
            secondary = QColors.Accent,
            background = QColors.Background,
            surface = QColors.Surface,
            onSurface = QColors.Foreground,
            surfaceVariant = QColors.Surface2,
            onSurfaceVariant = QColors.Muted,
            border = QColors.Focus,
        ),
        typography = typography,
        content = content,
    )
}
