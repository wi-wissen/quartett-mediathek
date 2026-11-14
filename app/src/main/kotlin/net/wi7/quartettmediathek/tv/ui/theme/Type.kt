package net.wi7.quartettmediathek.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.wi7.quartettmediathek.model.Broadcaster

/** Text styles at TV density (mockup px / 2). Tabular digits where numbers change. */
object QType {
    val rowTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val rowSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    val tileTitle = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
    val tileMeta = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 10.5.sp)
    val badge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, lineHeight = 11.sp)
    val channel = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 8.5.sp, lineHeight = 10.sp, letterSpacing = 0.3.sp)
    val button = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
    val chip = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val kicker = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.7.sp)
    val heroTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp)
    val episodeHeroTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
    val facts = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val factBadge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.4.sp)
    val railLabel = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    val menuTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val menuItem = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val menuSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 10.sp)
    val key = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
}

fun Broadcaster.color() = when (this) {
    Broadcaster.ARD -> QColors.Ard
    Broadcaster.ZDF -> QColors.Zdf
    Broadcaster.THREESAT -> QColors.ThreeSat
    Broadcaster.ARTE -> QColors.Arte
}
