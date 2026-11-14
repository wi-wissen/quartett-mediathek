package net.wi7.quartettmediathek.subscription

import net.wi7.quartettmediathek.model.Broadcaster

/**
 * Renders a subscription in the MediathekViewWeb selector syntax (`!` channel, `#` topic, `>` minutes).
 * Repeated selectors are OR, commas inside one value are AND. Example: `!3sat #nano >20`.
 */
object SubscriptionExpression {
    /** Default channel names per broadcaster (MVW channel names, lower case). */
    val DEFAULT_CHANNELS: Map<Broadcaster, List<String>> = mapOf(
        Broadcaster.ARD to listOf("ard"),
        Broadcaster.ZDF to listOf("zdf"),
        Broadcaster.THREESAT to listOf("3sat"),
        Broadcaster.ARTE to listOf("arte.de"),
    )

    /** [channels] lets the caller list sub-channels, e.g. ARD -> ["ard", "ndr"]. */
    fun format(subscription: Subscription, channels: Map<Broadcaster, List<String>> = DEFAULT_CHANNELS): String {
        val parts = mutableListOf<String>()
        subscription.broadcasters.forEach { b ->
            (channels[b] ?: DEFAULT_CHANNELS.getValue(b)).forEach { parts += "!" + it.trim().lowercase() }
        }
        subscription.topics.forEach { topic ->
            val words = topic.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isNotEmpty()) parts += "#" + words.joinToString(",")
        }
        if (subscription.minDurationMinutes > 0) parts += ">" + subscription.minDurationMinutes
        return parts.joinToString(" ")
    }
}
