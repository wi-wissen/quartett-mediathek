package net.wi7.quartettmediathek.library

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant

internal val LibraryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Stable keys used in the database. */
object LibraryKeys {
    fun episode(ref: EpisodeRef): String = "${ref.source.name}:${ref.key}"

    /** Bookmark key: `show:<source>:<key>` or `episode:<source>:<key>`. Use with `isBookmarked`. */
    fun bookmark(teaser: Teaser): String = when (teaser) {
        is Teaser.OfShow -> bookmark(teaser.show.ref)
        is Teaser.OfEpisode -> "episode:${teaser.episode.ref.source.name}:${teaser.episode.ref.key}"
    }

    fun bookmark(ref: ShowRef): String = "show:${ref.source.name}:${ref.key}"
    fun bookmark(ref: EpisodeRef): String = "episode:${ref.source.name}:${ref.key}"
}

// The domain models are not @Serializable (model/ is owned by the contract), so snapshots use their own DTOs.

@Serializable
internal data class ShowSnapshot(
    val source: String,
    val key: String,
    val title: String,
    val broadcaster: String,
    val channelLabel: String,
    val genre: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val episodeCount: Int? = null,
)

@Serializable
internal data class EpisodeSnapshot(
    val source: String,
    val key: String,
    val showSource: String? = null,
    val showKey: String? = null,
    val showTitle: String,
    val title: String,
    val broadcaster: String,
    val channelLabel: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val durationSeconds: Int,
    val publishedAtMs: Long,
    val availableUntilMs: Long? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val hasSubtitles: Boolean = false,
    val variants: List<String> = listOf(Variant.STANDARD.name),
    val maxHeight: Int? = null,
    val websiteUrl: String? = null,
)

@Serializable
internal data class TeaserSnapshot(val show: ShowSnapshot? = null, val episode: EpisodeSnapshot? = null)

internal fun Show.toSnapshot() = ShowSnapshot(
    ref.source.name, ref.key, title, broadcaster.name, channelLabel, genre, description, imageUrl, episodeCount,
)

internal fun ShowSnapshot.toModel() = Show(
    ref = ShowRef(SourceId.valueOf(source), key),
    title = title,
    broadcaster = Broadcaster.valueOf(broadcaster),
    channelLabel = channelLabel,
    genre = genre,
    description = description,
    imageUrl = imageUrl,
    episodeCount = episodeCount,
)

internal fun Episode.toSnapshot() = EpisodeSnapshot(
    source = ref.source.name,
    key = ref.key,
    showSource = showRef?.source?.name,
    showKey = showRef?.key,
    showTitle = showTitle,
    title = title,
    broadcaster = broadcaster.name,
    channelLabel = channelLabel,
    description = description,
    imageUrl = imageUrl,
    durationSeconds = durationSeconds,
    publishedAtMs = publishedAt.toEpochMilliseconds(),
    availableUntilMs = availableUntil?.toEpochMilliseconds(),
    season = season,
    episodeNumber = episodeNumber,
    hasSubtitles = hasSubtitles,
    variants = variants.map { it.name },
    maxHeight = maxHeight,
    websiteUrl = websiteUrl,
)

internal fun EpisodeSnapshot.toModel() = Episode(
    ref = EpisodeRef(SourceId.valueOf(source), key),
    showRef = if (showSource != null && showKey != null) ShowRef(SourceId.valueOf(showSource), showKey) else null,
    showTitle = showTitle,
    title = title,
    broadcaster = Broadcaster.valueOf(broadcaster),
    channelLabel = channelLabel,
    description = description,
    imageUrl = imageUrl,
    durationSeconds = durationSeconds,
    publishedAt = Instant.fromEpochMilliseconds(publishedAtMs),
    availableUntil = availableUntilMs?.let { Instant.fromEpochMilliseconds(it) },
    season = season,
    episodeNumber = episodeNumber,
    hasSubtitles = hasSubtitles,
    variants = variants.mapNotNull { runCatching { Variant.valueOf(it) }.getOrNull() }.toSet().ifEmpty { setOf(Variant.STANDARD) },
    maxHeight = maxHeight,
    websiteUrl = websiteUrl,
)

internal fun Teaser.toJson(): String = LibraryJson.encodeToString(
    TeaserSnapshot.serializer(),
    when (this) {
        is Teaser.OfShow -> TeaserSnapshot(show = show.toSnapshot())
        is Teaser.OfEpisode -> TeaserSnapshot(episode = episode.toSnapshot())
    },
)

/** Decodes a stored teaser; returns null for unreadable rows so that one bad row never breaks a list. */
internal fun decodeTeaser(json: String): Teaser? = runCatching {
    val s = LibraryJson.decodeFromString(TeaserSnapshot.serializer(), json)
    s.show?.let { Teaser.OfShow(it.toModel()) } ?: s.episode?.let { Teaser.OfEpisode(it.toModel()) }
}.getOrNull()

internal fun Episode.toJson(): String = LibraryJson.encodeToString(EpisodeSnapshot.serializer(), toSnapshot())

internal fun decodeEpisode(json: String): Episode? =
    runCatching { LibraryJson.decodeFromString(EpisodeSnapshot.serializer(), json).toModel() }.getOrNull()
