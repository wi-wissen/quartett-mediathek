package net.wi7.quartettmediathek.subscription

import kotlinx.serialization.Serializable
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Variant

/**
 * A subscription is a saved MediathekViewWeb search that the app filters again locally (see docs/SPEC.md,
 * "Subscriptions, duplicates and variants").
 *
 * @property topics exact topics (compared case-insensitively after normalisation), e.g. ["NANO", "NANO Doku"].
 * @property broadcasters allowed broadcasters; MediathekViewWeb channels outside them are dropped.
 * @property minDurationMinutes clips shorter than this are hidden. Default 20.
 * @property preferredVariant which version to show when several exist for one episode.
 * @property seenUntilEpochMs everything published up to this moment counts as seen (drives the "NEU" badge).
 */
@Serializable
data class Subscription(
    val id: String,
    val title: String,
    val topics: List<String>,
    val broadcasters: Set<Broadcaster>,
    val minDurationMinutes: Int = 20,
    val preferredVariant: Variant = Variant.STANDARD,
    val seenUntilEpochMs: Long,
    /** Every show page this subscription was made from (e.g. the ZDF show and the same show found via search). */
    val showRefs: List<ShowRef> = emptyList(),
)
