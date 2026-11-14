package net.wi7.quartettmediathek.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.subscription.SubscriptionFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * End-to-end check against the live APIs. Skipped unless QM_LIVE=1, so the normal test run stays offline:
 * `QM_LIVE=1 ./gradlew :shared:jvmTest --tests '*LiveSmokeTest*' -i`
 */
class LiveSmokeTest {
    @Test
    fun wholeDataLayerAgainstLiveApis() = runBlocking {
        if (System.getenv("QM_LIVE") != "1") return@runBlocking
        val graph = QuartettGraph(DatabaseDriverFactory())
        graph.catalog.refresh(force = true)
        val rows = graph.catalog.discoverRows().first { it.isNotEmpty() }
        println("DISCOVER " + rows.joinToString { "${it.category}=${it.items.size}" })
        val byBroadcaster = rows.flatMap { it.items }.groupingBy {
            when (it) { is Teaser.OfShow -> it.show.broadcaster; is Teaser.OfEpisode -> it.episode.broadcaster }
        }.eachCount()
        println("BROADCASTERS $byBroadcaster")
        assertTrue(byBroadcaster.keys.containsAll(listOf(Broadcaster.ARD, Broadcaster.ZDF, Broadcaster.ARTE, Broadcaster.THREESAT)))

        for (source in listOf(SourceId.ARD, SourceId.ZDF, SourceId.MVW)) {
            val show = rows.flatMap { it.items }.filterIsInstance<Teaser.OfShow>().first { it.show.ref.source == source }.show
            val eps = graph.catalog.episodes(show.ref)
            println("SHOW $source '${show.title}' episodes=${eps.size} first='${eps.firstOrNull()?.title}'")
            val ep = eps.first()
            val stream = graph.playback.stream(ep.ref)
            println("STREAM $source ${stream.mimeType} ${stream.height}p subs=${stream.subtitles.size} ${stream.url.take(70)}")
        }
        val arteEp = rows.flatMap { it.items }.filterIsInstance<Teaser.OfEpisode>().first { it.episode.ref.source == SourceId.ARTE }.episode
        val arteStream = graph.playback.stream(arteEp.ref)
        println("STREAM ARTE ${arteStream.mimeType} ${arteStream.url.take(70)}")

        val results = graph.catalog.search("nano")
        println("SEARCH nano: shows=${results.count { it is Teaser.OfShow }} episodes=${results.count { it is Teaser.OfEpisode }}")

        val nano = results.filterIsInstance<Teaser.OfShow>().first().show
        graph.library.updateSubscription(SubscriptionFactory.forShow(nano, Clock.System.now()))
        val feed = graph.library.feed().first { it.isNotEmpty() }
        println("FEED ${feed.size} entries, new=${feed.count { it.isNew }}, first='${feed.first().episode.title}' ${feed.first().episode.channelLabel}")
    }
}
