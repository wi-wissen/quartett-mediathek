package net.wi7.quartettmediathek.tv

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.tv.ui.Age
import net.wi7.quartettmediathek.tv.ui.UpdatedLabel
import net.wi7.quartettmediathek.tv.ui.ageOf
import net.wi7.quartettmediathek.tv.ui.formatClock
import net.wi7.quartettmediathek.tv.ui.minutesLeft
import net.wi7.quartettmediathek.tv.ui.mvwExpression
import net.wi7.quartettmediathek.tv.ui.options
import net.wi7.quartettmediathek.tv.ui.progressFraction
import net.wi7.quartettmediathek.tv.ui.updatedLabel
import net.wi7.quartettmediathek.tv.ui.withOptions

class FormatTest {
    private val now = Instant.parse("2026-11-14T20:15:00Z")

    @Test
    fun formatsClock() {
        assertEquals("36:50", formatClock(2210))
        assertEquals("0:05", formatClock(5))
        assertEquals("1:02:03", formatClock(3723))
        assertEquals("0:00", formatClock(-4))
    }

    @Test
    fun progressAndMinutesLeft() {
        assertEquals(0.5f, progressFraction(30, 60))
        assertEquals(0f, progressFraction(30, 0))
        assertEquals(1f, progressFraction(90, 60))
        assertEquals(12, minutesLeft(3000, 2290))
        assertEquals(0, minutesLeft(100, 200))
    }

    @Test
    fun agesFollowMockupRules() {
        assertEquals(Age.JustNow, ageOf(now - 20.minutes, now))
        assertEquals(Age.Hours(3), ageOf(now - 3.hours, now))
        assertEquals(Age.Yesterday, ageOf(now - 26.hours, now))
        assertEquals(Age.Days(3), ageOf(now - 3.days, now))
        assertEquals(Age.OneWeek, ageOf(now - 9.days, now))
        assertEquals(Age.Weeks(2), ageOf(now - 15.days, now))
        assertIs<Age.Older>(ageOf(now - 60.days, now))
    }

    @Test
    fun updatedLabels() {
        val zone = ZoneOffset.UTC
        assertEquals(UpdatedLabel.Never, updatedLabel(null, now, zone))
        assertIs<UpdatedLabel.Today>(updatedLabel(now - 5.hours, now, zone))
        assertIs<UpdatedLabel.Yesterday>(updatedLabel(now - 24.hours, now, zone))
        assertIs<UpdatedLabel.On>(updatedLabel(now - 5.days, now, zone))
    }

    private val sub = Subscription(
        id = "nano", title = "nano", topics = listOf("nano"), broadcasters = setOf(Broadcaster.THREESAT), seenUntilEpochMs = 0,
    )

    @Test
    fun expressionMatchesSpecExamples() {
        assertEquals("!3sat #nano >20", mvwExpression(sub))
        val multi = sub.copy(title = "Die Moorbauern", topics = listOf("Die Moorbauern"), broadcasters = setOf(Broadcaster.ARD), minDurationMinutes = 0)
        assertEquals("!ard #die,moorbauern", mvwExpression(multi))
    }

    @Test
    fun optionsRoundTrip() {
        val related = sub.withOptions(sub.options().copy(relatedTopics = true, audioDescription = true))
        assertEquals(listOf("nano", "nano Spezial", "nano Talk", "nano Doku"), related.topics)
        assertEquals(Variant.AUDIO_DESCRIPTION, related.preferredVariant)
        assertEquals("!3sat #nano #nano,spezial #nano,talk #nano,doku >20", mvwExpression(related))
        val back = related.withOptions(related.options().copy(relatedTopics = false, audioDescription = false, fullEpisodesOnly = false))
        assertEquals(listOf("nano"), back.topics)
        assertEquals(Variant.STANDARD, back.preferredVariant)
        assertEquals(0, back.minDurationMinutes)
    }
}
