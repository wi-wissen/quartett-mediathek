package net.wi7.quartettmediathek.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Identifies a show at its origin. [source] tells which client can load it; [key] is the source-specific id
 * (ARD grouping id, ZDF canonical, arte programId, or the MediathekViewWeb topic for 3sat and fallbacks).
 */
@Serializable
data class ShowRef(val source: SourceId, val key: String)

/** Identifies a single episode at its origin, analogous to [ShowRef]. */
@Serializable
data class EpisodeRef(val source: SourceId, val key: String)

@Serializable
enum class SourceId { ARD, ZDF, ARTE, MVW }

/** Accessible or alternative versions of the same episode. */
@Serializable
enum class Variant { STANDARD, AUDIO_DESCRIPTION, SIGN_LANGUAGE, ORIGINAL_VERSION }

data class Show(
    val ref: ShowRef,
    val title: String,
    val broadcaster: Broadcaster,
    /** Sub-channel shown on tiles, e.g. "NDR", "ZDFneo". */
    val channelLabel: String,
    val genre: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val episodeCount: Int? = null,
)

data class Episode(
    val ref: EpisodeRef,
    val showRef: ShowRef?,
    val showTitle: String,
    val title: String,
    val broadcaster: Broadcaster,
    val channelLabel: String,
    val description: String?,
    val imageUrl: String?,
    val durationSeconds: Int,
    val publishedAt: Instant,
    val availableUntil: Instant? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val hasSubtitles: Boolean = false,
    /** Variants that exist for this episode (always contains [Variant.STANDARD] unless only an AD file exists). */
    val variants: Set<Variant> = setOf(Variant.STANDARD),
    val maxHeight: Int? = null,
    val websiteUrl: String? = null,
)

/** Something a row on Home/Discover can show: either a whole show or a single episode. */
sealed interface Teaser {
    data class OfShow(val show: Show) : Teaser
    data class OfEpisode(val episode: Episode) : Teaser
}

/** Unified Discover categories (concept: "Entdecken"). Unknown broadcaster rows fall into [HIGHLIGHTS]. */
enum class DiscoverCategory { HIGHLIGHTS, SERIES, DOCUMENTARIES, KNOWLEDGE_CULTURE, MOVIES }

/** An editorial row as delivered by a broadcaster's start page, before mapping to [DiscoverCategory]. */
data class SourceRow(val broadcaster: Broadcaster, val title: String, val items: List<Teaser>)

data class DiscoverRow(val category: DiscoverCategory, val items: List<Teaser>)

data class SubtitleTrack(val url: String, val format: SubtitleFormat, val language: String?)

enum class SubtitleFormat { WEBVTT, EBU_TT, TTML }

/** A playable stream resolved right before playback (stream URLs may expire, never cache them long). */
data class StreamInfo(
    val url: String,
    val mimeType: String,
    val height: Int?,
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Audio track languages/classes, e.g. "main", "ad" (ZDF delivers audio description as a separate track). */
    val audioTracks: List<String> = emptyList(),
)
