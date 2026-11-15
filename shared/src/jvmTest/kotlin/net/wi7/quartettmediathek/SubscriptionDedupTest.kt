package net.wi7.quartettmediathek

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.wi7.quartettmediathek.db.QuartettDatabase
import net.wi7.quartettmediathek.library.SqlLibraryRepository
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.platform.DatabaseDriverFactory
import net.wi7.quartettmediathek.subscription.SubscriptionFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** User report: "Ringe der Macht" subscribed from the ZDF show page and from search = two subscriptions, one broken. */
class SubscriptionDedupTest {
    private val title = "Der Herr der Ringe: Die Ringe der Macht"
    private val zdfShow = Show(ShowRef(SourceId.ZDF, "der-herr-der-ringe-die-ringe-der-macht-100"), title, Broadcaster.ZDF, "ZDFneo")
    private val searchShow = Show(ShowRef(SourceId.MVW, title), title, Broadcaster.ZDF, "ZDFneo")

    private fun repo(db: QuartettDatabase = QuartettDatabase(DatabaseDriverFactory().create())) = SqlLibraryRepository(db) to db

    @Test
    fun subscribingTheSameShowTwiceKeepsOneSubscriptionReachableFromBoth() = runBlocking {
        val (library, _) = repo()
        library.subscribe(zdfShow)
        library.subscribe(searchShow)
        val subs = library.subscriptions().first()
        assertEquals(1, subs.size)
        assertNotNull(library.subscriptionFor(zdfShow.ref).first())
        assertNotNull(library.subscriptionFor(searchShow.ref).first())
        library.unsubscribe(library.subscriptionFor(searchShow.ref).first()!!.id)
        assertTrue(library.subscriptions().first().isEmpty())
    }

    @Test
    fun existingDuplicatesAreMergedOnStart() = runBlocking {
        val (library, db) = repo()
        // State left by older versions: two rows for the same show.
        for (show in listOf(zdfShow, searchShow)) {
            val sub = SubscriptionFactory.forShow(show, Clock.System.now()).copy(showRefs = emptyList())
            db.libraryQueries.upsertSubscription(sub.id, net.wi7.quartettmediathek.library.LibraryJson.encodeToString(
                net.wi7.quartettmediathek.subscription.Subscription.serializer(), sub), sub.seenUntilEpochMs, Clock.System.now().toEpochMilliseconds())
        }
        assertEquals(2, library.subscriptions().first().size)
        library.mergeDuplicateSubscriptions()
        val subs = library.subscriptions().first()
        assertEquals(1, subs.size)
        assertNotNull(library.subscriptionFor(zdfShow.ref).first(), "ZDF show page must find the merged subscription")
        assertNotNull(library.subscriptionFor(searchShow.ref).first(), "search show must find the merged subscription")
        Unit
    }
}
