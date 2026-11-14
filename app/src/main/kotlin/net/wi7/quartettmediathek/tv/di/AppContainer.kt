package net.wi7.quartettmediathek.tv.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Wires the repositories for the app.
 * TODO(integration): replace the fakes with the real implementations from the shared module once they exist
 *  (one instance each, created lazily from the application context); nothing else in the UI needs to change.
 */
class DefaultAppContainer(context: Context) : AppContainer {
    @Suppress("unused")
    private val appContext = context.applicationContext
    private val fakes = FakeAppContainer()
    override val catalog: CatalogRepository get() = fakes.catalog
    override val library: LibraryRepository get() = fakes.library
    override val playback: PlaybackRepository get() = fakes.playback
    override val settings: SettingsRepository get() = fakes.settings
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }
