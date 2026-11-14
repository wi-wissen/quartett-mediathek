package net.wi7.quartettmediathek.source

import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.StreamInfo

/** Editorial rows from a broadcaster's start page (ARD gateway, ZDF GraphQL, arte EMAC). */
interface RecommendationSource {
    suspend fun startPageRows(): List<SourceRow>
}

/** Show pages: metadata and the episode list, newest first. */
interface ShowSource {
    suspend fun show(ref: ShowRef): Show
    suspend fun episodes(ref: ShowRef): List<Episode>
}

/** Turns an episode into something playable. Called right before playback. */
interface StreamResolver {
    suspend fun resolve(ref: EpisodeRef): StreamInfo
}

/** Thrown by source clients when the origin answers but the data cannot be used. */
class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
