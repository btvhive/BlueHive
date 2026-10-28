package com.example.bluehive.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.api.isJapaneseAnimation
import com.example.bluehive.models.MediaItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * "Good copy" countdown on a movie poster (design E, approved 2026-10-24).
 *
 * A movie that came out in cinemas less than [WINDOW_DAYS] days ago usually only
 * exists as a cinema recording, so the card says roughly how long until a proper
 * copy is likely: a gold block with the number ("6 WKS", "10 DAYS", "1 DAY") and
 * "until / HD" beside it (was "until a / good copy"; the user found HD clearer). Movies only — TV, Japanese animation and anything on a streaming service never
 * get it — and it disappears by itself on day [WINDOW_DAYS], no data needed.
 *
 * Sizes come from the mockup (88 x 34 px badge at 92 %, 6 px under the poster's
 * top edge on a 94 px poster), converted with the poster's 90.25 dp / 94 px.
 */
object GoodCopyCountdown {

    const val WINDOW_DAYS = 60

    /** Under this many days left the block counts days instead of weeks. */
    private const val DAYS_FROM = 14

    data class Label(val number: String, val unit: String)

    /** The badge for [item] today, or null when it should not show. */
    fun labelFor(item: MediaItem, today: LocalDate = LocalDate.now()): Label? {
        if (item.mediaType != "movie") return null
        if (isJapaneseAnimation(item.genres?.joinToString(","), item.originalLanguage)) return null
        // Straight to Netflix/Prime/etc. = online in HD from day one, so there is
        // nothing to wait for. The server sets this from its US streaming list.
        if (item.onStreaming == true) return null
        val released = item.releaseDate
            ?.takeIf { it.length >= 10 }
            ?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
            ?: return null
        val age = ChronoUnit.DAYS.between(released, today)
        if (age < 0 || age >= WINDOW_DAYS) return null
        val left = (WINDOW_DAYS - age).toInt()
        return when {
            left >= DAYS_FROM -> Label((left / 7.0).roundToInt().toString(), "WKS")
            left == 1         -> Label("1", "DAY")
            else              -> Label(left.toString(), "DAYS")
        }
    }
}

// Mockup px -> dp: 92 % scale x (90.25 dp poster / 94 px poster).
private const val K = 0.92f * 90.25f / 94f
private fun px(v: Float) = (v * K).dp
private fun pxSp(v: Float): TextUnit = (v * K).sp

private val Gold = Color(0xFFE8B23A)
private val OnGold = Color(0xFF2A1D03)
private val Backing = Color(0xCC000000)
private val Soft = Color(0xFFDDDDDD)

/** Draw inside the poster box, aligned to its top centre. */
@Composable
fun GoodCopyBadge(label: GoodCopyCountdown.Label, modifier: Modifier = Modifier) {
    val font = AppTypography.interSemiBold
    val shape = RoundedCornerShape(px(6f))
    Row(
        modifier = modifier
            .padding(top = 6.dp)
            .size(width = px(88f), height = px(34f))
            .clip(shape)
            .background(Backing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(px(30f))
                .fillMaxHeight()
                .background(Gold),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label.number, color = OnGold, fontFamily = font, maxLines = 1,
                style = TextStyle(fontSize = pxSp(13f), lineHeight = pxSp(13f)))
            Text(label.unit, color = OnGold, fontFamily = font, maxLines = 1,
                style = TextStyle(fontSize = pxSp(6f), lineHeight = pxSp(8f)))
        }
        // "until" 10 px over "HD" 12 px, centred (the user's pick, design 3).
        Box(Modifier.weight(1f).padding(horizontal = px(5f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OneLine("until", Soft, size = 10f, lineHeight = 11f)
                OneLine("HD", Gold, size = 12f, lineHeight = 13f)
            }
        }
    }
}

@Composable
private fun OneLine(text: String, color: Color, size: Float, lineHeight: Float) {
    Text(
        text = text,
        color = color,
        fontFamily = AppTypography.interSemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        style = TextStyle(fontSize = pxSp(size), lineHeight = pxSp(lineHeight))
    )
}
