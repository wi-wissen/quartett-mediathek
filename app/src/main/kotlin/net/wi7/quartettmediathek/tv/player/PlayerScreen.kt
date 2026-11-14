@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package net.wi7.quartettmediathek.tv.player

import net.wi7.quartettmediathek.tv.ui.menu.MenuRow
import net.wi7.quartettmediathek.tv.ui.menu.QDialog
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.StreamInfo
import net.wi7.quartettmediathek.model.SubtitleFormat
import net.wi7.quartettmediathek.model.Variant
import net.wi7.quartettmediathek.player.SeekAccelerator
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.theme.Body
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Full-screen player (Media3 ExoPlayer). Owned by the player work package.
 * Contract for navigation: open with an episode and an optional start position; [onExit] returns to the caller.
 * A [startPositionSeconds] of null means "resume from saved progress", 0 means "from the beginning".
 * Needs [LocalPlayerDependencies] to be provided.
 */
@Composable
fun PlayerScreen(episode: EpisodeRef, startPositionSeconds: Long?, onExit: () -> Unit) {
    val deps = LocalPlayerDependencies.current
    val vm: PlayerViewModel = viewModel(
        key = "player:${episode.source}:${episode.key}",
        factory = PlayerViewModelFactory(deps, episode, startPositionSeconds),
    )
    val state by vm.state.collectAsState()
    // Preferences survive switching to the next episode.
    var speed by rememberSaveable { mutableStateOf(1f) }
    var subtitlesOn by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            PlayerUiState.Loading -> {
                BackHandler(onBack = onExit)
                Message(stringResource(R.string.player_loading), hint = null)
            }
            is PlayerUiState.Error -> {
                BackHandler(onBack = onExit)
                val text = when (s.error) {
                    PlayerError.NOT_FOUND -> R.string.player_error_not_found
                    PlayerError.STREAM -> R.string.player_error_stream
                    PlayerError.PLAYBACK -> R.string.player_error_playback
                }
                Message(stringResource(text), hint = stringResource(R.string.player_error_back))
            }
            is PlayerUiState.Ready -> PlayerContent(
                session = s.session, vm = vm, speed = speed, onSpeed = { speed = it },
                subtitlesOn = subtitlesOn, onSubtitles = { subtitlesOn = it }, onExit = onExit,
            )
        }
    }
}

@Composable
private fun Message(text: String, hint: String?) {
    Box(Modifier.fillMaxSize().padding(80.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Color.White, textAlign = TextAlign.Center, style = TextStyle(fontFamily = Body, fontSize = 20.sp))
            if (hint != null) {
                Text(
                    hint, color = QColors.Muted, textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Body, fontSize = 14.sp), modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun buildMediaItem(stream: StreamInfo): MediaItem {
    val subtitles = stream.subtitles.mapIndexed { i, t ->
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(t.url))
            .setMimeType(if (t.format == SubtitleFormat.WEBVTT) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_TTML)
            .setLanguage(t.language)
            .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }
    return MediaItem.Builder().setUri(stream.url).setMimeType(stream.mimeType).setSubtitleConfigurations(subtitles).build()
}

private val AD_PATTERN = Regex("(?i)(^|[^a-z])(ad|audiodeskription|audio description|audiodescription)([^a-z]|$)")

/** Fallback for streams without role flags: pick the audio group whose label, id or language says "AD". */
private fun selectAudioDescription(player: ExoPlayer, tracks: Tracks) {
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (groups.size < 2) return
    val ad = groups.firstOrNull { g ->
        (0 until g.length).any { i ->
            val f = g.getTrackFormat(i)
            listOfNotNull(f.label, f.id, f.language).any { AD_PATTERN.containsMatchIn(it) }
        }
    } ?: return
    if (ad.isSelected) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(ad.mediaTrackGroup, 0)).build()
}

@Composable
private fun PlayerContent(
    session: PlaybackSession,
    vm: PlayerViewModel,
    speed: Float,
    onSpeed: (Float) -> Unit,
    subtitlesOn: Boolean,
    onSubtitles: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val episode = session.episode
    val wantAd = session.variant == Variant.AUDIO_DESCRIPTION && "ad" in session.stream.audioTracks
    val player = remember(session) {
        ExoPlayer.Builder(context).build().apply {
            if (wantAd) {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioRoleFlags(C.ROLE_FLAG_DESCRIBES_VIDEO).build()
            }
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            setMediaItem(buildMediaItem(session.stream), session.startPositionMs)
            playWhenReady = true
            prepare()
        }
    }

    var playing by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(session.startPositionMs) }
    var durationMs by remember { mutableLongStateOf(episode.durationSeconds * 1000L) }
    var osdVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var barFocused by remember { mutableStateOf(false) }
    var press by remember { mutableStateOf<SeekAccelerator.Press?>(null) }
    var hintHold by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val bookmarked by vm.bookmarked.collectAsState()
    val versionFailed by vm.versionFailed.collectAsState()
    val playPauseFocus = remember { FocusRequester() }
    val accelerator = remember { SeekAccelerator() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) playing = false
            }
            override fun onPlayerError(error: PlaybackException) { vm.onPlaybackError() }
            override fun onTracksChanged(tracks: Tracks) { if (wantAd) selectAudioDescription(player, tracks) }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    vm.reportPosition(player.duration.coerceAtLeast(0) / 1000, player.duration.coerceAtLeast(0) / 1000)
                    vm.saveNow()
                    osdVisible = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            vm.reportPosition(player.currentPosition / 1000, player.duration.coerceAtLeast(0) / 1000)
            vm.saveNow()
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
        vm.reportPosition(player.currentPosition / 1000, player.duration.coerceAtLeast(0) / 1000)
        vm.saveNow()
    }
    LaunchedEffect(speed, player) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(subtitlesOn, player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOn).build()
    }
    // Position polling for the progress bar.
    LaunchedEffect(player) {
        while (true) {
            if (player.duration != C.TIME_UNSET) durationMs = player.duration
            if (press == null) positionMs = player.currentPosition
            vm.reportPosition(player.currentPosition / 1000, durationMs / 1000)
            delay(250)
        }
    }
    // Periodic progress saving.
    LaunchedEffect(player) {
        while (true) {
            delay(PlayerLogic.SAVE_INTERVAL_MS)
            if (player.isPlaying) vm.saveNow()
        }
    }
    // OSD auto hide.
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            if (osdVisible && press == null && PlayerLogic.shouldHideOsd(playing, lastInteraction, SystemClock.uptimeMillis())) {
                osdVisible = false
            }
        }
    }
    LaunchedEffect(hintHold) {
        if (hintHold != null) { delay(900); hintHold = null }
    }
    LaunchedEffect(Unit) {
        runCatching { playPauseFocus.requestFocus() }
    }

    fun touch() { lastInteraction = SystemClock.uptimeMillis(); osdVisible = true }
    fun togglePlay() { if (player.isPlaying) player.pause() else { player.play() }; touch() }
    fun durationSeconds() = (if (player.duration != C.TIME_UNSET) player.duration else durationMs) / 1000

    fun endSeek() {
        val p = press ?: return
        val target = accelerator.release(p, durationSeconds())
        player.seekTo(target * 1000)
        positionMs = target * 1000
        hintHold = target to PlayerLogic.seekHint(target - p.basePositionSeconds, null)
        press = null
        touch()
    }

    fun handleSeekKey(direction: Int, down: Boolean, repeat: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!down) { endSeek(); return }
        touch()
        var p = press
        if (p != null && p.direction != direction) { endSeek(); p = null }
        if (p == null) {
            press = accelerator.start(direction, now, player.currentPosition / 1000)
        } else if (repeat) {
            press = accelerator.repeat(p, now)
        }
    }

    // Back first hides the controls (like everywhere else on TV); only Back without controls leaves the video.
    BackHandler {
        if (osdVisible) {
            endSeek()
            osdVisible = false
        } else {
            vm.reportPosition(player.currentPosition / 1000, durationSeconds())
            vm.saveNow()
            onExit()
        }
    }

    val osdAlpha by animateFloatAsState(if (osdVisible) 1f else 0f, label = "osd")
    val activePress = press
    val seekTarget = when {
        activePress != null -> (activePress.basePositionSeconds + (if (activePress.scrubbing) activePress.deltaSeconds else 0))
            .coerceIn(0, durationSeconds())
        else -> hintHold?.first
    }
    val seekHint = when {
        activePress != null && activePress.scrubbing ->
            PlayerLogic.seekHint(activePress.deltaSeconds, stepLabel(activePress.stepSeconds))
        activePress != null -> null
        else -> hintHold?.second
    }
    val shownPositionSeconds = if (activePress != null && activePress.scrubbing) seekTarget ?: 0 else positionMs / 1000

    val separator = remember { java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator }
    val speedLabel = PlayerLogic.formatSpeed(speed, separator)
    val versionLabel = stringResource(versionLabelRes(session.variant))
    var versionChooser by remember { mutableStateOf(false) }
    if (versionChooser) {
        // Chooser instead of cycling: all versions at once, the playing one checked and focused.
        val currentFocus = remember { FocusRequester() }
        QDialog(
            title = stringResource(R.string.player_version_button),
            subtitle = null,
            onDismiss = { versionChooser = false },
            initialFocus = currentFocus,
        ) {
            session.versions.forEach { option ->
                val current = option.variant == session.variant
                MenuRow(
                    title = stringResource(versionLabelRes(option.variant)),
                    icon = if (current) R.drawable.ic_check else null,
                    modifier = if (current) Modifier.focusRequester(currentFocus) else Modifier,
                    onClick = {
                        versionChooser = false
                        if (!current) vm.switchVersion(option, player.currentPosition)
                        touch()
                    },
                )
            }
        }
    }
    val model = OsdModel(
        showTitle = episode.showTitle,
        channel = episode.channelLabel,
        infoRight = infoRight(
            session.stream, speed, speedLabel, subtitlesOn && session.stream.subtitles.isNotEmpty(),
            version = versionLabel.takeIf { session.versions.size > 1 || session.variant != Variant.STANDARD },
            versionFailed = versionFailed,
        ),
        episodeTitle = episode.title,
        subLine = subLine(episode),
        positionSeconds = positionMs / 1000,
        durationSeconds = durationSeconds(),
        seekTargetSeconds = seekTarget,
        seekHint = seekHint,
        playing = playing,
        speedLabel = speedLabel,
        versionAvailable = session.versions.size > 1,
        versionLabel = stringResource(R.string.player_version_button),
        subtitlesAvailable = session.stream.subtitles.isNotEmpty(),
        subtitlesLabel = stringResource(if (subtitlesOn) R.string.player_subtitles_on else R.string.player_subtitles),
        bookmarked = bookmarked,
        bookmarkLabel = stringResource(if (bookmarked) R.string.player_bookmarked else R.string.player_bookmark),
        hasNext = session.next != null,
        nextLabel = stringResource(R.string.player_next),
    ).let { if (activePress != null && activePress.scrubbing) it.copy(positionSeconds = shownPositionSeconds.coerceAtLeast(0)) else it }

    val actions = OsdActions(
        onPlayPause = { togglePlay() },
        onSpeed = { onSpeed(PlayerLogic.nextSpeed(speed)); touch() },
        onVersion = { versionChooser = true; touch() },
        onSubtitles = { onSubtitles(!subtitlesOn); touch() },
        onBookmark = { vm.toggleBookmark(); touch() },
        onNext = {
            session.next?.let {
                vm.reportPosition(player.currentPosition / 1000, durationSeconds())
                vm.saveNow()
                vm.load(it.ref, 0)
            }
        },
        onBarFocus = { barFocused = it },
    )

    Box(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp
                if (code == KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                // Play/pause keys work everywhere.
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (down && event.nativeKeyEvent.repeatCount == 0) {
                            when (code) {
                                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); touch() }
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); touch() }
                                else -> togglePlay()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                val seekDirection = when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> 0
                }
                val wasHidden = !osdVisible
                if (seekDirection != 0 && (barFocused || wasHidden || press != null)) {
                    if (down) handleSeekKey(seekDirection, true, event.nativeKeyEvent.repeatCount > 0)
                    else if (up) handleSeekKey(seekDirection, false, false)
                    return@onPreviewKeyEvent true
                }
                if (wasHidden) {
                    // The first key only reveals the OSD; it never reaches an invisible button.
                    if (down) {
                        touch()
                        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
                            code == KeyEvent.KEYCODE_NUMPAD_ENTER
                        if (ok && event.nativeKeyEvent.repeatCount == 0) togglePlay()
                        runCatching { playPauseFocus.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }
                if (down) touch()
                false
            }
            ,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = playing
                // Keep subtitles clear of the TV's overscan edge, and above the controls while they are shown.
                view.subtitleView?.setBottomPaddingFraction(if (osdVisible) SUBTITLE_PADDING_WITH_OSD else SUBTITLE_PADDING)
            },
        )
        PlayerShade(Modifier.fillMaxSize().graphicsLayer { alpha = osdAlpha })
        PlayerOsd(model, actions, playPauseFocus, Modifier.graphicsLayer { alpha = osdAlpha })
    }
}

private fun versionLabelRes(variant: Variant): Int = when (variant) {
    Variant.STANDARD -> R.string.player_version_standard
    Variant.ORIGINAL_VERSION -> R.string.player_version_original
    Variant.AUDIO_DESCRIPTION -> R.string.player_version_ad
    Variant.SIGN_LANGUAGE -> R.string.player_version_dgs
}

@Composable
private fun stepLabel(seconds: Int): String =
    if (seconds < 60) stringResource(R.string.player_step_seconds, seconds)
    else stringResource(R.string.player_step_minutes, seconds / 60)

@Composable
private fun infoRight(
    stream: StreamInfo, speed: Float, speedLabel: String, subtitlesOn: Boolean,
    version: String?, versionFailed: Boolean,
): String {
    val format = if (stream.mimeType.contains("mpegurl", ignoreCase = true)) "HLS" else "MP4"
    val quality = stream.height?.let { (if (it >= 720) "HD " else "") + "${it}p" }
    val parts = listOfNotNull(
        if (versionFailed) stringResource(R.string.player_version_failed) else version,
        format, quality,
        speedLabel.takeIf { speed != 1f },
        stringResource(R.string.player_subtitles_on).takeIf { subtitlesOn },
    )
    return parts.joinToString(" · ")
}

@Composable
private fun subLine(episode: Episode): String {
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val published = Instant.ofEpochMilli(episode.publishedAt.toEpochMilliseconds()).atZone(zone)
    val parts = mutableListOf<String>()
    episode.episodeNumber?.let { parts += stringResource(R.string.player_episode_number, it) }
    parts += DateTimeFormatter.ofPattern("EEE, d. MMMM", locale).format(published)
    episode.availableUntil?.let {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(it.toEpochMilliseconds()).atZone(zone))
        parts += stringResource(R.string.player_available_until, date)
    }
    return parts.joinToString(" · ")
}

private const val SUBTITLE_PADDING = 0.12f
private const val SUBTITLE_PADDING_WITH_OSD = 0.34f
