package net.wi7.quartettmediathek.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateLogicTest {
    @Test
    fun tagsBecomeVersionNames() {
        assertEquals("1.2.3", versionNameFromTag("v1.2.3"))
        assertEquals("1.2.3", versionNameFromTag("1.2.3"))
        assertEquals("1.2", versionNameFromTag("v1.2-rc1"))
        assertNull(versionNameFromTag("nightly"))
        assertNull(versionNameFromTag("v1.2.3.4"))
        assertNull(versionNameFromTag("v"))
        assertNull(versionNameFromTag("v1..3"))
    }

    @Test
    fun versionCodeMatchesGradle() {
        assertEquals(10203, versionCodeOf("1.2.3"))
        assertEquals(102, versionCodeOf("0.1.2"))
        assertEquals(1, versionCodeOf("0.0.0"))
        assertEquals(100, versionCodeOf("0.1.0-dev"))
    }

    @Test
    fun comparison() {
        assertTrue(isNewerVersion(versionCodeOf("1.2.4"), versionCodeOf("1.2.3")))
        assertTrue(isNewerVersion(versionCodeOf("1.10.0"), versionCodeOf("1.9.9")))
        assertFalse(isNewerVersion(versionCodeOf("1.2.3"), versionCodeOf("1.2.3")))
        assertFalse(isNewerVersion(versionCodeOf("1.2.2"), versionCodeOf("1.2.3")))
    }

    @Test
    fun parsesRelease() {
        val json = """{"tag_name":"v1.2.4","html_url":"https://x/r","draft":false,"prerelease":false,
            "assets":[{"name":"other.zip","size":1,"browser_download_url":"https://x/o.zip"},
            {"name":"quartett-mediathek.apk","size":1234,"browser_download_url":"https://x/a.apk"}]}"""
        val r = parseLatestRelease(json)!!
        assertEquals("1.2.4", r.versionName)
        assertEquals(10204, r.versionCode)
        assertEquals("https://x/a.apk", r.apkUrl)
        assertEquals(1234L, r.apkSize)
        assertEquals(r, availableUpdate(json, 10203))
        assertNull(availableUpdate(json, 10204))
    }

    @Test
    fun rejectsUnusableReleases() {
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease("""{"message":"Not Found"}"""))
        assertNull(parseLatestRelease("""{"tag_name":"v1.0.0","assets":[]}"""))
        assertNull(parseLatestRelease("""{"tag_name":"v1.0.0","draft":true,"assets":[{"name":"quartett-mediathek.apk","browser_download_url":"https://x"}]}"""))
        assertNull(parseLatestRelease("""{"tag_name":"v1.0.0","assets":[{"name":"quartett-mediathek.apk","browser_download_url":"http://x"}]}"""))
    }

    @Test
    fun checkInterval() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(isCheckDue(0, 1_000))
        assertFalse(isCheckDue(1_000, 1_000 + day - 1))
        assertTrue(isCheckDue(1_000, 1_000 + day))
        assertTrue(isCheckDue(5_000, 1_000))
    }

    @Test
    fun apiUrl() {
        assertEquals("https://api.github.com/repos/a-b/c.d/releases/latest", releasesApiUrl(" a-b/c.d "))
        assertNull(releasesApiUrl(""))
        assertNull(releasesApiUrl("a/b/c"))
        assertNull(releasesApiUrl("a/b?x=1"))
    }
}
