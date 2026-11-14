package net.wi7.quartettmediathek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.cache.CachePolicy
import net.wi7.quartettmediathek.cache.CacheStore
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.feed.FeedCandidate
import net.wi7.quartettmediathek.feed.candidate
import net.wi7.quartettmediathek.feed.episode
import net.wi7.quartettmediathek.feed.show
import net.wi7.quartettmediathek.feed.t
import net.wi7.quartettmediathek.library.LibraryKeys
import net.wi7.quartettmediathek.library.SqlLibraryRepository
import net.wi7.quartettmediathek.library.SqlSettingsRepository
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.repository.StartScreen
import net.wi7.quartettmediathek.subscription.SubscriptionFactory

private class FakeClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

private fun newDb() = QuartettDatabase(DatabaseDriverFactory().create())

class LibraryTest {
    private val clock = FakeClock(t("2026-11-14T12:00:00Z"))
    private val nanoShow = show("nano", Broadcaster.THREESAT)

    @Test
    fun subscribeUpdateUnsubscribe() = runTest {
        val repo = SqlLibraryRepository(newDb(), clock = clock)
        assertNull(repo.subscriptionFor(nanoShow.ref).first())
        repo.subscribe(nanoShow)
        repo.subscribe(nanoShow) // idempotent
        val subs = repo.subscriptions().first()
        assertEquals(1, subs.size)
        val sub = subs.single()
        assertEquals(sub, repo.subscriptionFor(nanoShow.ref).first())
        assertEquals(setOf(Broadcaster.THREESAT), sub.broadcasters)

        val changed = sub.copy(minDurationMinutes = 0, preferredVariant = Variant.AUDIO_DESCRIPTION, topics = listOf("nano", "nano doku"))
        repo.updateSubscription(changed)
        assertEquals(changed, repo.subscriptions().first().single())

        repo.unsubscribe(sub.id)
        assertTrue(repo.subscriptions().first().isEmpty())
    }

    @Test
    fun bookmarksRoundTripOffline() = runTest {
        val repo = SqlLibraryRepository(newDb(), clock = clock)
        val ep = episode("e1", "nano", "Game of Drones", Broadcaster.THREESAT, "2026-11-12T18:30:00Z")
            .copy(description = "Drohnen", imageUrl = "https://img/1.jpg", season = 2026, episodeNumber = 3, hasSubtitles = true)
        val showTeaser = Teaser.OfShow(nanoShow.copy(genre = "Wissen", episodeCount = 5))
        val epTeaser = Teaser.OfEpisode(ep)
        val key = LibraryKeys.bookmark(epTeaser)
        assertFalse(repo.isBookmarked(key).first())

        repo.toggleBookmark(showTeaser)
        clock.current = t("2026-11-14T12:05:00Z")
        repo.toggleBookmark(epTeaser)
        assertTrue(repo.isBookmarked(key).first())
        // newest first
        assertEquals(listOf(epTeaser, showTeaser), repo.bookmarks().first())

        repo.toggleBookmark(epTeaser)
        assertFalse(repo.isBookmarked(key).first())
        assertEquals(listOf(showTeaser), repo.bookmarks().first())
    }

    @Test
    fun progressAndWatchedThreshold() = runTest {
        val repo = SqlLibraryRepository(newDb(), clock = clock)
        val ep = episode("e1", "nano", "Folge", Broadcaster.THREESAT, "2026-11-12T18:30:00Z", minutes = 100) // 6000 s
        assertNull(repo.progress(ep.ref).first())

        repo.saveProgress(ep, 2210)
        val p = assertNotNull(repo.progress(ep.ref).first())
        assertEquals(2210, p.positionSeconds)
        assertFalse(p.watched)
        assertEquals(ep, p.episode)
        assertEquals(listOf(ep.ref), repo.continueWatching().first().map { it.episode.ref })

        repo.saveProgress(ep, 5699)
        assertFalse(repo.progress(ep.ref).first()!!.watched)
        repo.saveProgress(ep, 5700) // exactly 95 %
        assertTrue(repo.progress(ep.ref).first()!!.watched)
        assertTrue(repo.continueWatching().first().isEmpty())

        repo.setWatched(ep, false)
        assertFalse(repo.progress(ep.ref).first()!!.watched)
        assertTrue(repo.continueWatching().first().isEmpty()) // position reset
        repo.setWatched(ep, true)
        assertTrue(repo.progress(ep.ref).first()!!.watched)
    }

    @Test
    fun continueWatchingNewestFirst() = runTest {
        val repo = SqlLibraryRepository(newDb(), clock = clock)
        val a = episode("a", "nano", "A", Broadcaster.THREESAT, "2026-11-12T18:30:00Z")
        val b = episode("b", "nano", "B", Broadcaster.THREESAT, "2026-11-12T18:30:00Z")
        repo.saveProgress(a, 100)
        clock.current = t("2026-11-14T13:00:00Z")
        repo.saveProgress(b, 100)
        assertEquals(listOf("b", "a"), repo.continueWatching().first().map { it.episode.ref.key })
    }

    @Test
    fun feedUsesProviderAndWatchedState() = runTest {
        val db = newDb()
        val hits = listOf(
            candidate("new", "nano", "Neu", Broadcaster.THREESAT, "2026-11-13T18:30:00Z"),
            candidate("old", "nano", "Alt", Broadcaster.THREESAT, "2026-11-01T18:30:00Z"),
            candidate("clip", "nano", "Clip", Broadcaster.THREESAT, "2026-11-13T19:30:00Z", minutes = 7),
        )
        var calls = 0
        val repo = SqlLibraryRepository(db, feedCandidates = { sub ->
            calls++
            assertEquals("nano", sub.topics.single())
            hits
        }, clock = clock)
        assertTrue(repo.feed().first().isEmpty())
        repo.subscribe(nanoShow)

        val feed = repo.feed().first()
        assertEquals(listOf("new", "old"), feed.map { it.episode.ref.key })
        assertEquals(listOf(true, false), feed.map { it.isNew })
        assertTrue(calls > 0)

        repo.setWatched(feed[0].episode, true)
        assertEquals(listOf(false, false), repo.feed().first().map { it.isNew })
    }

    @Test
    fun failingProviderYieldsEmptyFeedNotCrash() = runTest {
        val repo = SqlLibraryRepository(newDb(), feedCandidates = { error("offline") }, clock = clock)
        repo.subscribe(nanoShow)
        assertTrue(repo.feed().first().isEmpty())
    }

    @Test
    fun settingsStartScreenAndClearCache() = runTest {
        val db = newDb()
        val settings = SqlSettingsRepository(db)
        assertEquals(StartScreen.HOME, settings.startScreen().first())
        settings.setStartScreen(StartScreen.SUBSCRIPTIONS)
        assertEquals(StartScreen.SUBSCRIPTIONS, settings.startScreen().first())
        settings.setStartScreen(StartScreen.BOOKMARKS)
        assertEquals(StartScreen.BOOKMARKS, settings.startScreen().first())

        val cache = CacheStore(db, clock)
        cache.put("k", "v")
        settings.clearCache()
        assertNull(cache.get("k"))
    }

    @Suppress("unused")
    private fun types(c: FeedCandidate) = SubscriptionFactory
}

class CacheStoreTest {
    private val clock = FakeClock(t("2026-11-14T06:00:00Z"))
    private val policy = CachePolicy.START_PAGE

    @Test
    fun getOrFetchUsesFreshCacheAndRefetchesWhenOld() = runTest {
        val cache = CacheStore(newDb(), clock)
        var fetches = 0
        val fetch: suspend () -> String = { "body${++fetches}" }
        assertEquals("body1", cache.getOrFetch("home", policy, fetch = fetch))
        clock.current = clock.current + 23.hours
        assertEquals("body1", cache.getOrFetch("home", policy, fetch = fetch))
        clock.current = clock.current + 2.hours
        assertEquals("body2", cache.getOrFetch("home", policy, fetch = fetch))
        assertEquals("body3", cache.getOrFetch("home", policy, forceRefresh = true, fetch = fetch))
        assertEquals(3, fetches)
    }

    @Test
    fun getOrFetchFallsBackToStaleOnError() = runTest {
        val cache = CacheStore(newDb(), clock)
        cache.put("home", "old")
        clock.current = clock.current + 48.hours
        assertEquals("old", cache.getOrFetch("home", policy) { error("offline") })
        assertFailsWith<IllegalStateException> { cache.getOrFetch("other", policy) { error("offline") } }
    }

    @Test
    fun staleWhileRevalidateEmitsCachedThenFresh() = runTest {
        val cache = CacheStore(newDb(), clock)
        cache.put("feed", "old")
        clock.current = clock.current + 30.hours
        val results = cache.load("feed", CachePolicy.FEED) { "new" }.toList()
        assertEquals(listOf("old" to true, "new" to false), results.map { it.body to it.isStale })
        assertEquals("new", cache.get("feed")!!.body)
    }

    @Test
    fun freshCacheEmitsOnceWithoutFetching() = runTest {
        val cache = CacheStore(newDb(), clock)
        cache.put("feed", "cached")
        val results = cache.load("feed", CachePolicy.FEED) { error("must not fetch") }.toList()
        assertEquals(listOf("cached" to false), results.map { it.body to it.isStale })
    }

    @Test
    fun staleWhileRevalidateKeepsCacheWhenRefreshFailsAndThrowsWithoutCache() = runTest {
        val cache = CacheStore(newDb(), clock)
        assertFailsWith<IllegalStateException> { cache.load("x", policy) { error("offline") }.toList() }
        cache.put("x", "old")
        clock.current = clock.current + 48.hours
        val results = cache.load("x", policy) { error("offline") }.toList()
        assertEquals(listOf("old"), results.map { it.body })
    }

    @Test
    fun clearAndRemove() = runTest {
        val cache = CacheStore(newDb(), clock)
        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals(clock.current, cache.fetchedAt("a"))
        cache.remove("a")
        assertNull(cache.get("a"))
        cache.clear()
        assertNull(cache.get("b"))
    }
}
