package com.example.bluehive.details.tvshowdetails

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bluehive.details.shared.playback.PlaybackSession
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import com.exjunk.lib.thanos.vanish.VanishContainer
import com.exjunk.lib.thanos.vanish.VanishGLSurfaceView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How long the snap holds the press. Playback starts only after it. */
internal const val SNAP_DURATION_MS = 1000L   // the user's call, 2026-10-27 (was 2 s)

/**
 * The duration handed to the particle renderer, longer than the hold so the
 * dust fills all of it. ThanosEffect's shader ends a particle at
 *   delay + life = (x / screenWidth) * D/4 + (D/4) * (1 + r),  r in [0, 1),
 * so the last one off a card centred on the screen (right edge at ~0.68 of
 * the width) goes at ~0.67 D: at D = 1000 the dust was gone by ~670 ms and
 * the rest of the second was empty. D = 1500 ends it at the 1 s mark, as
 * the loading screen begins.
 */
internal const val SNAP_RENDER_DURATION_MS = 1500L

/**
 * The dust's first frames overlap the card before the card hides, so nothing
 * blinks. 200 ms, not Favorites' 150: the surface is made fresh on each press
 * now, so its first frame (a new GL context, the shaders compiled) comes later.
 */
private const val SNAP_HIDE_DELAY_MS = 200L

/** A capture that takes longer than this is abandoned: the press goes ahead without dust. */
private const val SNAP_CAPTURE_TIMEOUT_MS = 600L

/** After the press's action, how long to wait for the extraction to begin before giving the card back. */
private const val SNAP_START_WAIT_MS = 3000L

/** After a successful extraction, how long the player may take to cover this screen. */
private const val SNAP_PLAYER_OPEN_WAIT_MS = 3000L

private const val TAG = "EpisodeSnap"

/**
 * The episode overlay's snap: a Watch on ... press turns the card to dust
 * (ThanosEffect's particle renderer) and the press's action -- starting the
 * source chain -- runs only once the dust has finished.
 *
 * ONE PATH, THREE PHASES, and every way out of each is handled:
 *
 *   Idle     -> the card is shown and takes presses.
 *   Snapping -> the card is captured and handed to the particle renderer;
 *               the card hides once the dust covers it; keys are swallowed.
 *               After [SNAP_DURATION_MS] the action runs, exactly once.
 *   Playing  -> the card stays hidden while the extraction runs; when it ends
 *               (an error, a cancel, or -- after the player opened -- the
 *               viewer coming back from it) the renderer is reset and the
 *               card comes back, focus still on the pressed button.
 *
 * What it guards against:
 *   - a second press while snapping or playing: ignored (phase check);
 *   - no renderer yet, a capture that fails or hangs: no dust, the card hides
 *     and the action runs at once -- the snap can delay playback, never block it;
 *   - the action throwing: the card comes back and the phase returns to Idle;
 *   - the screen closing mid-snap: the scope is cancelled with the screen, so
 *     the action never runs for a page that is gone;
 *   - an extraction that never begins: the card returns after [SNAP_START_WAIT_MS].
 *
 * WHY IT CAPTURES THE CARD ITSELF: the library's own capture (VanishEffect's
 * vanishable) replays the card's drawing into an android.graphics.Picture,
 * and a Picture cannot hold layered content -- the still and the focus glow
 * (both drawn on graphics layers) vanished from the card (v463). Recording
 * the card into a Compose GraphicsLayer and taking its bitmap keeps all of
 * it, and the card draws normally the rest of the time.
 */
@Stable
internal class EpisodeSnapState(
    private val scope: CoroutineScope,
    /** Starts the dust; false when there is nothing to animate with. Swapped in tests. */
    private val startDust: suspend (EpisodeSnapState) -> Boolean = { it.startRendererDust() },
    /** android.util.Log outside tests, where it does not exist. */
    private val warn: (String, Throwable) -> Unit = { message, t -> Log.w(TAG, message, t) },
) {

    enum class Phase { Idle, Snapping, Playing }

    var phase by mutableStateOf(Phase.Idle)
        private set

    /** False while the dust (or the loading screen) has the card's place. */
    var cardVisible by mutableStateOf(true)
        private set

    /**
     * The particle renderer's surface is wanted: from a press until the dust
     * is gone. It is NOT kept while the overlay idles or while the player is
     * up -- a live GL surface behind the player has to be rebuilt before this
     * screen can draw its first frame on the way back, which added ~60 ms to
     * every player close (v463-v467: 305 ms average against 246 ms in v462).
     */
    var rendererWanted by mutableStateOf(false)
        private set

    /** The particle renderer, once its surface exists; null otherwise. */
    var renderer by mutableStateOf<VanishGLSurfaceView?>(null)
        internal set

    internal var layer: GraphicsLayer? = null
    internal var bounds: Rect? = null

    /**
     * The anime card's English / Japanese row: snapped on its own, the card
     * staying (v585). Shares this snap's renderer -- see RowSnapState.
     */
    val row = RowSnapState(scope, rendererOf = { renderer }, warn = warn)

    /**
     * A failure the playback flow reports outside the session -- the anime
     * flow's "couldn't reach" and "no dub" notices. Read in snapshot scope,
     * so it may read Compose state. Set by the episode overlay.
     */
    internal var flowFailed: () -> Boolean = { false }

    /**
     * Snap the card, then run [action]. A press while a snap or its playback
     * is in progress does nothing.
     */
    fun snapThen(action: () -> Unit) {
        if (phase != Phase.Idle) return
        phase = Phase.Snapping
        rendererWanted = true
        scope.launch {
            val dust = try {
                withTimeoutOrNull(SNAP_CAPTURE_TIMEOUT_MS) { startDust(this@EpisodeSnapState) } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warn("capture failed; playing without the snap", t)
                false
            }
            if (dust) {
                delay(SNAP_HIDE_DELAY_MS)
                cardVisible = false
                delay(SNAP_DURATION_MS - SNAP_HIDE_DELAY_MS)
            } else {
                cardVisible = false
                rendererWanted = false
            }
            phase = Phase.Playing
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warn("the press's action failed", t)
                finish()
                return@launch
            }
            // The renderer runs longer than the hold (SNAP_RENDER_DURATION_MS):
            // let the last of the dust go, then drop the surface.
            if (dust) {
                delay(SNAP_RENDER_DURATION_MS - SNAP_DURATION_MS + SNAP_HIDE_DELAY_MS)
                rendererWanted = false
            }
        }
    }

    /**
     * As [snapThen], but a press that arrives while a snap or its playback is
     * still in progress runs [action] at once instead of being dropped. For
     * the anime flow's server choice: it comes from a dialog, never twice
     * from the card, and must never be lost.
     */
    fun snapThenOrNow(action: () -> Unit) {
        if (phase == Phase.Idle) snapThen(action) else action()
    }

    /**
     * Playing: wait for the extraction to begin (or fail at once), then to
     * end, and give the card back -- but after a SUCCESSFUL extraction, not
     * until the viewer is back from the player. The player takes a moment to
     * open over this screen, and handing the card back as the extraction
     * ended flashed it between the loading screen and the player (v466). So:
     * an error or a cancel (or [flowFailed]) gives it back at once;
     * otherwise wait for the screen to be covered (stopped) and then resumed.
     * A player that never opens (the launch refused) gives it back after
     * [SNAP_PLAYER_OPEN_WAIT_MS].
     *
     * STOPPED, NOT PAUSED (v481): the anime flow's on-device extractor is an
     * activity of its own, and its result can arrive before this screen is
     * resumed again -- already "paused", so the old check passed at once and
     * the brief resume before the player covered the screen gave the card
     * back for a frame. Only a full-screen activity stops this one.
     */
    suspend fun awaitPlaybackEnd(session: PlaybackSession, lifecycle: Lifecycle) {
        withTimeoutOrNull(SNAP_START_WAIT_MS) {
            snapshotFlow { session.isExtracting || session.extractionError != null || flowFailed() }.first { it }
        }
        snapshotFlow { session.isExtracting }.first { !it }
        val playerOpening = session.extractionError == null && !session.extractCancelled && !flowFailed()
        if (playerOpening) {
            val covered = withTimeoutOrNull(SNAP_PLAYER_OPEN_WAIT_MS) {
                lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.STARTED) }
            }
            if (covered != null) {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            }
        }
        finish()
    }

    /**
     * Runs [whilePlaying] each time the phase turns Playing, cancelling it if
     * the phase leaves Playing first. Read in a flow, so the composable that
     * starts it never recomposes on a phase change.
     */
    suspend fun followPlayback(whilePlaying: suspend () -> Unit) {
        snapshotFlow { phase == Phase.Playing }.collectLatest { playing -> if (playing) whilePlaying() }
    }

    /** Back to Idle, the card shown. Internal so a test can end a playback. */
    internal fun finish() {
        try {
            renderer?.resetAnimation()
        } catch (t: Throwable) {
            warn("renderer reset failed", t)
        }
        rendererWanted = false
        cardVisible = true
        phase = Phase.Idle
    }

    /** Hands the card's picture to the renderer; false when there is nothing to animate with. */
    internal suspend fun startRendererDust(): Boolean = startDustOn({ renderer }, layer, bounds)
}

/**
 * Hands a recorded picture to the particle renderer; false when there is
 * nothing to animate with. Shared by the card's snap and the row's.
 */
private suspend fun startDustOn(
    rendererOf: () -> VanishGLSurfaceView?,
    layer: GraphicsLayer?,
    bounds: Rect?,
): Boolean {
    // The surface is made on the press (rendererWanted): wait for it.
    val gl = snapshotFlow { rendererOf() }.first { it != null }!!
    layer ?: return false
    val box = bounds ?: return false
    if (box.width < 1f || box.height < 1f) return false
    var bitmap = layer.toImageBitmap().asAndroidBitmap()
    // The renderer uploads it as a GL texture, which a HARDWARE bitmap cannot be.
    // The copy reads the card back from the GPU (~2 MB): off the main thread,
    // which is drawing the press (v515).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        val hardware = bitmap
        bitmap = withContext(Dispatchers.Default) { hardware.copy(Bitmap.Config.ARGB_8888, false) } ?: return false
    }
    gl.startAnimationWithBitmap(
        bitmap,
        android.graphics.Rect(
            box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()
        ),
    )
    return true
}

/** How long Default server / Choose a server take to fade in once the dust is gone (v585). */
internal const val SERVER_PAIR_FADE_MS = 350

/**
 * The anime card's language row snap (v585): English or Japanese turns the
 * row -- both buttons -- to dust, and only once the dust is gone does
 * [snapThen]'s action run, which puts Default server / Choose a server in its
 * place (they fade in, SERVER_PAIR_FADE_MS). The card stays; no second
 * overlay. The same capture and renderer as the card's snap, so it degrades
 * the same way: no renderer or a failed capture, and the row simply goes.
 *
 * [busy] from the press until the pair has faded in: the card swallows keys
 * and the page refuses focus, so nothing lands behind the card while the row
 * has no button in it.
 */
@Stable
internal class RowSnapState(
    private val scope: CoroutineScope,
    private val rendererOf: () -> VanishGLSurfaceView?,
    private val warn: (String, Throwable) -> Unit,
    /** Starts the dust; false when there is nothing to animate with. Swapped in tests. */
    private val startDust: suspend (RowSnapState) -> Boolean = { startDustOn(rendererOf, it.layer, it.bounds) },
) {
    var busy by mutableStateOf(false)
        private set

    /** False while the dust has the row's place. */
    var rowVisible by mutableStateOf(true)
        private set

    /** The renderer is wanted for this row's dust -- see EpisodeSnapRendererWhenWanted. */
    var rendererWanted by mutableStateOf(false)
        private set

    internal var layer: GraphicsLayer? = null
    internal var bounds: Rect? = null

    /** Snap the row, then run [action]. A press while one is running does nothing. */
    fun snapThen(action: () -> Unit) {
        if (busy) return
        busy = true
        rendererWanted = true
        scope.launch {
            val dust = try {
                withTimeoutOrNull(SNAP_CAPTURE_TIMEOUT_MS) { startDust(this@RowSnapState) } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warn("row capture failed; switching without the snap", t)
                false
            }
            if (dust) {
                delay(SNAP_HIDE_DELAY_MS)
                rowVisible = false
                delay(SNAP_DURATION_MS - SNAP_HIDE_DELAY_MS)
            } else {
                rowVisible = false
            }
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                warn("the row press's action failed", t)
            }
            // The row's place is the pair's now; it fades in from its own alpha.
            rowVisible = true
            delay(SERVER_PAIR_FADE_MS.toLong())
            busy = false
            if (dust) delay((SNAP_RENDER_DURATION_MS - SNAP_DURATION_MS - SERVER_PAIR_FADE_MS).coerceAtLeast(0L))
            rendererWanted = false
        }
    }
}

/**
 * On the language row: records its drawing for the dust (and draws that, so
 * it looks exactly as before), tracks its bounds and hides it while the dust
 * has its place.
 */
internal fun Modifier.rowSnap(row: RowSnapState): Modifier = this
    .graphicsLayer { alpha = if (row.rowVisible) 1f else 0f }
    .onGloballyPositioned { row.bounds = it.boundsInRoot() }
    .drawWithContent {
        val layer = row.layer
        if (layer == null) {
            drawContent()
        } else {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }

/**
 * The overlay's snap state, and the wait for the playback it started. Held by
 * the screen, not the overlay, so the page can refuse focus while it runs --
 * see blockFocusWhileSnapping.
 */
@Composable
internal fun rememberEpisodeSnap(session: PlaybackSession): EpisodeSnapState {
    val scope = rememberCoroutineScope()
    val snap = remember { EpisodeSnapState(scope) }
    snap.layer = rememberGraphicsLayer()
    snap.row.layer = rememberGraphicsLayer()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Followed in a flow, not read here: this runs in the screen function, and
    // reading the phase recomposed the whole screen three times a press (v515).
    LaunchedEffect(snap) { snap.followPlayback { snap.awaitPlaybackEnd(session, lifecycle) } }
    return snap
}

/**
 * On the card: records its drawing into the snap's layer (and draws that,
 * so the card looks exactly as before), tracks its bounds for the renderer,
 * hides it while the dust has its place, and swallows keys while snapping.
 */
internal fun Modifier.episodeSnapCard(snap: EpisodeSnapState): Modifier = this
    .onPreviewKeyEvent { snap.phase == EpisodeSnapState.Phase.Snapping || snap.row.busy }
    .graphicsLayer { alpha = if (snap.cardVisible) 1f else 0f }
    .onGloballyPositioned { snap.bounds = it.boundsInRoot() }
    .drawWithContent {
        val layer = snap.layer
        if (layer == null) {
            drawContent()
        } else {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }

/**
 * On the page (under the overlay): while a snap or its playback runs, focus
 * cannot enter it. The card hides and the loading screen's trap takes focus a
 * frame after it appears; if focus is dropped anywhere in between, Android
 * hands it back to the first focusable thing on the page -- Add to Favorites,
 * which lit up through the dust (v465). With the page closed to focus there
 * is nothing there for it to land on. Explicit requestFocus() calls (the
 * return to the pressed button, Back to the tile) are not affected.
 */
// focusProperties.enter: experimental in Compose 1.7, stable (as onEnter) later.
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.blockFocusWhileSnapping(snap: EpisodeSnapState): Modifier = this
    .focusProperties {
        enter = {
            if (snap.phase != EpisodeSnapState.Phase.Idle || snap.row.busy) FocusRequester.Cancel
            else FocusRequester.Default
        }
    }
    .focusGroup()

/**
 * The dust's GL surface (ThanosEffect's VanishContainer, which puts it on top
 * of the window), composed only while [EpisodeSnapState.rendererWanted]. The
 * snap is told about it when it exists and forgets it when it goes.
 */
@Composable
internal fun EpisodeSnapRenderer(snap: EpisodeSnapState) {
    DisposableEffect(snap) { onDispose { snap.renderer = null } }
    VanishContainer(
        modifier = Modifier.fillMaxSize(),
        onGLSurfaceViewCreated = {
            it.setAnimationConfig(duration = SNAP_RENDER_DURATION_MS.toFloat(), particleSize = 2f)
            snap.renderer = it
        },
    ) {}
}
