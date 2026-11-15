package net.wi7.quartettmediathek.tv.player

import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Variant

/**
 * One selectable version of the episode. [ref] is where its stream comes from; [audioTrack] means it is an audio
 * track of the stream that already plays (ZDF audio description), so no new stream is resolved.
 */
data class VersionOption(val variant: Variant, val ref: EpisodeRef, val audioTrack: Boolean = false)

/** One entry of the quality chooser; a null [height] is "automatic" (best file, or adaptive streaming). */
data class QualityOption(val height: Int?) {
    val isAuto get() = height == null
}

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

    /** Order of the version chooser. */
    val VERSION_ORDER = listOf(Variant.STANDARD, Variant.ORIGINAL_VERSION, Variant.AUDIO_DESCRIPTION, Variant.SIGN_LANGUAGE)

    /**
     * Versions to offer while [playedRef] plays [stream]: every ref in [versionRefs] (separate files, e.g. from
     * MediathekViewWeb) plus, when no audio description file exists but the stream has an "ad" audio track (ZDF),
     * that track (and the main track as standard). Sorted by [VERSION_ORDER]. One entry or none means: no chooser.
     */
    fun versionOptions(versionRefs: Map<Variant, EpisodeRef>, playedRef: EpisodeRef, stream: StreamInfo): List<VersionOption> {
        val options = versionRefs.map { (variant, ref) -> VersionOption(variant, ref) }.toMutableList()
        if (Variant.AUDIO_DESCRIPTION !in versionRefs && "ad" in stream.audioTracks) {
            options += VersionOption(Variant.AUDIO_DESCRIPTION, playedRef, audioTrack = true)
            if (Variant.STANDARD !in versionRefs) options += VersionOption(Variant.STANDARD, playedRef, audioTrack = true)
        }
        return options.sortedBy { VERSION_ORDER.indexOf(it.variant) }
    }

    /**
     * The version that actually plays: the one whose ref is [playedRef], else audio description when it was
     * requested and the stream has the "ad" track, else standard.
     */
    fun activeVersion(versionRefs: Map<Variant, EpisodeRef>, playedRef: EpisodeRef, requested: Variant, stream: StreamInfo): Variant =
        versionRefs.entries.firstOrNull { it.value == playedRef }?.key
            ?: if (requested == Variant.AUDIO_DESCRIPTION && "ad" in stream.audioTracks) Variant.AUDIO_DESCRIPTION else Variant.STANDARD


    /**
     * Quality chooser entries: Automatic plus every resolution, best first. Progressive files come from
     * [StreamInfo.alternatives]; for adaptive streams (HLS) [trackHeights] are the video tracks ExoPlayer reports.
     * Fewer than two resolutions means nothing to choose, so the list is empty (button hidden).
     */
    fun qualityOptions(stream: StreamInfo, trackHeights: List<Int>): List<QualityOption> {
        val heights = if (stream.alternatives.size >= 2) stream.alternatives.map { it.height } else trackHeights
        val distinct = heights.filter { it > 0 }.distinct().sortedDescending()
        return if (distinct.size < 2) emptyList() else listOf(QualityOption(null)) + distinct.map { QualityOption(it) }
    }

    /**
     * The option that applies for the remembered wish [preferredHeight] (null = automatic): the exact height, else the
     * best one below it, else the lowest one. Without a wish, or without options, automatic.
     */
    fun selectedQuality(options: List<QualityOption>, preferredHeight: Int?): QualityOption {
        val auto = QualityOption(null)
        if (preferredHeight == null || options.isEmpty()) return auto
        val heights = options.mapNotNull { it.height }.sortedDescending()
        val pick = heights.firstOrNull { it <= preferredHeight } ?: heights.lastOrNull() ?: return auto
        return QualityOption(pick)
    }

    /** File to play for [quality]: the matching progressive file, otherwise the stream's own URL. */
    fun qualityUrl(stream: StreamInfo, quality: QualityOption): String =
        stream.alternatives.firstOrNull { it.height == quality.height }?.url ?: stream.url

    /** Resolution to show in the info line. */
    fun shownHeight(stream: StreamInfo, quality: QualityOption): Int? = quality.height ?: stream.height

    /** Heights of the selectable video tracks, e.g. from ExoPlayer's `Tracks`: best first, no duplicates. */
    fun distinctHeights(heights: List<Int>): List<Int> = heights.filter { it > 0 }.distinct().sortedDescending()
}
