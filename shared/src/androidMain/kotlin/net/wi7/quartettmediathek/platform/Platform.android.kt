package net.wi7.quartettmediathek.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.wi7.quartettmediathek.db.QuartettDatabase

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(QuartettDatabase.Schema, context, "quartett.db")
}
