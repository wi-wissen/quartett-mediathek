package net.wi7.quartettmediathek.source.mvw

import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.SubtitleFormat
import net.wi7.quartettmediathek.model.SubtitleTrack
import net.wi7.quartettmediathek.model.Variant

/**
 * ARD item id of an MVW hit: last path segment of `url_website` (`https://www.ardmediathek.de/video/{id}`).
 * Null for entries that do not point to the ARD Mediathek.
 */
val MvwEntry.ardItemId: String?
    get() = ardItemIdOf(urlWebsite)

/** ARD item id of an `ardmediathek.de` page URL, or null for other hosts. */
fun ardItemIdOf(url: String?): String? {
    if (url == null) return null
    if (!url.substringAfter("://").substringBefore('/').endsWith("ardmediathek.de")) return null
    return url.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/').takeIf { it.isNotEmpty() }
}

/** Maps a hit to an [Episode], or null if the channel is out of scope (SRF, ORF, KiKA, ...). */
fun MvwEntry.toEpisode(): Episode? {
    val broadcaster = Broadcaster.fromMvwChannel(channel) ?: return null
    return Episode(
        ref = EpisodeRef(SourceId.MVW, id),
        showRef = ShowRef(SourceId.MVW, topic),
        showTitle = topic,
        title = title,
        broadcaster = broadcaster,
        channelLabel = channel,
        description = description,
        imageUrl = null,
        durationSeconds = (duration ?: 0L).toInt(),
        publishedAt = Instant.fromEpochSeconds(timestamp),
        hasSubtitles = urlSubtitle != null,
        variants = variantsOf(title),
        maxHeight = if (urlVideoHd != null) 720 else null,
        websiteUrl = urlWebsite,
    )
}

fun List<MvwEntry>.toEpisodes(): List<Episode> = mapNotNull { it.toEpisode() }

/** Plain MP4 stream of a hit (best of hd/standard/low), as used for 3sat. */
fun MvwEntry.toStreamInfo(): StreamInfo? {
    val (url, height) = when {
        urlVideoHd != null -> urlVideoHd to 720
        urlVideo != null -> urlVideo to 540
        urlVideoLow != null -> urlVideoLow to 360
        else -> return null
    }
    val subs = urlSubtitle?.let {
        val format = if (it.substringBefore('?').endsWith(".vtt")) SubtitleFormat.WEBVTT else SubtitleFormat.EBU_TT
        listOf(SubtitleTrack(it, format, "de"))
    }.orEmpty()
    return StreamInfo(url = url, mimeType = "video/mp4", height = height, subtitles = subs)
}

private fun variantsOf(title: String): Set<Variant> {
    val t = title.lowercase()
    return when {
        "audiodeskription" in t || "hörfassung" in t -> setOf(Variant.AUDIO_DESCRIPTION)
        "gebärdensprache" in t || "(dgs)" in t -> setOf(Variant.SIGN_LANGUAGE)
        "(originalversion)" in t || "(ov)" in t || "(omu)" in t -> setOf(Variant.ORIGINAL_VERSION)
        else -> setOf(Variant.STANDARD)
    }
}
