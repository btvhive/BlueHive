package com.example.bluehive.webview.hls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bluehive.utilities.AppTypography

// ── Up Next (v580-v582) ─────────────────────────────────────────────────────
//
// The last minute of an episode shows the next one -- its still, its number
// and title -- over the player while the credits keep playing, counting down
// from 60, and the details screen opens it through the usual backend
// extraction when this one ends. Every third episode in a row nobody touches
// the remote for, a "Still watching?" prompt comes up five minutes before the
// end (v582). Answered, the card follows at the minute and the next episode
// plays; unanswered, no card shows and the player closes when the episode
// ends. A press in that last minute still brings the card back.
// TV shows (HlsPlayerActivity) and anime (MiruroPlayerActivity) alike -- see
// UpNextDirector.
//
// The clock is the STREAM's, never the wall's: the countdown is the position
// against the duration, so a pause or a stall holds it, and a seek moves it.

/** The card shows, and counts down, over the episode's last this-many ms (v581: a minute). */
internal const val UP_NEXT_WINDOW_MS = 60_000L

/** The Still Watching prompt shows this long before the end, until the card's minute (v582: five minutes). */
internal const val STILL_WATCHING_LEAD_MS = 5L * 60_000L

/** The episode, counted in a row without a remote press, that asks "Still watching?". */
internal const val UNATTENDED_LIMIT = 3

/** Shorter than this is no episode (a broken or trailer-length stream): no card. */
internal const val UP_NEXT_MIN_DURATION_MS = 5L * 60_000L

/** How often the player's position is read. */
internal const val UP_NEXT_TICK_MS = 250L

internal enum class UpNextPhase { Hidden, StillWatching, Card }

/** The next episode, from the player's intent: none at a season's end. */
internal data class NextEpisode(
    val season: Int,
    val episode: Int,
    val title: String?,
    /** TMDB's w300 still as the episodes API sends it: small on purpose. */
    val stillUrl: String?,
)

/**
 * What the overlay shows with [remainingMs] of the stream left. Pure, so the
 * timing is tested without a player.
 */
internal fun upNextPhaseFor(
    remainingMs: Long,
    durationMs: Long,
    hasNext: Boolean,
    cancelled: Boolean,
    askStillWatching: Boolean,
    promptDismissed: Boolean,
    /** Still Watching went unanswered: this episode is the last, so no card. */
    stopAfterThis: Boolean = false,
): UpNextPhase = when {
    !hasNext || durationMs < UP_NEXT_MIN_DURATION_MS || remainingMs < 0 -> UpNextPhase.Hidden
    remainingMs <= UP_NEXT_WINDOW_MS ->
        if (cancelled || stopAfterThis) UpNextPhase.Hidden else UpNextPhase.Card
    remainingMs <= STILL_WATCHING_LEAD_MS && askStillWatching && !promptDismissed -> UpNextPhase.StillWatching
    else -> UpNextPhase.Hidden
}

/** Whether this episode asks: the [UNATTENDED_LIMIT]th in a row with no press. */
internal fun shouldAskStillWatching(unattendedBefore: Int, touchedThisEpisode: Boolean): Boolean =
    !touchedThisEpisode && unattendedBefore + 1 >= UNATTENDED_LIMIT

/** The count the next episode starts from: a press anywhere in this one resets it. */
internal fun unattendedAfter(unattendedBefore: Int, touchedThisEpisode: Boolean): Int =
    if (touchedThisEpisode) 0 else unattendedBefore + 1

/** The whole seconds the card counts down: 60 at the window's start, never 0. */
internal fun upNextSeconds(remainingMs: Long): Int =
    ((remainingMs + 999) / 1000).toInt().coerceIn(1, (UP_NEXT_WINDOW_MS / 1000).toInt())

/** "S1 E2 · Compulsion", or "S1 E2" without a title. */
internal fun upNextHeading(next: NextEpisode): String {
    val number = "S${next.season} E${next.episode}"
    return next.title?.takeIf { it.isNotBlank() }?.let { "$number  ·  $it" } ?: number
}

// ── The look: the approved mock-up (B mirrored, 2026-10-31) at half size ────
// v581 halved every size; the text went to 60 % so it still reads from the sofa.

internal val UP_NEXT_BLUE  = Color(0xFF2563EB)
private val UP_NEXT_LABEL  = Color(0xFF96AAE6)
private val UP_NEXT_DIM    = Color(0xFFC8CDDC)
internal val UP_NEXT_STILL_W = 110.dp
internal val UP_NEXT_STILL_H = 62.dp

private fun Modifier.bottomScrim(fromFraction: Float, strength: Float) = background(
    Brush.verticalGradient(
        fromFraction to Color.Transparent,
        1f to Color.Black.copy(alpha = strength),
    )
)

/**
 * The Up Next card: the still on the right with the countdown along its
 * bottom, the number and title right-aligned beside it, then "Watch next
 * episode" and Cancel. [focus] 0 is Watch, 1 is Cancel.
 */
@Composable
internal fun BoxScope.UpNextCard(
    next: NextEpisode,
    remainingMs: Long,
    focus: Int,
) {
    Box(Modifier.matchParentSize().bottomScrim(0.74f, 0.92f))
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 23.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.height(UP_NEXT_STILL_H),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "Next episode in ${upNextSeconds(remainingMs)}",
                color = UP_NEXT_LABEL,
                fontSize = 8.sp,
                fontFamily = AppTypography.interSemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = upNextHeading(next),
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = AppTypography.interBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(280.dp),
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                UpNextButton("Watch next episode", focused = focus == 0, playIcon = true)
                UpNextButton("Cancel", focused = focus == 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(UP_NEXT_STILL_W, UP_NEXT_STILL_H)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF14161E))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(next.stillUrl)
                    .size(300, 169)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Fills from the right toward the words, as the seconds go.
            val elapsed = (1f - remainingMs.toFloat() / UP_NEXT_WINDOW_MS).coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.24f))
            ) {
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .fillMaxWidth(elapsed).background(UP_NEXT_BLUE)
                )
            }
        }
    }
}

/**
 * "Still watching {show}?" with Keep watching and I'm done. [focus] 0 is Keep
 * watching. [progress] runs 0..1 over the four minutes before the card's.
 */
@Composable
internal fun BoxScope.StillWatchingPrompt(showTitle: String?, focus: Int, progress: Float) {
    Box(Modifier.matchParentSize().bottomScrim(0.67f, 0.92f))
    Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 31.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = showTitle?.takeIf { it.isNotBlank() }?.let { "Still watching $it?" } ?: "Still watching?",
            color = Color.White,
            fontSize = 15.sp,
            fontFamily = AppTypography.interBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Press any button to keep going. Otherwise playback stops after this episode.",
            color = UP_NEXT_DIM,
            fontSize = 8.sp,
            fontFamily = AppTypography.interSemiBold,
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            UpNextButton("Keep watching", focused = focus == 0, width = 95.dp)
            UpNextButton("I’m done", focused = focus == 1, width = 95.dp)
        }
        Spacer(Modifier.height(11.dp))
        Box(
            Modifier.width(150.dp).height(1.5.dp).clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(UP_NEXT_BLUE))
        }
    }
}

/** The mock-up's pill: white with a blue ring when focused, frosted otherwise. */
@Composable
private fun UpNextButton(
    label: String,
    focused: Boolean,
    playIcon: Boolean = false,
    width: Dp? = null,
) {
    val text = if (focused) Color(0xFF0C0C10) else Color(0xFFEBEEF5)
    // The ring sits 1.5 dp outside the pill, so both states take the same room.
    Box(
        Modifier
            .border(1.dp, if (focused) UP_NEXT_BLUE else Color.Transparent, RoundedCornerShape(7.5.dp))
            .padding(1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (focused) Color.White else Color.White.copy(alpha = 0.15f))
                .padding(horizontal = if (playIcon) 13.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (playIcon) {
                Canvas(Modifier.size(5.5.dp, 7.dp)) {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close()
                        },
                        color = text,
                    )
                }
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = text, fontSize = 9.sp, fontFamily = AppTypography.interBold)
        }
    }
}
