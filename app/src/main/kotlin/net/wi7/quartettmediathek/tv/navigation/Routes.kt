package net.wi7.quartettmediathek.tv.navigation

import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.repository.StartScreen

/** Navigation routes. Source keys are hex-encoded in routes so any character is safe. */
object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val SEARCH = "search"
    const val SUBSCRIPTIONS = "subscriptions"
    const val BOOKMARKS = "bookmarks"
    const val SETTINGS = "settings"

    const val SHOW_PATTERN = "show/{source}/{key}"
    const val EPISODE_PATTERN = "episode/{source}/{key}"
    const val PLAYER_PATTERN = "player/{source}/{key}/{start}"

    val topLevel = listOf(HOME, DISCOVER, SEARCH, SUBSCRIPTIONS, BOOKMARKS, SETTINGS)

    fun show(ref: ShowRef) = "show/${ref.source.name}/${encode(ref.key)}"
    fun episode(ref: EpisodeRef) = "episode/${ref.source.name}/${encode(ref.key)}"

    /** [startSeconds] null = resume from saved progress, 0 = from the beginning. */
    fun player(ref: EpisodeRef, startSeconds: Long?) =
        "player/${ref.source.name}/${encode(ref.key)}/${startSeconds ?: -1L}"

    fun showRef(source: String?, key: String?) = ShowRef(SourceId.valueOf(source!!), decode(key!!))
    fun episodeRef(source: String?, key: String?) = EpisodeRef(SourceId.valueOf(source!!), decode(key!!))

    fun forStartScreen(s: StartScreen) = when (s) {
        StartScreen.HOME -> HOME
        StartScreen.SUBSCRIPTIONS -> SUBSCRIPTIONS
        StartScreen.BOOKMARKS -> BOOKMARKS
    }

    fun encode(s: String): String = s.encodeToByteArray().joinToString("") { "%02x".format(it) }

    fun decode(s: String): String =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }.decodeToString()
}
