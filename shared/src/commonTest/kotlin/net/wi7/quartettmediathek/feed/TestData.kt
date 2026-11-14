package net.wi7.quartettmediathek.feed

import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Variant

fun t(iso: String): Instant = Instant.parse(iso)

fun episode(
    key: String,
    show: String,
    title: String,
    broadcaster: Broadcaster,
    at: String,
    minutes: Int = 45,
    variants: Set<Variant> = setOf(Variant.STANDARD),
) = Episode(
    ref = EpisodeRef(SourceId.MVW, key),
    showRef = ShowRef(SourceId.MVW, show),
    showTitle = show,
    title = title,
    broadcaster = broadcaster,
    channelLabel = broadcaster.displayName,
    description = null,
    imageUrl = null,
    durationSeconds = minutes * 60,
    publishedAt = t(at),
    variants = variants,
)

fun candidate(
    key: String,
    show: String,
    title: String,
    broadcaster: Broadcaster,
    at: String,
    minutes: Int = 45,
    url: String? = "https://cdn/$key.mp4",
    size: Long? = null,
) = FeedCandidate(episode(key, show, title, broadcaster, at, minutes), url, size)

fun show(title: String, broadcaster: Broadcaster, key: String = title) = Show(
    ref = ShowRef(SourceId.MVW, key),
    title = title,
    broadcaster = broadcaster,
    channelLabel = broadcaster.displayName,
)
