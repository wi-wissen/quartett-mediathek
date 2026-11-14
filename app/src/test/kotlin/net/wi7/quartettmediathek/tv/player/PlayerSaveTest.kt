package net.wi7.quartettmediathek.tv.player

import androidx.lifecycle.viewModelScope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.wi7.quartettmediathek.library.LibraryKeys
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.tv.data.fake.FakeCatalogRepository
import net.wi7.quartettmediathek.tv.data.fake.FakeData
import net.wi7.quartettmediathek.tv.data.fake.FakeLibraryRepository
import net.wi7.quartettmediathek.tv.data.fake.FakePlaybackRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSaveTest {
    private val ref = EpisodeRef(SourceId.MVW, "kueche-3") // not in the fake library yet
    private val library = FakeLibraryRepository()
    private val duration = FakeData.episode("kueche-3").durationSeconds.toLong()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun deps() =
        PlayerDependencies(FakeCatalogRepository(), library, FakePlaybackRepository(), CoroutineScope(UnconfinedTestDispatcher()))

    private fun viewModel() = PlayerViewModel(deps(), ref, null).also { assertIs<PlayerUiState.Ready>(it.state.value) }

    @Test
    fun saveOnExitSurvivesTheViewModelScopeBeingCancelled() = runTest {
        val vm = viewModel()
        vm.reportPosition(321, duration)
        // The player was popped from the back stack: viewModelScope is cancelled before/while the final save runs.
        vm.viewModelScope.cancel()
        vm.saveNow()
        assertEquals(321, library.progress(ref).first()?.positionSeconds)
    }

    @Test
    fun finishingMarksWatched() = runTest {
        val vm = viewModel()
        vm.reportPosition(duration, duration)
        vm.viewModelScope.cancel()
        vm.saveNow()
        assertTrue(library.progress(ref).first()!!.watched)
        assertTrue(LibraryKeys.episode(ref) in library.watchedKeys().first())
    }

    @Test
    fun nothingIsSavedBeforeThePlayerIsReady() = runTest {
        val unknown = EpisodeRef(SourceId.MVW, "does-not-exist")
        val vm = PlayerViewModel(deps(), unknown, null)
        vm.reportPosition(50, 100)
        vm.saveNow()
        assertNull(library.progress(unknown).first())
    }
}
