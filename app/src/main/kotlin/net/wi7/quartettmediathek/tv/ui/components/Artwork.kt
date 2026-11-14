package net.wi7.quartettmediathek.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.math.abs

/**
 * Show or episode image. Without an [imageUrl] a landscape-like gradient is drawn (hue derived from [title], so a
 * show and its episodes match); with one, Coil loads it over the placeholder.
 */
@Composable
fun Artwork(imageUrl: String?, title: String, modifier: Modifier = Modifier, seed: Int = 0) {
    val palette = remember(title, seed) { Palette(title, seed) }
    Box(modifier.drawBehind {
        drawRect(Brush.verticalGradient(*palette.stops.toTypedArray()))
        val center = Offset(size.width * palette.sunX, size.height * palette.sunY)
        drawRect(
            Brush.radialGradient(
                colorStops = arrayOf(0f to palette.sunCore, 0.28f to palette.sunCore, 0.3f to palette.sunHalo, 1f to Color.Transparent),
                center = center,
                radius = size.minDimension * 0.65f,
            ),
        )
    }) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private class Palette(title: String, seed: Int) {
    private val h = (abs(title.hashCode()) % 360).toFloat()
    private val s = abs(seed)
    private val horizon = 0.48f + (s * 7 % 22) / 100f
    val sunX = (15 + (s * 29) % 70) / 100f
    val sunY = (18 + (s * 13) % 30) / 100f
    val sunCore = hsl(h + 35, 0.85f, 0.78f, 0.95f)
    val sunHalo = hsl(h + 35, 0.85f, 0.70f, 0.25f)
    val stops = listOf(
        0f to hsl(h, 0.42f, 0.30f),
        horizon to hsl(h + 18, 0.48f, 0.52f),
        horizon to hsl(h - 12, 0.38f, 0.22f),
        1f to hsl(h - 22, 0.40f, 0.11f),
    )
}

private fun hsl(hue: Float, s: Float, l: Float, a: Float = 1f) = Color.hsl(((hue % 360) + 360) % 360, s, l, a)
