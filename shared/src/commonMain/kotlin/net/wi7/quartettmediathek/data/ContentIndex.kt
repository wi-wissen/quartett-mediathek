package net.wi7.quartettmediathek.data

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.network.QuartettJson
import net.wi7.quartettmediathek.source.mvw.MvwEntry

/**
 * Everything the app has seen: shows, episodes and raw MediathekViewWeb hits.
 *
 * Two levels: an in-memory map for the running process and the `content_item` table, so pages, the player and
 * stream resolution work instantly after an app restart (Android TV often kills apps in the background).
 * Entries are pruned by last use: not opened or re-loaded for [maxAge], or beyond the newest [maxItems]
 * (see [prune]). Without a [db] it is memory-only (tests).
 */
class ContentIndex(
    private val db: QuartettDatabase? = null,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxAge: Duration = 90.days,
    private val maxItems: Long = 20_000,
) {
    private val lock = Mutex()
    private val shows = HashMap<ShowRef, Show>()
    private val episodes = HashMap<EpisodeRef, Episode>()
    private val mvwEntries = HashMap<String, MvwEntry>()

    suspend fun remember(teasers: Iterable<Teaser>) {
        val list = teasers.toList()
        lock.withLock {
            list.forEach {
                when (it) {
                    is Teaser.OfShow -> shows[it.show.ref] = it.show
                    is Teaser.OfEpisode -> episodes[it.episode.ref] = it.episode
                }
            }
        }
        persist(
            list.filterIsInstance<Teaser.OfShow>().map { Row(SHOW, showKey(it.show.ref), encode(Show.serializer(), it.show)) } +
                list.filterIsInstance<Teaser.OfEpisode>().map { Row(EPISODE, episodeKey(it.episode.ref), encode(Episode.serializer(), it.episode)) },
        )
    }

    suspend fun rememberShow(show: Show) {
        lock.withLock { shows[show.ref] = show }
        persist(listOf(Row(SHOW, showKey(show.ref), encode(Show.serializer(), show))))
    }

    suspend fun rememberEpisodes(list: Iterable<Episode>) {
        val all = list.toList()
        lock.withLock { all.forEach { episodes[it.ref] = it } }
        persist(all.map { Row(EPISODE, episodeKey(it.ref), encode(Episode.serializer(), it)) })
    }

    suspend fun rememberMvw(entries: Iterable<MvwEntry>) {
        val all = entries.toList()
        lock.withLock { all.forEach { mvwEntries[it.id] = it } }
        persist(all.map { Row(MVW, it.id, encode(MvwEntry.serializer(), it)) })
    }

    suspend fun show(ref: ShowRef): Show? =
        lock.withLock { shows[ref] } ?: load(SHOW, showKey(ref), Show.serializer())?.also { lock.withLock { shows[ref] = it } }

    suspend fun episode(ref: EpisodeRef): Episode? =
        lock.withLock { episodes[ref] } ?: load(EPISODE, episodeKey(ref), Episode.serializer())?.also { lock.withLock { episodes[ref] = it } }

    suspend fun mvwEntry(id: String): MvwEntry? =
        lock.withLock { mvwEntries[id] } ?: load(MVW, id, MvwEntry.serializer())?.also { lock.withLock { mvwEntries[id] = it } }

    /** Drops entries not used for [maxAge] and everything beyond the newest [maxItems]. Call once per app start. */
    suspend fun prune() {
        val q = db?.contentCacheQueries ?: return
        withContext(dispatcher) {
            runCatching {
                q.pruneContentOlderThan((clock.now() - maxAge).toEpochMilliseconds())
                q.pruneContentBeyond(maxItems)
            }
        }
    }

    private class Row(val kind: String, val key: String, val json: String)

    private suspend fun persist(rows: List<Row>) {
        val database = db ?: return
        if (rows.isEmpty()) return
        val now = clock.now().toEpochMilliseconds()
        // The cache is an optimisation: a database problem must never break a page.
        withContext(dispatcher) {
            runCatching {
                database.contentCacheQueries.transaction {
                    rows.forEach { database.contentCacheQueries.putContentItem(it.kind, it.key, it.json, now) }
                }
            }
        }
    }

    private suspend fun <T> load(kind: String, key: String, serializer: KSerializer<T>): T? {
        val q = db?.contentCacheQueries ?: return null
        return withContext(dispatcher) {
            runCatching {
                val json = q.contentItem(kind, key).executeAsOneOrNull() ?: return@runCatching null
                QuartettJson.decodeFromString(serializer, json).also { q.touchContentItem(clock.now().toEpochMilliseconds(), kind, key) }
            }.getOrNull()
        }
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T) = QuartettJson.encodeToString(serializer, value)

    private companion object {
        const val SHOW = "show"
        const val EPISODE = "episode"
        const val MVW = "mvw"
        fun showKey(ref: ShowRef) = "${ref.source.name}:${ref.key}"
        fun episodeKey(ref: EpisodeRef) = "${ref.source.name}:${ref.key}"
    }
}
