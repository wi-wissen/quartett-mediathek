package net.wi7.quartettmediathek.subscription

import net.wi7.quartettmediathek.feed.FeedCandidate
import net.wi7.quartettmediathek.feed.normalizeText
import net.wi7.quartettmediathek.model.Episode

/**
 * Local re-filtering of MediathekViewWeb hits (the API matches by words, so "nano" also returns "NANO Doku",
 * SRF and 7 minute clips).
 */
object SubscriptionFilter {
    /** Suffixes that make a topic a "related topic" of a base topic ("NANO" -> "NANO Talk"). */
    val RELATED_SUFFIXES = listOf("Spezial", "Talk", "Doku")

    /** The related topics of [topic]: "X Spezial", "X Talk", "X Doku". */
    fun relatedTopics(topic: String): List<String> = RELATED_SUFFIXES.map { "$topic $it" }

    /**
     * An episode matches when its topic (`showTitle`) equals one of the subscription's topics after
     * normalisation, its broadcaster is allowed and it is at least `minDurationMinutes` long. With
     * [includeRelated] the related topics of every topic match as well.
     */
    fun matches(subscription: Subscription, episode: Episode, includeRelated: Boolean = false): Boolean {
        if (episode.broadcaster !in subscription.broadcasters) return false
        if (episode.durationSeconds < subscription.minDurationMinutes * 60) return false
        val topic = normalizeText(episode.showTitle)
        return subscription.topics.any { t ->
            val base = normalizeText(t)
            topic == base || (includeRelated && relatedTopics(base).any { topic == normalizeText(it) })
        }
    }

    fun filter(
        subscription: Subscription,
        candidates: List<FeedCandidate>,
        includeRelated: Boolean = false,
    ): List<FeedCandidate> = candidates.filter { matches(subscription, it.episode, includeRelated) }
}
