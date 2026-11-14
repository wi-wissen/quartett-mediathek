package net.wi7.quartettmediathek.subscription

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import net.wi7.quartettmediathek.feed.TitleParser
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Variant

/** Creates subscriptions with the defaults from docs/SPEC.md. */
object SubscriptionFactory {
    /** Deterministic id so that `subscriptionFor(ShowRef)` can find a subscription without extra storage. */
    fun idFor(ref: ShowRef): String = "${ref.source.name}:${ref.key}"

    /**
     * Defaults: minimum 20 minutes, standard variant, only the show's broadcaster, topics = the cleaned show
     * title, `seenUntil` = [now] minus 7 days (so the last week shows up as new).
     */
    fun forShow(show: Show, now: Instant): Subscription {
        val name = TitleParser.cleanShowName(show.title)
        return Subscription(
            id = idFor(show.ref),
            title = name,
            topics = listOf(name),
            broadcasters = setOf(show.broadcaster),
            minDurationMinutes = 20,
            preferredVariant = Variant.STANDARD,
            seenUntilEpochMs = (now - 7.days).toEpochMilliseconds(),
        )
    }
}
