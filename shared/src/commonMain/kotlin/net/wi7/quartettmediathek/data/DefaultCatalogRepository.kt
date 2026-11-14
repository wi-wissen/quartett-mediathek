package net.wi7.quartettmediathek.data

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import net.wi7.quartettmediathek.cache.CachePolicy
import net.wi7.quartettmediathek.cache.CacheStore
import net.wi7.quartettmediathek.catalog.DiscoverAssembler
import net.wi7.quartettmediathek.feed.EpisodeMerger
import net.wi7.quartettmediathek.feed.FeedCandidate
import net.wi7.quartettmediathek.feed.normalizeText
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.DiscoverRow
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.SourceRow
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.network.QuartettJson
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.source.SourceException
import net.wi7.quartettmediathek.source.ard.ArdClient
import net.wi7.quartettmediathek.source.arte.ArteClient
import net.wi7.quartettmediathek.source.mvw.MvwClause
import net.wi7.quartettmediathek.source.mvw.MvwEntry
import net.wi7.quartettmediathek.source.mvw.MvwField
import net.wi7.quartettmediathek.source.mvw.MvwQuery
import net.wi7.quartettmediathek.source.mvw.MvwSelectorParser
import net.wi7.quartettmediathek.source.mvw.toEpisode
import net.wi7.quartettmediathek.source.zdf.ZdfClient

/**
 * Catalog on top of the broadcaster clients and MediathekViewWeb (SPEC 2, 4).
 *
 * Start pages are cached per broadcaster in the database for a day. On start the cached rows are shown at once
 * (even when stale), then refreshed in the background; old rows stay visible until new ones arrived.
 * Shows and episodes are resolved by their source: ARD and ZDF through their APIs, arte collections and
 * 3sat through MediathekViewWeb topics.
 */
class DefaultCatalogRepository(
    private val ard: ArdClient,
    private val zdf: ZdfClient,
    private val arte: ArteClient,
    private val mvw: MvwFeedProvider,
    private val images: EpisodeImageResolver?,
    private val cache: CacheStore,
    private val index: ContentIndex,
    private val scope: CoroutineScope,
    /** Fallback for episodes not seen in this process, e.g. from progress or watchlist snapshots. */
    private val snapshotLookup: suspend (EpisodeRef) -> Episode? = { null },
    private val clock: Clock = Clock.System,
) : CatalogRepository {

    private val rows = MutableStateFlow<Map<Broadcaster, List<SourceRow>>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private val updated = MutableStateFlow<Instant?>(null)
    private val startLock = Mutex()
    private var started = false
    private val rowsSerializer = ListSerializer(SourceRow.serializer())

    private val startPages: Map<Broadcaster, suspend () -> List<SourceRow>> = mapOf(
        Broadcaster.ARD to { ard.startPageRows() },
        Broadcaster.ZDF to { zdf.startPageRows() },
        Broadcaster.ARTE to { arte.startPageRows() },
        Broadcaster.THREESAT to { threeSatRows() },
    )

    override fun discoverRows(): Flow<List<DiscoverRow>> =
        rows.map { byBroadcaster -> DiscoverAssembler.assemble(byBroadcaster.values.flatten()) }
            .onStart { ensureStarted() }

    override fun lastUpdated(): Flow<Instant?> = updated.asStateFlow()
    override fun isRefreshing(): StateFlow<Boolean> = refreshing.asStateFlow()

    private suspend fun ensureStarted() {
        val first = startLock.withLock { if (started) false else true.also { started = true } }
        if (!first) return
        loadFromCache()
        scope.launch { refresh(force = false) }
    }

    private suspend fun loadFromCache() {
        val loaded = startPages.keys.mapNotNull { b ->
            val cached = cache.get(cacheKey(b)) ?: return@mapNotNull null
            val decoded = runCatching { QuartettJson.decodeFromString(rowsSerializer, cached.body) }.getOrNull()
                ?: return@mapNotNull null
            index.remember(decoded.flatMap { it.items })
            Triple(b, decoded, cached.fetchedAt)
        }
        rows.value = loaded.associate { it.first to it.second }
        updated.value = loaded.minOfOrNull { it.third }
    }

    override suspend fun refresh(force: Boolean) {
        if (!refreshing.compareAndSet(expect = false, update = true)) return
        try {
            if (force) mvw.invalidate()
            val now = clock.now()
            val results = coroutineScope {
                startPages.map { (b, load) ->
                    async {
                        val cached = cache.get(cacheKey(b))
                        if (!force && cached != null && CachePolicy.START_PAGE.isFresh(cached.fetchedAt, now)) {
                            return@async true
                        }
                        try {
                            val fresh = load()
                            if (fresh.isNotEmpty()) {
                                cache.put(cacheKey(b), QuartettJson.encodeToString(rowsSerializer, fresh))
                                index.remember(fresh.flatMap { it.items })
                                rows.update { it + (b to fresh) }
                            }
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false // keep old rows of this broadcaster
                        }
                    }
                }.awaitAll()
            }
            if (results.any { it }) {
                updated.value = startPages.keys.mapNotNull { cache.fetchedAt(cacheKey(it)) }.minOrNull() ?: now
            }
        } finally {
            refreshing.value = false
        }
    }

    private fun cacheKey(b: Broadcaster) = "rows:${b.name}"

    // Shows and episodes

    override suspend fun show(ref: ShowRef): Show {
        index.show(ref)?.let { return it }
        return when (ref.source) {
            SourceId.ARD -> ard.grouping(ref).also { index.rememberEpisodes(it.episodes) }.show
            SourceId.ZDF -> zdf.loadShow(ref).also { index.rememberEpisodes(it.second) }.first
            SourceId.ARTE -> arte.program(ref.key).let {
                Show(ref, it.title, Broadcaster.ARTE, "arte", description = it.description, imageUrl = it.imageUrl)
            }
            SourceId.MVW -> mvwShow(ref, mvwEpisodes(ref.key, null))
        }.also { index.rememberShow(it) }
    }

    override suspend fun episodes(ref: ShowRef): List<Episode> {
        val list = when (ref.source) {
            SourceId.ARD -> ard.grouping(ref).episodes
            SourceId.ZDF -> zdf.loadShow(ref).second
            SourceId.ARTE -> mvwEpisodes(show(ref).title, Broadcaster.ARTE)
            SourceId.MVW -> mvwEpisodes(ref.key, null)
        }
        index.rememberEpisodes(list)
        return list
    }

    override suspend fun episode(ref: EpisodeRef): Episode {
        index.episode(ref)?.let { return withImage(it) }
        snapshotLookup(ref)?.let { return withImage(it) }
        // Not seen in this process (e.g. after an app restart): load it from its source.
        val loaded = when (ref.source) {
            SourceId.ARD -> ard.episode(ref)
            SourceId.ZDF -> zdf.episode(ref)
            SourceId.ARTE -> arte.episode(ref)
            SourceId.MVW -> throw SourceException("Episode ${ref.key} is no longer known, please open it from its show again")
        }
        index.rememberEpisodes(listOf(loaded))
        return loaded
    }

    /** Episodes from older snapshots or beyond the list limit may lack a thumbnail: look it up (cached). */
    private suspend fun withImage(episode: Episode): Episode {
        if (episode.imageUrl != null || episode.ref.source != SourceId.MVW || images == null) return episode
        val url = images.imageFor(episode.ref.key, episode.websiteUrl) ?: return episode
        return episode.copy(imageUrl = url).also { index.rememberEpisodes(listOf(it)) }
    }

    private suspend fun withImages(episodes: List<Episode>): List<Episode> =
        images?.fill(episodes, IMAGE_LIMIT) ?: episodes

    /** Episodes of a MediathekViewWeb topic: exact topic match, duplicates and variants merged, newest first. */
    private suspend fun mvwEpisodes(topic: String, broadcaster: Broadcaster?): List<Episode> {
        val query = MvwQuery(clauses = listOf(MvwClause(MvwField.TOPIC, topic)), size = 100)
        val wanted = normalizeText(topic)
        val entries = mvw.query(query, "mvw:topic:${topic.lowercase()}")
            .filter { normalizeText(it.topic) == wanted }
        return withImages(merged(entries).filter { broadcaster == null || it.broadcaster == broadcaster })
    }

    private fun mvwShow(ref: ShowRef, episodes: List<Episode>): Show {
        val first = episodes.firstOrNull() ?: throw SourceException("No episodes for ${ref.key}")
        return Show(
            ref = ref,
            title = first.showTitle,
            broadcaster = first.broadcaster,
            channelLabel = first.channelLabel,
            description = first.description,
            imageUrl = episodes.firstNotNullOfOrNull { it.imageUrl },
            episodeCount = episodes.size,
        )
    }

    private fun merged(entries: List<MvwEntry>): List<Episode> =
        EpisodeMerger.merge(
            entries.mapNotNull { e -> e.toEpisode()?.let { FeedCandidate(it, e.urlVideo, e.size) } },
            broadcasterOrder = BROADCASTER_ORDER,
        ).map { it.episode }

    // 3sat has no own client: newest shows of the channel from MediathekViewWeb.
    private suspend fun threeSatRows(): List<SourceRow> {
        val query = MvwQuery(
            clauses = listOf(MvwClause(MvwField.CHANNEL, "3sat")),
            size = 150,
            durationMinSeconds = 15 * 60,
        )
        val episodes = merged(mvw.query(query, "mvw:channel:3sat", force = true))
        val groups = episodes.groupBy { normalizeText(it.showTitle) }.values
            .sortedByDescending { group -> group.maxOf { it.publishedAt } }
            .take(24)
        val found = images?.imagesFor(groups.map { it.first() }).orEmpty()
        val shows = groups
            .map { group ->
                val e = group.first().let { it.copy(imageUrl = found[it.ref] ?: it.imageUrl) }
                Teaser.OfShow(
                    Show(
                        ref = ShowRef(SourceId.MVW, e.showTitle),
                        title = e.showTitle,
                        broadcaster = Broadcaster.THREESAT,
                        channelLabel = "3sat",
                        description = e.description,
                        imageUrl = e.imageUrl,
                        episodeCount = group.size,
                    ),
                )
            }
        return if (shows.isEmpty()) emptyList() else listOf(SourceRow(Broadcaster.THREESAT, "Neu bei 3sat", shows))
    }

    // Search

    override suspend fun search(query: String): List<Teaser> {
        val text = query.trim()
        if (text.length < 2) return emptyList()
        val queries = if (text.any { it in SELECTOR_CHARS }) MvwSelectorParser.parse(text, size = 100)
        else listOf(MvwQuery(clauses = listOf(MvwClause(listOf(MvwField.TOPIC, MvwField.TITLE), text)), size = 100))
        val entries = coroutineScope {
            queries.map { q -> async { mvw.query(q, "mvw:search:${q.toJson().hashCode()}") } }.awaitAll()
        }.flatten().distinctBy { it.id }
        val allEpisodes = merged(entries)
        val groups = allEpisodes.groupBy { normalizeText(it.showTitle) }.values.sortedByDescending { it.size }.take(12)
        val shownEpisodes = allEpisodes.take(36)
        val found = images?.imagesFor(groups.map { it.first() } + shownEpisodes).orEmpty()
        val episodes = shownEpisodes.withImages(found)
        val shows = groups
            .map { group ->
                val e = group.first().let { it.copy(imageUrl = found[it.ref] ?: it.imageUrl) }
                Teaser.OfShow(
                    Show(
                        ref = ShowRef(SourceId.MVW, e.showTitle),
                        title = e.showTitle,
                        broadcaster = group.groupingBy { it.broadcaster }.eachCount().maxBy { it.value }.key,
                        channelLabel = e.channelLabel,
                        description = e.description,
                        imageUrl = e.imageUrl,
                        episodeCount = group.size,
                    ),
                )
            }
        val teasers = shows + episodes.map { Teaser.OfEpisode(it) }
        index.remember(teasers)
        return teasers
    }

    private companion object {
        const val IMAGE_LIMIT = 40
        val SELECTOR_CHARS = setOf('!', '#', '+', '*', '>', '<')
        val BROADCASTER_ORDER = listOf(Broadcaster.ARD, Broadcaster.ZDF, Broadcaster.ARTE, Broadcaster.THREESAT)
    }
}
