package net.wi7.quartettmediathek.data

import kotlinx.coroutines.CancellationException
import net.wi7.quartettmediathek.feed.TitleParser
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository
import net.wi7.quartettmediathek.source.SourceException
import net.wi7.quartettmediathek.source.ard.ArdClient
import net.wi7.quartettmediathek.source.arte.ArteClient
import net.wi7.quartettmediathek.source.mvw.MvwClause
import net.wi7.quartettmediathek.source.mvw.MvwEntry
import net.wi7.quartettmediathek.source.mvw.MvwField
import net.wi7.quartettmediathek.source.mvw.MvwQuery
import net.wi7.quartettmediathek.source.mvw.ardItemId
import net.wi7.quartettmediathek.source.mvw.toStreamInfo
import net.wi7.quartettmediathek.source.zdf.ZdfClient

/**
 * Resolves streams right before playback (SPEC 2: ARD item API, ZDF ptmd, arte player, MP4 from MediathekViewWeb).
 * MediathekViewWeb hits that point to the ARD Mediathek are played from the ARD API (up to 1080p, WebVTT) when
 * they are the standard version; everything else uses the MP4 from MediathekViewWeb.
 */
class DefaultPlaybackRepository(
    private val ard: ArdClient,
    private val zdf: ZdfClient,
    private val arte: ArteClient,
    private val mvw: MvwFeedProvider,
    private val index: ContentIndex,
    private val catalog: CatalogRepository,
) : PlaybackRepository {

    override suspend fun stream(ref: EpisodeRef, variant: Variant): StreamInfo = when (ref.source) {
        SourceId.ARD -> ard.resolve(ref)
        SourceId.ZDF -> zdf.resolve(ref)
        SourceId.ARTE -> arte.resolve(ref)
        SourceId.MVW -> mvwStream(ref)
    }

    /**
     * MediathekViewWeb offers at most 720p MP4. For the standard version of an ARD or ZDF episode the broadcaster's own
     * API is asked first (up to 1080p, WebVTT subtitles); the MP4 from MediathekViewWeb is the fallback.
     */
    private suspend fun mvwStream(ref: EpisodeRef): StreamInfo {
        val entry = index.mvwEntry(ref.key) ?: refetch(ref)
        if (TitleParser.parse(entry.title).variant == Variant.STANDARD) {
            val direct = entry.ardItemId?.let { EpisodeRef(SourceId.ARD, it) }
                ?: (entry.urlWebsite?.let(::classifyEpisodePage) as? ImageStrategy.Zdf)?.let { EpisodeRef(SourceId.ZDF, it.canonical) }
            if (direct != null) {
                try {
                    return if (direct.source == SourceId.ARD) ard.resolve(direct) else zdf.resolve(direct)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // fall back to the MP4 from MediathekViewWeb
                }
            }
        }
        return entry.toStreamInfo() ?: throw SourceException("No stream for ${ref.key}")
    }

    /** After an app restart the raw hit is gone; look it up again by show topic and title. */
    private suspend fun refetch(ref: EpisodeRef): MvwEntry {
        val episode = catalog.episode(ref)
        val query = MvwQuery(
            clauses = listOf(MvwClause(MvwField.TOPIC, episode.showTitle), MvwClause(MvwField.TITLE, episode.title)),
            size = 20,
        )
        val entries = mvw.query(query, "mvw:episode:${ref.key}")
        return entries.firstOrNull { it.id == ref.key } ?: entries.firstOrNull()
            ?: throw SourceException("Episode ${ref.key} is no longer available")
    }
}
