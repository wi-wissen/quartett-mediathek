package net.wi7.quartettmediathek.platform

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient

/** Creates the platform HTTP client (OkHttp on Android and JVM). */
expect fun createPlatformHttpClient(): HttpClient

/** Creates the SQLDelight driver. [context] is the Android Context on Android and ignored elsewhere. */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}
