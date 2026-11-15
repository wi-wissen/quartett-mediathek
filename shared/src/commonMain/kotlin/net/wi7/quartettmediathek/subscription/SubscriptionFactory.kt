package net.wi7.quartettmediathek.subscription

import net.wi7.quartettmediathek.feed.normalizeText
import net.wi7.quartettmediathek.model.SourceId
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

    /** The show ref an id was made from, or null for foreign ids. */
    fun refOf(id: String): ShowRef? {
        val source = SourceId.entries.firstOrNull { id.startsWith(it.name + ":") } ?: return null
        return ShowRef(source, id.removePrefix(source.name + ":"))
    }

    /** Topics compared like the feed does: case, umlauts and whitespace do not matter. */
    fun topicKey(sub: Subscription): Set<String> = sub.topics.map { normalizeText(it) }.toSet()

    /** True when [sub] belongs to the show [ref] (made from it, or an MVW show whose key is one of its topics). */
    fun belongsTo(sub: Subscription, ref: ShowRef): Boolean =
        sub.id == idFor(ref) || ref in sub.showRefs ||
            (ref.source == SourceId.MVW && normalizeText(ref.key) in topicKey(sub))

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
            showRefs = listOf(show.ref),
        )
    }
}
