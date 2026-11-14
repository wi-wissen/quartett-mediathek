package net.wi7.quartettmediathek.model

import kotlinx.serialization.Serializable

/**
 * The four public broadcasters the app covers.
 *
 * [mvwChannels] lists the channel names MediathekViewWeb uses for content that belongs to this broadcaster
 * (lower case, as returned in `channel`). ARD includes its regional programmes, ZDF includes ZDFneo/ZDFinfo.
 * Anything else in MediathekViewWeb (e.g. SRF, ORF, KiKA, DW) is out of scope and must be filtered out.
 */
@Serializable
enum class Broadcaster(val displayName: String, val mvwChannels: Set<String>) {
    ARD(
        "ARD",
        setOf(
            "ard", "das erste", "br", "hr", "mdr", "ndr", "radio bremen", "rbb", "sr", "swr", "wdr",
            "one", "ard-alpha", "tagesschau24", "phoenix", "funk.net",
        ),
    ),
    ZDF("ZDF", setOf("zdf", "zdfneo", "zdfinfo", "zdf-tivi")),
    THREESAT("3sat", setOf("3sat")),
    ARTE("arte", setOf("arte.de", "arte.fr", "arte"));

    companion object {
        /** Maps a MediathekViewWeb channel name to a broadcaster, or null if it is out of scope. */
        fun fromMvwChannel(channel: String): Broadcaster? {
            val c = channel.trim().lowercase()
            return entries.firstOrNull { c in it.mvwChannels }
        }
    }
}
