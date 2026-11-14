package net.wi7.quartettmediathek.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.subscription.SubscriptionExpression
import net.wi7.quartettmediathek.subscription.SubscriptionFactory
import net.wi7.quartettmediathek.subscription.SubscriptionFilter

class SubscriptionAndFeedTest {
    private val now = t("2026-11-14T12:00:00Z")

    private val nano = SubscriptionFactory.forShow(show("nano", Broadcaster.THREESAT), now)

    @Test
    fun factoryDefaults() {
        val s = SubscriptionFactory.forShow(show("Die Friesenjunkies | Impro-Dramedy-Serie", Broadcaster.ARD), now)
        assertEquals(listOf("Die Friesenjunkies"), s.topics)
        assertEquals("Die Friesenjunkies", s.title)
        assertEquals(20, s.minDurationMinutes)
        assertEquals(Variant.STANDARD, s.preferredVariant)
        assertEquals(setOf(Broadcaster.ARD), s.broadcasters)
        assertEquals(t("2026-11-07T12:00:00Z").toEpochMilliseconds(), s.seenUntilEpochMs)
    }

    @Test
    fun expressionFormat() {
        assertEquals("!3sat #nano >20", SubscriptionExpression.format(nano))
        val anstalt = nano.copy(
            topics = listOf("Die Anstalt", "Die Anstalt Spezial"),
            broadcasters = setOf(Broadcaster.ZDF, Broadcaster.ARD),
            minDurationMinutes = 0,
        )
        assertEquals("!zdf !ard #die,anstalt #die,anstalt,spezial", SubscriptionExpression.format(anstalt))
        val ard = nano.copy(topics = listOf("Tatort"), broadcasters = setOf(Broadcaster.ARD))
        assertEquals(
            "!ard !ndr !br #tatort >20",
            SubscriptionExpression.format(ard, mapOf(Broadcaster.ARD to listOf("ard", "NDR", "br"))),
        )
    }

    private fun nanoHit(key: String, topic: String, b: Broadcaster, minutes: Int, at: String = "2026-11-12T18:30:00Z") =
        candidate(key, topic, "Beitrag $key", b, at, minutes)

    @Test
    fun filterMatchesNanoCases() {
        val hits = listOf(
            nanoHit("ok", "nano", Broadcaster.THREESAT, 28),
            nanoHit("upper", "NANO", Broadcaster.THREESAT, 28),
            nanoHit("clip", "nano", Broadcaster.THREESAT, 7),
            nanoHit("talk", "NANO Talk", Broadcaster.THREESAT, 28),
            nanoHit("zdf", "nano", Broadcaster.ZDF, 28),
        )
        assertEquals(listOf("ok", "upper"), SubscriptionFilter.filter(nano, hits).map { it.episode.ref.key })
        val related = SubscriptionFilter.filter(nano, hits, includeRelated = true)
        assertEquals(listOf("ok", "upper", "talk"), related.map { it.episode.ref.key })
    }

    @Test
    fun topicMatchIgnoresUmlautsCaseAndWhitespace() {
        val s = nano.copy(topics = listOf("Käpt'n  Blaubär"))
        assertTrue(SubscriptionFilter.matches(s, episode("1", "kaept'n blaubaer", "x", Broadcaster.THREESAT, "2026-11-12T18:30:00Z")))
        assertFalse(SubscriptionFilter.matches(s, episode("2", "Blaubär", "x", Broadcaster.THREESAT, "2026-11-12T18:30:00Z")))
    }

    @Test
    fun feedIsNewestFirstDedupedFilteredAndMarksNew() {
        val hits = listOf(
            candidate("old", "nano", "Alt", Broadcaster.THREESAT, "2026-11-01T18:30:00Z"),
            candidate("a", "nano", "Game of Drones", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", url = "u1"),
            candidate("a-ad", "nano", "Game of Drones (Audiodeskription)", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", url = "u2"),
            candidate("a-zdf", "nano", "Game of Drones", Broadcaster.ZDF, "2026-11-12T18:30:00Z", url = "u1"),
            candidate("clip", "nano", "Chemie-Nobelpreis", Broadcaster.THREESAT, "2026-11-13T18:30:00Z", 7),
            candidate("srf", "nano", "Chemie-Nobelpreis", Broadcaster.ZDF, "2026-11-13T18:30:00Z", 28),
        )
        val feed = FeedBuilder.build(listOf(nano to hits))
        assertEquals(listOf("a", "old"), feed.map { it.episode.ref.key })
        assertEquals(setOf(Variant.STANDARD, Variant.AUDIO_DESCRIPTION), feed[0].episode.variants)
        assertEquals(listOf(true, false), feed.map { it.isNew })
        assertEquals(nano.id, feed[0].subscriptionId)
    }

    @Test
    fun watchedEpisodesAreNotNewAndFeedMergesSubscriptions() {
        val anstalt = SubscriptionFactory.forShow(show("Die Anstalt", Broadcaster.ZDF), now)
        val nanoHits = listOf(candidate("n", "nano", "Folge", Broadcaster.THREESAT, "2026-11-12T18:30:00Z"))
        val anstaltHits = listOf(candidate("d", "Die Anstalt", "Folge", Broadcaster.ZDF, "2026-11-13T21:15:00Z"))
        val feed = FeedBuilder.build(listOf(nano to nanoHits, anstalt to anstaltHits)) { it.key == "d" }
        assertEquals(listOf("d", "n"), feed.map { it.episode.ref.key })
        assertEquals(listOf(false, true), feed.map { it.isNew })
    }

    @Test
    fun subscriptionPreferredVariantIsUsed() {
        val hits = listOf(
            candidate("a", "nano", "Folge", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", url = "u1"),
            candidate("a-ad", "nano", "Folge (Audiodeskription)", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", url = "u2"),
        )
        val feed = FeedBuilder.build(listOf(nano.copy(preferredVariant = Variant.AUDIO_DESCRIPTION) to hits))
        assertEquals("a-ad", feed.single().episode.ref.key)
    }

    @Suppress("unused")
    private fun unusedType(s: Subscription) = s
}
