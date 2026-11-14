package net.wi7.quartettmediathek.data

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import net.wi7.quartettmediathek.cache.CacheStore
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.feed.episode
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.network.fetchHead
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.source.Fixtures
import net.wi7.quartettmediathek.source.Reply
import net.wi7.quartettmediathek.source.ard.ArdClient
import net.wi7.quartettmediathek.source.arte.ArteClient
import net.wi7.quartettmediathek.source.mockClient
import net.wi7.quartettmediathek.source.zdf.ZdfClient

private const val ARD_PAGE = "https://www.ardmediathek.de/video/Y3JpZDovL3Rh"
private const val ZDF_PAGE = "https://www.zdf.de/video/shows/die-anstalt-104/die-anstalt-vom-10-november-2026-100"
private const val ARTE_PAGE = "https://www.arte.tv/de/videos/128830-009-A/twist/"
private const val SAT_PAGE = "https://www.3sat.de/wissen/nano/261113-doku-game-of-drones-nano-100.html"

private class TestClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now() = now
}

class EpisodeImageResolverTest {
    private val clock = TestClock()

    private fun resolver(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        zdfGraphQl: (String) -> Reply? = { Reply(Fixtures.text("zdf_video.json")) },
        sat: () -> Reply = { Reply(Fixtures.text("page_og_3sat.html")) },
    ): EpisodeImageResolver {
        val http = mockClient(requests) { r ->
            val url = r.url.toString()
            when {
                url.startsWith("https://api.ardmediathek.de/page-gateway/pages/ard/item/") -> Reply(Fixtures.text("ard_item.json"))
                url == "https://www.zdf.de/" -> Reply(Fixtures.text("zdf_home.html"))
                url == "https://api.zdf.de/graphql" -> zdfGraphQl((r.body as TextContent).text)
                url.startsWith("https://api.arte.tv/api/player/v2/config/de/") -> Reply(Fixtures.text("arte_player.json"))
                url == ZDF_PAGE -> Reply(Fixtures.text("page_og_3sat.html"))
                url == SAT_PAGE -> sat()
                else -> null
            }
        }
        val db = QuartettDatabase(DatabaseDriverFactory().create())
        return EpisodeImageResolver(http, ArdClient(http), ZdfClient(http), ArteClient(http), CacheStore(db, clock), clock = clock)
    }

    @Test
    fun classifiesPagesByHost() {
        assertEquals(ImageStrategy.Ard("Y3JpZDovL3Rh"), classifyEpisodePage(ARD_PAGE))
        assertEquals(ImageStrategy.Zdf("die-anstalt-vom-10-november-2026-100"), classifyEpisodePage(ZDF_PAGE))
        assertEquals(
            ImageStrategy.Zdf("terra-x-die-geschichte-der-deutschen-trailer-100"),
            classifyEpisodePage("https://www.zdf.de/dokumentation/terra-x/terra-x-die-geschichte-der-deutschen-trailer-100.html?x=1"),
        )
        assertEquals(ImageStrategy.Arte("128830-009-A"), classifyEpisodePage(ARTE_PAGE))
        assertEquals(ImageStrategy.OgImage, classifyEpisodePage(SAT_PAGE))
        assertEquals(ImageStrategy.OgImage, classifyEpisodePage("https://www.arte.tv/de/"))
    }

    @Test
    fun parsesOgImageInAnyAttributeOrder() {
        val page = "https://example.org/a/b"
        assertEquals("https://x.org/i.jpg?a=1&b=2", parseOgImage("""<meta content="https://x.org/i.jpg?a=1&amp;b=2" property="og:image">""", page))
        assertEquals("https://example.org/img.png", parseOgImage("<meta property='og:image' content='/img.png'/>", page))
        assertEquals("https://cdn.org/p.jpg", parseOgImage("""<meta property="og:image" content="//cdn.org/p.jpg" />""", page))
        assertNull(parseOgImage("""<meta property="og:title" content="x">""", page))
        assertNull(parseOgImage("""<meta property="og:image" content="">""", page))
    }

    @Test
    fun ardUsesItemApiAndFillsWidth() = runBlocking {
        val url = resolver().imageFor("ard1", ARD_PAGE)
        assertEquals("https://api.ardmediathek.de/image-service/images/urn:ard:image:d8ec07d6877e9bcc?w=960&ch=a161980bd7eb16dc", url)
    }

    @Test
    fun zdfUsesGraphQlTeaserImage() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val url = resolver(seen).imageFor("zdf1", ZDF_PAGE)
        assertEquals("https://epg-image.zdf.de/fotobase-webdelivery/images/91d7bcbc-4c2d-431b-a0af-7c32cfb18d1b?layout=768x432", url)
        assertTrue(seen.any { it.url.toString() == "https://api.zdf.de/graphql" })
        assertTrue(seen.none { it.url.toString() == ZDF_PAGE })
    }

    @Test
    fun zdfFallsBackToOgImageWhenGraphQlFails() = runBlocking {
        val url = resolver(zdfGraphQl = { Reply("{}", HttpStatusCode.InternalServerError) }).imageFor("zdf2", ZDF_PAGE)
        assertEquals("https://www.3sat.de/assets/261113-bild-doku-games-nano-100~1280x720?cb=1794496224716", url)
    }

    @Test
    fun arteUsesPlayerConfig() = runBlocking {
        val url = resolver().imageFor("arte1", ARTE_PAGE)
        assertEquals("https://api-cdn.arte.tv/img/v2/image/RcwVPYdZUvqkWJEy53RfX6/940x530", url)
    }

    @Test
    fun otherSitesUseOgImage() = runBlocking {
        val url = resolver().imageFor("sat1", SAT_PAGE)
        assertEquals("https://www.3sat.de/assets/261113-bild-doku-games-nano-100~1280x720?cb=1794496224716", url)
    }

    @Test
    fun secondLookupComesFromCache() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val r = resolver(seen)
        val first = r.imageFor("sat1", SAT_PAGE)
        val count = seen.size
        assertEquals(first, r.imageFor("sat1", SAT_PAGE))
        assertEquals(count, seen.size)
    }

    @Test
    fun cacheExpiresAfterThirtyDays() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val r = resolver(seen)
        r.imageFor("sat1", SAT_PAGE)
        val count = seen.size
        clock.now += 29.days
        r.imageFor("sat1", SAT_PAGE)
        assertEquals(count, seen.size)
        clock.now += 2.days
        r.imageFor("sat1", SAT_PAGE)
        assertEquals(count + 1, seen.size)
    }

    @Test
    fun failureGivesNullAndIsRetriedAfterAnHour() = runBlocking {
        var broken = true
        val seen = mutableListOf<HttpRequestData>()
        val r = resolver(seen, sat = { if (broken) Reply("", HttpStatusCode.ServiceUnavailable) else Reply(Fixtures.text("page_og_3sat.html")) })
        assertNull(r.imageFor("sat1", SAT_PAGE))
        assertEquals(1, seen.size)
        assertNull(r.imageFor("sat1", SAT_PAGE)) // negative result is cached
        assertEquals(1, seen.size)
        broken = false
        clock.now += 2.hours
        assertTrue(r.imageFor("sat1", SAT_PAGE)!!.startsWith("https://www.3sat.de/assets/"))
    }

    @Test
    fun pageWithoutImageIsCachedAsNone() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val r = resolver(seen, sat = { Reply("<html><head><title>x</title></head><body></body></html>") })
        assertNull(r.imageFor("sat1", SAT_PAGE))
        assertNull(r.imageFor("sat1", SAT_PAGE))
        assertEquals(1, seen.size)
        clock.now += 20.days
        assertNull(r.imageFor("sat1", SAT_PAGE))
        assertEquals(1, seen.size)
    }

    @Test
    fun fillOnlyTouchesEpisodesWithoutImageUpToLimit() = runBlocking {
        fun ep(key: String, page: String?) =
            episode(key, "Nano", "T$key", Broadcaster.THREESAT, "2026-11-0${key}T10:00:00Z").copy(websiteUrl = page)
        val withImage = ep("1", SAT_PAGE).copy(imageUrl = "https://keep/me.jpg")
        val list = listOf(withImage, ep("2", SAT_PAGE), ep("3", null), ep("4", SAT_PAGE))
        val seen = mutableListOf<HttpRequestData>()
        val filled = resolver(seen).fill(list, limit = 1)
        assertEquals("https://keep/me.jpg", filled[0].imageUrl)
        assertTrue(filled[1].imageUrl!!.contains("261113-bild"))
        assertNull(filled[2].imageUrl)
        assertNull(filled[3].imageUrl) // beyond the limit
        assertEquals(1, seen.size)
        assertEquals(EpisodeRef(SourceId.MVW, "2"), filled[1].ref)
    }

    @Test
    fun fetchHeadStopsAfterHeadTag() = runBlocking {
        val big = "<html><head><meta property=\"og:image\" content=\"https://x/y.jpg\"></head><body>" +
            "x".repeat(2_000_000) + "</body></html>"
        val http = mockClient { Reply(big) }
        val text = http.fetchHead("https://example.org/", maxBytes = 150_000)
        assertTrue(text.contains("</head>"))
        assertTrue(text.length < 100_000, "read ${text.length} chars")
        val capped = mockClient { Reply("a".repeat(500_000)) }.fetchHead("https://example.org/", maxBytes = 50_000)
        assertEquals(50_000, capped.length)
    }
}
