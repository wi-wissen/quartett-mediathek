package net.wi7.quartettmediathek.model

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Stores an [Instant] as epoch milliseconds (used for cached catalog data and library snapshots). */
object EpochMillisInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("EpochMillisInstant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())
    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
}

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

@Serializable
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

@Serializable
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
    @Serializable(with = EpochMillisInstantSerializer::class)
    val publishedAt: Instant,
    @Serializable(with = EpochMillisInstantSerializer::class)
    val availableUntil: Instant? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val hasSubtitles: Boolean = false,
    /** Variants that exist for this episode (always contains [Variant.STANDARD] unless only an AD file exists). */
    val variants: Set<Variant> = setOf(Variant.STANDARD),
    val maxHeight: Int? = null,
    val websiteUrl: String? = null,
    /**
     * Where each version of this episode lives (set when separate files exist per version, e.g. MediathekViewWeb:
     * standard, English, audio description). Empty for sources that deliver one entry per episode.
     */
    val versionRefs: Map<Variant, EpisodeRef> = emptyMap(),
)

/** Something a row on Home/Discover can show: either a whole show or a single episode. */
@Serializable
sealed interface Teaser {
    @Serializable @SerialName("show")
    data class OfShow(val show: Show) : Teaser
    @Serializable @SerialName("episode")
    data class OfEpisode(val episode: Episode) : Teaser
}

/** Unified Discover categories (concept: "Entdecken"). Unknown broadcaster rows fall into [HIGHLIGHTS]. */
@Serializable
enum class DiscoverCategory { HIGHLIGHTS, SERIES, DOCUMENTARIES, KNOWLEDGE_CULTURE, MOVIES }

/** An editorial row as delivered by a broadcaster's start page, before mapping to [DiscoverCategory]. */
@Serializable
data class SourceRow(val broadcaster: Broadcaster, val title: String, val items: List<Teaser>)

@Serializable
data class DiscoverRow(val category: DiscoverCategory, val items: List<Teaser>)

@Serializable
data class SubtitleTrack(val url: String, val format: SubtitleFormat, val language: String?)

@Serializable
enum class SubtitleFormat { WEBVTT, EBU_TT, TTML }

/** A playable stream resolved right before playback (stream URLs may expire, never cache them long). */
@Serializable
data class StreamInfo(
    val url: String,
    val mimeType: String,
    val height: Int?,
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Audio track languages/classes, e.g. "main", "ad" (ZDF delivers audio description as a separate track). */
    val audioTracks: List<String> = emptyList(),
    /**
     * The same video as separate progressive files (same audio version, same format as [url]), best first, one per
     * height. Includes the file behind [url]. Empty when the source only offers an adaptive (HLS) stream; the player
     * then selects the quality among the stream's own tracks.
     */
    val alternatives: List<StreamVariant> = emptyList(),
)

/** One progressive file of a stream at a fixed resolution. */
@Serializable
data class StreamVariant(val height: Int, val url: String, val mimeType: String)

/** Keeps the first entry per height, sorted by height descending; entries without a height are dropped. */
fun List<StreamVariant>.distinctByHeightDesc(): List<StreamVariant> =
    filter { it.height > 0 }.distinctBy { it.height }.sortedByDescending { it.height }
