package net.wi7.quartettmediathek.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.data.fake.FakeLibraryRepository
import net.wi7.quartettmediathek.tv.navigation.Routes
import net.wi7.quartettmediathek.tv.ui.key
import net.wi7.quartettmediathek.tv.ui.overlay

class RoutesAndFakesTest {
    @Test
    fun routeKeysSurviveAnyCharacter() {
        val key = "ard:Tatort/äöü ß%20?#"
        assertEquals(key, Routes.decode(Routes.encode(key)))
        val ref = ShowRef(SourceId.ZDF, key)
        assertEquals("show/ZDF/" + Routes.encode(key), Routes.show(ref))
        assertEquals(ref, Routes.showRef("ZDF", Routes.encode(key)))
        assertEquals(EpisodeRef(SourceId.MVW, "x"), Routes.episodeRef("MVW", Routes.encode("x")))
        assertEquals("player/MVW/78/-1", Routes.player(EpisodeRef(SourceId.MVW, "x"), null))
        assertEquals("player/MVW/78/0", Routes.player(EpisodeRef(SourceId.MVW, "x"), 0))
    }

    @Test
    fun fakeLibraryFeedMarksNewEpisodesAndSubscribes() = runTest {
        val library = FakeLibraryRepository()
        val feed = library.feed().first()
        assertTrue(feed.zipWithNext().all { (a, b) -> a.episode.publishedAt >= b.episode.publishedAt }, "newest first")
        assertTrue(feed.any { it.isNew })
        assertFalse(feed.first { it.episode.ref.key == "hafenrevier-3" }.isNew, "watched episodes are not new")

        val sternwarte = FakeData.show("sternwarte")
        assertEquals(null, library.subscriptionFor(sternwarte.ref).first())
        library.subscribe(sternwarte)
        assertEquals("sternwarte", library.subscriptionFor(sternwarte.ref).first()?.id)
        library.unsubscribe("sternwarte")
        assertEquals(null, library.subscriptionFor(sternwarte.ref).first())
    }

    @Test
    fun overlayDecoratesTiles() = runTest {
        val library = FakeLibraryRepository()
        val overlay = library.overlay().first()
        val inProgress = overlay.decorate(FakeData.episode("abgruende-3"))
        assertEquals(2210L, inProgress.progressSeconds)
        assertTrue(overlay.decorate(FakeData.episode("hafenrevier-3")).watched)
        assertTrue(overlay.decorate(FakeData.show("grenzland")).bookmarked)
        assertTrue(overlay.decorate(FakeData.show("tiefsee")).subscribed)
        val toggled = FakeData.show("kueche").let { Teaser.OfShow(it) }
        library.toggleBookmark(toggled)
        assertTrue(library.isBookmarked(toggled.key()).first())
    }
}
