package net.wi7.quartettmediathek.library

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.repository.SettingsRepository
import net.wi7.quartettmediathek.repository.StartScreen

/** `SettingsRepository` on top of the `setting` and `http_cache` tables. */
class SqlSettingsRepository(
    private val db: QuartettDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SettingsRepository {
    override fun startScreen(): Flow<StartScreen> =
        db.libraryQueries.setting(START_SCREEN).asFlow().mapToOneOrNull(dispatcher).map { value ->
            StartScreen.entries.firstOrNull { it.name == value } ?: StartScreen.HOME
        }

    override suspend fun setStartScreen(screen: StartScreen) {
        db.libraryQueries.putSetting(START_SCREEN, screen.name)
    }

    override suspend fun clearCache() {
        db.libraryQueries.clearCache()
    }

    private companion object {
        const val START_SCREEN = "start_screen"
    }
}
