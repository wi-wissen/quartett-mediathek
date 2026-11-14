package net.wi7.quartettmediathek.source

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

object Fixtures {
    fun text(name: String): String =
        (Fixtures::class.java.getResourceAsStream("/fixtures/$name") ?: error("missing fixture $name"))
            .readBytes().decodeToString()
}

/** Answer for a mocked request: status and body. */
class Reply(val body: String, val status: HttpStatusCode = HttpStatusCode.OK)

/** Builds a client whose engine answers via [route]; a null answer fails the test (no network access). */
fun mockClient(requests: MutableList<HttpRequestData> = mutableListOf(), route: (HttpRequestData) -> Reply?): HttpClient =
    HttpClient(
        MockEngine { request ->
            requests += request
            val reply = route(request) ?: error("Unexpected request: ${request.method.value} ${request.url}")
            respond(
                content = reply.body,
                status = reply.status,
                headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
            )
        },
    )
