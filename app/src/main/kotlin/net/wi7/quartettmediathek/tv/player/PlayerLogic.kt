package net.wi7.quartettmediathek.tv.player

import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Variant

/** Pure player state logic (no Android dependencies, unit tested). */
object PlayerLogic {
    const val OSD_TIMEOUT_MS = 4000L
    const val SAVE_INTERVAL_MS = 10_000L
    const val WATCHED_THRESHOLD = 0.95
    val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    /** The OSD hides [OSD_TIMEOUT_MS] after the last interaction, but only while playing. */
    fun shouldHideOsd(playing: Boolean, lastInteractionMs: Long, nowMs: Long, timeoutMs: Long = OSD_TIMEOUT_MS) =
        playing && nowMs - lastInteractionMs >= timeoutMs

    /** Next speed in [SPEEDS], wrapping around; unknown values restart at 1x. */
    fun nextSpeed(current: Float): Float {
        val i = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
        return if (i < 0) 1f else SPEEDS[(i + 1) % SPEEDS.size]
    }

    /** "1×", "1,25×" (German) or "1.25×" depending on [decimalSeparator]. */
    fun formatSpeed(speed: Float, decimalSeparator: Char): String {
        val text = when (speed) {
            0.75f -> "0.75"
            1.25f -> "1.25"
            1.5f -> "1.5"
            else -> if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
        }
        return text.replace('.', decimalSeparator) + "×"
    }

    /** The episode published right after [current] (list order does not matter), or null for the newest one. */
    fun nextEpisode(episodes: List<Episode>, current: EpisodeRef): Episode? {
        val byDate = episodes.sortedBy { it.publishedAt }
        val i = byDate.indexOfFirst { it.ref == current }
        return if (i < 0) null else byDate.getOrNull(i + 1)
    }

    fun isWatched(positionSeconds: Long, durationSeconds: Long): Boolean =
        durationSeconds > 0 && positionSeconds >= durationSeconds * WATCHED_THRESHOLD

    /**
     * Where playback starts: an explicit [requested] position wins (0 = from the beginning), otherwise the
     * saved position. A watched or (almost) finished episode restarts from 0.
     */
    fun startPosition(requested: Long?, saved: Long?, savedWatched: Boolean, durationSeconds: Long): Long {
        if (requested != null) return requested.coerceAtLeast(0)
        if (saved == null || savedWatched || isWatched(saved, durationSeconds)) return 0
        return saved.coerceAtLeast(0)
    }

    /** Variant to request: the subscription's choice if available, else standard, else whatever exists. */
    fun preferredVariant(subscriptionVariant: Variant?, available: Set<Variant>): Variant = when {
        subscriptionVariant != null && subscriptionVariant in available -> subscriptionVariant
        Variant.STANDARD in available -> Variant.STANDARD
        else -> available.firstOrNull() ?: Variant.STANDARD
    }

    /** "7:20" or "1:02:03". */
    fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** "+7:20" or "−0:10" with an optional step label such as " · 1 min". */
    fun seekHint(deltaSeconds: Long, stepLabel: String?): String {
        val sign = if (deltaSeconds < 0) "−" else "+"
        val base = sign + formatTime(kotlin.math.abs(deltaSeconds))
        return if (stepLabel != null) "$base · $stepLabel" else base
    }
}
