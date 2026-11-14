package net.wi7.quartettmediathek.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.wi7.quartettmediathek.db.QuartettDatabase

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)

actual class DatabaseDriverFactory(private val url: String = JdbcSqliteDriver.IN_MEMORY) {
    actual fun create(): SqlDriver = JdbcSqliteDriver(url).also { QuartettDatabase.Schema.create(it) }
}
