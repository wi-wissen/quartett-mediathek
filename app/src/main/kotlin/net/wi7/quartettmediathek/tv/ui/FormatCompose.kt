package net.wi7.quartettmediathek.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.Instant as JInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.tv.R

/** Composable mappers from the pure presentation model to localized strings. */

private fun Instant.zoned() = JInstant.ofEpochMilli(toEpochMilliseconds()).atZone(ZoneId.systemDefault())

@Composable
fun formatDate(at: Instant, style: FormatStyle = FormatStyle.MEDIUM): String {
    val locale = currentLocale()
    return DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(at.zoned())
}

@Composable
fun formatTime(at: Instant): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(at.zoned())

@Composable
fun currentLocale(): Locale = LocalContext.current.resources.configuration.locales[0]

@Composable
fun Age.label(): String = when (this) {
    Age.JustNow -> stringResource(R.string.age_just_now)
    is Age.Hours -> stringResource(R.string.age_hours, n)
    Age.Yesterday -> stringResource(R.string.age_yesterday)
    is Age.Days -> stringResource(R.string.age_days, n)
    Age.OneWeek -> stringResource(R.string.age_one_week)
    is Age.Weeks -> stringResource(R.string.age_weeks, n)
    is Age.Older -> DateTimeFormatter.ofPattern("dd.MM.").withLocale(currentLocale()).format(published.zoned())
}

@Composable
fun UpdatedLabel.label(): String = when (this) {
    UpdatedLabel.Never -> stringResource(R.string.updated_never)
    is UpdatedLabel.Today -> stringResource(R.string.updated_today, formatTime(at))
    is UpdatedLabel.Yesterday -> stringResource(R.string.updated_yesterday, formatTime(at))
    is UpdatedLabel.On -> formatDate(at, FormatStyle.SHORT) + ", " + formatTime(at)
}

/** "Episode 5 · yesterday" or "Episode 5 · 12 min left": the part of the meta line that is never truncated. */
@Composable
fun episodeMetaSuffix(episode: Episode, progressSeconds: Long?, now: Instant): String {
    val parts = buildList {
        episode.episodeNumber?.let { add(stringResource(R.string.episode_number, it)) }
        if (progressSeconds != null) add(stringResource(R.string.minutes_left, minutesLeft(episode.durationSeconds, progressSeconds)))
        else add(ageOf(episode.publishedAt, now).label())
    }
    return parts.joinToString(" · ")
}

/** Current time, refreshed every 30 seconds. */
@Composable
fun rememberNow(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Clock.System.now()
        }
    }
    return now
}

@Composable
fun clockText(now: Instant): String =
    DateTimeFormatter.ofPattern("EEE, d. MMM · HH:mm", currentLocale()).format(now.zoned())
