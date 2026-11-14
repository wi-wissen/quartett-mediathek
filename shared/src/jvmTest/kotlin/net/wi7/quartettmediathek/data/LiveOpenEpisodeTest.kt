package net.wi7.quartettmediathek.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import kotlin.test.Test

/** Reproduces "open an episode from a show page" against the live APIs. Only with QM_LIVE=1. */
class LiveOpenEpisodeTest {
    @Test
    fun openEpisodeFromShowPage() = runBlocking {
        if (System.getenv("QM_LIVE") != "1") return@runBlocking
        val graph = QuartettGraph(DatabaseDriverFactory())
        withTimeout(120_000) { graph.catalog.refresh(force = true) }
        val rows = graph.catalog.discoverRows().first { it.isNotEmpty() }
        val shows = rows.flatMap { it.items }.filterIsInstance<Teaser.OfShow>().map { it.show }
        val show = shows.firstOrNull { it.title.contains("Ringe der Macht", ignoreCase = true) } ?: shows.first()
        println("OPEN show ${show.ref} '${show.title}'")
        val eps = graph.catalog.episodes(show.ref)
        println("OPEN episodes=${eps.size}")
        for (ep in eps.take(3)) {
            val r = runCatching { graph.catalog.episode(ep.ref) }
            println("OPEN episode ${ep.ref} '${ep.title}' -> ${r.exceptionOrNull() ?: "ok img=${r.getOrNull()?.imageUrl != null}"} showRef=${ep.showRef}")
            ep.showRef?.let { sr -> println("OPEN others via ${sr} -> " + runCatching { graph.catalog.episodes(sr).size }.let { it.exceptionOrNull() ?: it.getOrNull() }) }
        }
        val fresh = QuartettGraph(DatabaseDriverFactory())
        val r2 = runCatching { fresh.catalog.episode(eps.first().ref) }
        println("OPEN after restart ${eps.first().ref} -> ${r2.exceptionOrNull() ?: "ok"}")
    }
}
