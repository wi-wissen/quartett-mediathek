package net.wi7.quartettmediathek.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.source.mvw.MvwEntry

/**
 * In-memory index of everything the app has seen in this process: shows, episodes and raw MediathekViewWeb hits.
 * Lets the episode page and the player open an item without another round trip. After an app restart the
 * library snapshots (progress, watchlist) fill the gaps, see [DefaultCatalogRepository.episode].
 */
class ContentIndex {
    private val lock = Mutex()
    private val shows = HashMap<ShowRef, Show>()
    private val episodes = HashMap<EpisodeRef, Episode>()
    private val mvwEntries = HashMap<String, MvwEntry>()

    suspend fun remember(teasers: Iterable<Teaser>) = lock.withLock {
        teasers.forEach {
            when (it) {
                is Teaser.OfShow -> shows[it.show.ref] = it.show
                is Teaser.OfEpisode -> episodes[it.episode.ref] = it.episode
            }
        }
    }

    suspend fun rememberShow(show: Show) = lock.withLock { shows[show.ref] = show }
    suspend fun rememberEpisodes(list: Iterable<Episode>) = lock.withLock { list.forEach { episodes[it.ref] = it } }
    suspend fun rememberMvw(entries: Iterable<MvwEntry>) = lock.withLock { entries.forEach { mvwEntries[it.id] = it } }

    suspend fun show(ref: ShowRef): Show? = lock.withLock { shows[ref] }
    suspend fun episode(ref: EpisodeRef): Episode? = lock.withLock { episodes[ref] }
    suspend fun mvwEntry(id: String): MvwEntry? = lock.withLock { mvwEntries[id] }
}
