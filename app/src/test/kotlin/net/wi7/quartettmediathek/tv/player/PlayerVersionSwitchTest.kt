package net.wi7.quartettmediathek.tv.player

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.repository.CatalogRepository
import net.wi7.quartettmediathek.repository.PlaybackRepository
import net.wi7.quartettmediathek.tv.data.fake.FakeCatalogRepository
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.data.fake.FakeLibraryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerVersionSwitchTest {
    private val de = EpisodeRef(SourceId.MVW, "de")
    private val en = EpisodeRef(SourceId.MVW, "en")
    private val ad = EpisodeRef(SourceId.MVW, "ad")
    private val library = FakeLibraryRepository()
    private val resolved = mutableListOf<Pair<EpisodeRef, Variant>>()
    private val base = FakeData.episode("kueche-3")
    private val episode: Episode = base.copy(
        ref = de, variants = setOf(Variant.STANDARD, Variant.ORIGINAL_VERSION, Variant.AUDIO_DESCRIPTION),
        versionRefs = mapOf(Variant.STANDARD to de, Variant.ORIGINAL_VERSION to en, Variant.AUDIO_DESCRIPTION to ad),
    )
    private val catalog = object : CatalogRepository by FakeCatalogRepository() {
        override suspend fun episode(ref: EpisodeRef) = episode
    }
    private val playback = object : PlaybackRepository {
        override suspend fun stream(ref: EpisodeRef, variant: Variant): StreamInfo {
            resolved += ref to variant
            if (ref == ad) error("gone")
            return StreamInfo("https://example.invalid/${ref.key}.mp4", "video/mp4", 720)
        }
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = PlayerViewModel(
        PlayerDependencies(catalog, library, playback, CoroutineScope(UnconfinedTestDispatcher())), de, 0,
    )

    private fun session(vm: PlayerViewModel) = assertIs<PlayerUiState.Ready>(vm.state.value).session

    @Test
    fun switchingResolvesTheVersionsStreamAndKeepsThePosition() = runTest {
        val vm = vm()
        assertEquals(3, session(vm).versions.size)
        val option = session(vm).versions.first { it.variant != session(vm).variant }
        vm.switchVersion(option, 754_000)
        val s = session(vm)
        assertEquals(Variant.ORIGINAL_VERSION, s.variant)
        assertEquals("https://example.invalid/en.mp4", s.stream.url)
        assertEquals(754_000, s.startPositionMs)
        assertEquals(de, s.episode.ref)
        assertEquals(en to Variant.ORIGINAL_VERSION, resolved.last())
    }

    @Test
    fun progressStaysWithTheEpisodeTheUserStarted() = runTest {
        val vm = vm()
        vm.switchVersion(session(vm).versions.first { it.variant == Variant.ORIGINAL_VERSION }, 100_000)
        vm.reportPosition(321, base.durationSeconds.toLong())
        vm.saveNow()
        assertEquals(321, library.progress(de).first()?.positionSeconds)
        assertNull(library.progress(en).first())
    }

    @Test
    fun failedSwitchKeepsPlayingTheCurrentVersion() = runTest {
        val vm = vm()
        vm.switchVersion(session(vm).versions.first { it.variant == Variant.AUDIO_DESCRIPTION }, 5_000)
        assertEquals(Variant.STANDARD, session(vm).variant)
        assertEquals("https://example.invalid/de.mp4", session(vm).stream.url)
    }
}
