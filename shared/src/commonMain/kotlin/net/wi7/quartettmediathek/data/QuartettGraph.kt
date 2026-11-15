package net.wi7.quartettmediathek.data

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.wi7.quartettmediathek.cache.CacheStore
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.library.SqlLibraryRepository
import net.wi7.quartettmediathek.library.SqlSettingsRepository
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.platform.createPlatformHttpClient
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository
import net.wi7.quartettmediathek.repository.SettingsRepository
import net.wi7.quartettmediathek.source.ard.ArdClient
import net.wi7.quartettmediathek.source.arte.ArteClient
import net.wi7.quartettmediathek.source.mvw.MvwClient
import net.wi7.quartettmediathek.source.zdf.ZdfClient

/** Wires the whole data layer. One instance per app process; platforms only supply the database driver. */
class QuartettGraph(
    driverFactory: DatabaseDriverFactory,
    http: HttpClient = createPlatformHttpClient(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val db = QuartettDatabase(driverFactory.create())
    private val cache = CacheStore(db)
    private val index = ContentIndex(db)

    private val ard = ArdClient(http)
    private val zdf = ZdfClient(http)
    private val arte = ArteClient(http)
    private val images = EpisodeImageResolver(http, ard, zdf, arte, cache)
    private val mvw = MvwFeedProvider(MvwClient(http), cache, index, images)

    val library: LibraryRepository = SqlLibraryRepository(db, feedCandidates = { mvw.candidates(it) })

    val catalog: CatalogRepository = DefaultCatalogRepository(
        ard = ard,
        zdf = zdf,
        arte = arte,
        mvw = mvw,
        images = images,
        cache = cache,
        index = index,
        scope = scope,
        snapshotLookup = { ref ->
            library.progress(ref).first()?.episode
                ?: library.bookmarks().first().filterIsInstance<Teaser.OfEpisode>().firstOrNull { it.episode.ref == ref }?.episode
        },
    )

    val playback: PlaybackRepository = DefaultPlaybackRepository(ard, zdf, arte, mvw, index, catalog)

    val settings: SettingsRepository = SqlSettingsRepository(db)

    /** Raw MediathekViewWeb title of the file behind [ref] (tests check which version would play). */
    internal suspend fun rawMvwTitle(ref: net.wi7.quartettmediathek.model.EpisodeRef): String? = index.mvwEntry(ref.key)?.title

    init {
        // Keep the content cache bounded (entries unused for 90 days, at most 20 000 items).
        scope.launch { runCatching { index.prune() } }
        // Older versions could subscribe to the same show twice (show page + search).
        scope.launch { runCatching { (library as? SqlLibraryRepository)?.mergeDuplicateSubscriptions() } }
    }
}
