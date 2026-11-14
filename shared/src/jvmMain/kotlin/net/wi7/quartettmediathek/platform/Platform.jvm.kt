package net.wi7.quartettmediathek.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.wi7.quartettmediathek.db.QuartettDatabase
import java.util.Properties

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)

/**
 * [url] = `jdbc:sqlite:<file>` keeps data across "restarts" (a new factory on the same file), like the app on a TV.
 * The schema is created for a new database and migrated for an existing one (SQLite `user_version`).
 */
actual class DatabaseDriverFactory(private val url: String = JdbcSqliteDriver.IN_MEMORY) {
    actual fun create(): SqlDriver = JdbcSqliteDriver(url, Properties(), QuartettDatabase.Schema)
}
