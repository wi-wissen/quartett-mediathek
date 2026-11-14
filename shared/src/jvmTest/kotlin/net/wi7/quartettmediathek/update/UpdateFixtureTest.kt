package net.wi7.quartettmediathek.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.wi7.quartettmediathek.source.Fixtures

/** Parser against a trimmed real answer of the GitHub releases API. */
class UpdateFixtureTest {
    private val json = Fixtures.text("github_release_latest.json")

    @Test
    fun parsesRecordedRelease() {
        val r = parseLatestRelease(json)!!
        assertEquals("v0.1.2", r.tag)
        assertEquals(102, r.versionCode)
        assertEquals(UpdateAssetName, r.apkUrl.substringAfterLast('/'))
        assertEquals(3162376L, r.apkSize)
        assertNull(availableUpdate(json, 102))
        assertEquals(r, availableUpdate(json, 101))
    }
}
