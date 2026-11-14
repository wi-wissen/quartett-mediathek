package net.wi7.quartettmediathek.tv.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import net.wi7.quartettmediathek.data.QuartettGraph
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository
import net.wi7.quartettmediathek.repository.SettingsRepository
import net.wi7.quartettmediathek.tv.data.fake.FakeCatalogRepository
import net.wi7.quartettmediathek.tv.data.fake.FakeLibraryRepository
import net.wi7.quartettmediathek.tv.data.fake.FakePlaybackRepository
import net.wi7.quartettmediathek.tv.data.fake.FakeSettingsRepository

/** Manual dependency injection: one place that wires the shared repositories for the UI. */
interface AppContainer {
    val catalog: CatalogRepository
    val library: LibraryRepository
    val playback: PlaybackRepository
    val settings: SettingsRepository
}

/** Fakes with sample content (see data/fake). Also used by previews and tests. */
class FakeAppContainer : AppContainer {
    override val catalog: CatalogRepository = FakeCatalogRepository()
    override val library: LibraryRepository = FakeLibraryRepository()
    override val playback: PlaybackRepository = FakePlaybackRepository()
    override val settings: SettingsRepository = FakeSettingsRepository()
}

/** The real data layer from the shared module (network, cache, local database). */
class DefaultAppContainer(context: Context) : AppContainer {
    private val graph = QuartettGraph(DatabaseDriverFactory(context.applicationContext))
    override val catalog: CatalogRepository = graph.catalog
    override val library: LibraryRepository = graph.library
    override val playback: PlaybackRepository = graph.playback
    override val settings: SettingsRepository = graph.settings
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }
