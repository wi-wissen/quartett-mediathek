package net.wi7.quartettmediathek.feed

import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.repository.FeedEntry
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.subscription.SubscriptionFilter

/** Builds the subscription feed from the raw candidates of every subscription. */
object FeedBuilder {
    /**
     * @param sources each subscription with the raw candidates loaded for it
     * @param isWatched tells whether an episode was watched (watched episodes are never "new")
     * @return one entry per episode in the subscription's preferred variant, newest first
     */
    fun build(
        sources: List<Pair<Subscription, List<FeedCandidate>>>,
        isWatched: (EpisodeRef) -> Boolean = { false },
    ): List<FeedEntry> {
        val seen = HashSet<EpisodeRef>()
        val entries = ArrayList<FeedEntry>()
        for ((subscription, candidates) in sources) {
            val filtered = SubscriptionFilter.filter(subscription, candidates)
            val merged = EpisodeMerger.merge(filtered, subscription.preferredVariant, subscription.broadcasters.toList())
            for (m in merged) {
                val ep = m.episode
                if (!seen.add(ep.ref)) continue
                val isNew = ep.publishedAt.toEpochMilliseconds() > subscription.seenUntilEpochMs && !isWatched(ep.ref)
                entries.add(FeedEntry(ep, subscription.id, isNew))
            }
        }
        return entries.sortedWith(compareBy(EpisodeOrder.newestFirst) { it.episode })
    }
}
