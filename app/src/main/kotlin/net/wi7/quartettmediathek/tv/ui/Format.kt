package net.wi7.quartettmediathek.tv.ui

import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.subscription.Subscription

/** Pure helpers for presentation logic (no Android dependencies, unit tested). */

/** "36:50" or "1:02:03". */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

fun progressFraction(positionSeconds: Long, durationSeconds: Int): Float =
    if (durationSeconds <= 0) 0f else (positionSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f)

fun minutesLeft(durationSeconds: Int, positionSeconds: Long): Int =
    ceil(((durationSeconds - positionSeconds).coerceAtLeast(0)) / 60.0).toInt()

/** Relative age of an episode as shown on tiles ("yesterday", "3 days ago", ...). */
sealed interface Age {
    data object JustNow : Age
    data class Hours(val n: Int) : Age
    data object Yesterday : Age
    data class Days(val n: Int) : Age
    data object OneWeek : Age
    data class Weeks(val n: Int) : Age
    data class Older(val published: Instant) : Age
}

fun ageOf(published: Instant, now: Instant): Age {
    val hours = (now - published).inWholeMinutes / 60.0
    if (hours < 1) return Age.JustNow
    if (hours < 20) return Age.Hours(hours.roundToInt())
    val days = (hours / 24).roundToInt()
    return when {
        days <= 1 -> Age.Yesterday
        days < 7 -> Age.Days(days)
        days < 14 -> Age.OneWeek
        days < 31 -> Age.Weeks(days / 7)
        else -> Age.Older(published)
    }
}

/** What the header refresh button shows next to the icon. */
sealed interface UpdatedLabel {
    data object Never : UpdatedLabel
    data class Today(val at: Instant) : UpdatedLabel
    data class Yesterday(val at: Instant) : UpdatedLabel
    data class On(val at: Instant) : UpdatedLabel
}

fun updatedLabel(updated: Instant?, now: Instant, zone: ZoneId): UpdatedLabel {
    if (updated == null) return UpdatedLabel.Never
    val d = java.time.Instant.ofEpochMilli(updated.toEpochMilliseconds()).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(zone).toLocalDate()
    return when (d) {
        today -> UpdatedLabel.Today(updated)
        today.minusDays(1) -> UpdatedLabel.Yesterday(updated)
        else -> UpdatedLabel.On(updated)
    }
}

/** Stable key of a teaser, also the key format of bookmarks. */
fun Teaser.key(): String = when (this) {
    is Teaser.OfShow -> "show:${show.ref.key}"
    is Teaser.OfEpisode -> "ep:${episode.ref.key}"
}

fun Teaser.broadcaster(): Broadcaster = when (this) {
    is Teaser.OfShow -> show.broadcaster
    is Teaser.OfEpisode -> episode.broadcaster
}

/** Options the "Adjust subscription" dialog toggles. They map onto [Subscription] fields. */
data class SubscriptionOptions(
    val fullEpisodesOnly: Boolean,
    val relatedTopics: Boolean,
    val audioDescription: Boolean,
    val signLanguage: Boolean,
)

private val RELATED_SUFFIXES = listOf("Spezial", "Talk", "Doku")

fun Subscription.options() = SubscriptionOptions(
    fullEpisodesOnly = minDurationMinutes > 0,
    relatedTopics = topics.size > 1,
    audioDescription = preferredVariant == Variant.AUDIO_DESCRIPTION,
    signLanguage = preferredVariant == Variant.SIGN_LANGUAGE,
)

/** Applies [o]; audio description and sign language exclude each other (the model has one preferred variant). */
fun Subscription.withOptions(o: SubscriptionOptions): Subscription {
    val base = topics.firstOrNull() ?: title
    return copy(
        minDurationMinutes = if (o.fullEpisodesOnly) 20 else 0,
        topics = if (o.relatedTopics) listOf(base) + RELATED_SUFFIXES.map { "$base $it" } else listOf(base),
        preferredVariant = when {
            o.audioDescription -> Variant.AUDIO_DESCRIPTION
            o.signLanguage -> Variant.SIGN_LANGUAGE
            else -> Variant.STANDARD
        },
    )
}

/** MediathekViewWeb selector expression of a subscription, e.g. `!3sat #nano >20` (see docs/SPEC.md 2.1). */
fun mvwExpression(sub: Subscription): String {
    val parts = mutableListOf<String>()
    sub.broadcasters.sortedBy { it.ordinal }.forEach { parts += "!" + it.displayName.lowercase() }
    sub.topics.forEach { topic ->
        val words = topic.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isNotEmpty()) parts += "#" + words.joinToString(",")
    }
    if (sub.minDurationMinutes > 0) parts += ">${sub.minDurationMinutes}"
    return parts.joinToString(" ")
}
