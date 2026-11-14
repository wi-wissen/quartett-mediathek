package net.wi7.quartettmediathek.tv.di

import android.content.Context
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.LibraryRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository
import net.wi7.quartettmediathek.repository.SettingsRepository

/** Manual dependency injection: one place that wires the shared repositories for the UI. */
interface AppContainer {
    val catalog: CatalogRepository
    val library: LibraryRepository
    val playback: PlaybackRepository
    val settings: SettingsRepository
}

/**
 * Wires the real implementations from the shared module.
 * TODO(integration): replace the fakes once the shared repositories are implemented.
 */
class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    override val catalog: CatalogRepository get() = TODO("CatalogRepository not wired yet")
    override val library: LibraryRepository get() = TODO("LibraryRepository not wired yet")
    override val playback: PlaybackRepository get() = TODO("PlaybackRepository not wired yet")
    override val settings: SettingsRepository get() = TODO("SettingsRepository not wired yet")
}
