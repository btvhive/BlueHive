package com.example.bluehive.details.tvshowdetails

import androidx.compose.ui.draw.scale
import com.example.bluehive.utilities.WatchedCornerFold
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import com.example.bluehive.utilities.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.example.bluehive.R
import com.example.bluehive.models.Episode

/**
 * Measures [content] at its natural size, reports ZERO height to the parent, and
 * places it directly ABOVE where it would otherwise sit.
 *
 * Exists for the episode air date. That Text sits above the still inside the same
 * Column, so its line height became real layout space and pushed every thumbnail
 * down — the tiles visibly sat lower than before the date was added on
 * 2026-08-16.
 *
 * TWO EARLIER ATTEMPTS AND WHY THEY WERE WRONG, so nobody repeats them:
 *
 *   Leaving it alone — the tiles stay pushed down by however tall the font
 *   happens to render. Nothing in the code says how much, so it cannot be
 *   compensated anywhere else with confidence.
 *
 *   Pinning the Text to a fixed height and lifting the Column by the same
 *   constant — this DELETED THE DATE. dongleRegular's line box at 11.sp is taller
 *   than the 14.dp that was guessed, so the glyphs were clipped away entirely and
 *   the strip rendered blank. Any hardcoded height is the same trap with a
 *   different number: it silently clips the moment a font, size, or fontScale
 *   changes.
 *
 * This measures first and never constrains, so the text renders at whatever size
 * it actually needs, and the parent is told it took none of the column. The
 * result is exact rather than tuned — the still returns to precisely the Y it had
 * before the date existed, for any font metrics, with nothing to keep in sync.
 */
@Composable
internal fun ZeroHeightAbove(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        // Natural height: pass the incoming width through, but no height bound.
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        // Report height 0 — the Column advances not at all for this child.
        layout(width, 0) {
            // Negative Y puts it in the strip above the tile's top edge.
            placeables.forEach { it.place(0, -it.height) }
        }
    }
}

@Composable
fun EpisodeBadge(
    current: Int,
    total: Int,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeText = remember(current, total) { "$current/$total" }

    // ✅ Same "exposure" scale idea used for stills
    val darkScale = 0.40f

    fun darken(color: Color): Color {
        return color.copy(
            red = color.red * darkScale,
            green = color.green * darkScale,
            blue = color.blue * darkScale
        )
    }

    val baseBg = Color(0xFF262629)
    val baseText = Color.White

    val bgColor = if (isFocused) baseBg else darken(baseBg)
    val textColor = if (isFocused) baseText else darken(baseText)

    Box(
        modifier = modifier
            // ⬇️ slightly wider than RatingBadge, same height
            .height(16.5.dp)
            .width(25.dp) // you can tweak if you want more breathing room
            .clip(AppShapes.ratingBadge)
            .border(width = 1.8.dp, color = Color(0xB0000000), shape = AppShapes.ratingBadge)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badgeText,
            fontSize = 7.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}





// ✅ "Exposure" style darkening using a color matrix (no alpha usage). Made
// once, here: the row used to remember these per composition (P4).
private val darkenFilter =
    ColorFilter.colorMatrix(
        ColorMatrix().apply {
            // Lower RGB scale = darker image
            setToScale(0.40f, 0.40f, 0.40f, 1f)
        }
    )

// The border diapers whenever the still path is focused
private val episodeShape = RoundedCornerShape(7.5.dp)

private val baseStillModifier =
    Modifier
        .width(174.27.dp)
        .height(97.5.dp)
        .clip(episodeShape)

/**
 * One episode tile: the air date above the still, the still with its focus
 * glow, badge and watched fold, the unaired scrim, and the title under it.
 * (The overview and the sources moved to the episode overlay, 2026-10-27 --
 * see EpisodeSourceOverlay.)
 *
 * Takes plain values (architecture P3), so a tile whose values did not change
 * is skipped when the row recomposes. What the row decides stays with the row
 * and comes in as callbacks: [onFocused] when the tile gains focus, the
 * D-pad rules as [onKeyEvent].
 *
 * Moved here from the screen in Stage 8 of the details redesign.
 */
@Composable
internal fun EpisodeCard(
    episode: Episode,
    index: Int,
    totalEpisodes: Int,
    isWatched: Boolean,
    isUnaired: Boolean,
    isEpisodeSelectOverlay: Boolean,
    backdropUrl: String?,
    rowState: LazyListState,
    focusRequester: FocusRequester,
    passionFont: FontFamily,
    dongleRegular: FontFamily,
    onFocused: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean,
) {
    var isEpisodeFocused by remember { mutableStateOf(false) }

    // Try the episode still first, fallback to show's backdropUrl
    val stillUrl = episode.stillImageUrl ?: backdropUrl

    val titleText = episode.title

    // 👇 true only when the whole item is within the viewport
    val isFullyVisible by remember(rowState, index) {
        derivedStateOf {
            val layoutInfo = rowState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf false

            val itemStart = itemInfo.offset
            val itemEnd   = itemInfo.offset + itemInfo.size

            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd   = layoutInfo.viewportEndOffset

            itemStart >= viewportStart && itemEnd <= viewportEnd
        }
    }

    // The air date sits at the card's right end, so it shows whenever that end
    // is clear of the caps: the right half out from under the season cap, and
    // the right edge short of the right cap. (isFullyVisible above is the
    // row's own edges, which run under both caps.)
    val density = LocalDensity.current
    val leftCapCover = with(density) { (SEASON_CAP_X + SEASON_CAP_WIDTH - EPISODE_ROW_X).toPx() }
    val rightCapCover = with(density) { (EPISODE_ROW_END - RIGHT_CAP_X).toPx() }
    val dateEndPadding = with(density) { 4.dp.toPx() }
    val isDateClear by remember(rowState, index, leftCapCover, rightCapCover) {
        derivedStateOf {
            val layoutInfo = rowState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf false

            val rightHalfStart = itemInfo.offset + itemInfo.size / 2f
            val dateEnd = itemInfo.offset + itemInfo.size - dateEndPadding

            rightHalfStart >= layoutInfo.viewportStartOffset + leftCapCover &&
                dateEnd <= layoutInfo.viewportEndOffset - rightCapCover
        }
    }

    // ✅ This outer column is the "focus target"
    //    It gives the title real space UNDER the still image.
    Column(
        modifier = Modifier
            // keep item layout sized to the still image (prevents spacing weirdness)
            .width(174.27.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                // hasFocus stays true when any child (like provider buttons) is focused
                isEpisodeFocused = state.hasFocus
                if (state.hasFocus) onFocused()
            }

            .onKeyEvent(onKeyEvent)
            .focusable()
    ) {

        // ✅ Air date — above the still, right-aligned. Same font and
        // focus colours as the episode title below it, two points smaller.
        //
        // Rendered UNCONDITIONALLY, unlike that title. The title sits
        // UNDER the image, so it can come and go without disturbing
        // anything; this sits ABOVE it, where appearing and disappearing
        // would shove every thumbnail up and down as the row scrolls.
        // So isDateClear only HIDES it (alpha 0, read in the layer so
        // scrolling redraws rather than recomposes): a card whose right end
        // is under a cap shows no half date. A blank string still occupies its line,
        // which keeps unaired episodes (null air_date) exactly the same
        // height as aired ones.
        // Measured at its NATURAL size, then reported as zero height and
        // placed above the baseline — see ZeroHeightAbove.
        ZeroHeightAbove {
            Text(
                text = remember(episode.airDate) { formatEpisodeAirDate(episode.airDate) },
                fontFamily = dongleRegular,
                fontSize = 11.sp,          // episode title is 13.sp
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (!isEpisodeFocused) Color(0xFF737373) else Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp)
                    .graphicsLayer { alpha = if (isDateClear) 1f else 0f }
            )
        }

        // ✅ Image/BADGE/GLOW stack stays inside this Box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
        ) {

            // ✅ Glow behind image when focused
            if (isEpisodeFocused && !isEpisodeSelectOverlay) {
                Image(
                    painter = painterResource(id = R.drawable.episode_focus_glow),
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = (-0.25).dp, y = (-0.3).dp) // offset the position of the glow
                        .wrapContentSize(unbounded = true) // allow the glow to exceed the layout bounds
                        // force exact glow size even if parent is smaller
                        .requiredSize(width = 206.3.dp, height = 129.dp),  // length and width of the focus glow
                    contentScale = ContentScale.FillBounds
                )
            }

            // ✅ Still image centered over glow
            if (!stillUrl.isNullOrBlank()) {

                val stillModifier = baseStillModifier.then(
                    if (!isEpisodeFocused || isEpisodeSelectOverlay) {
                        Modifier.border(
                            width = 1.1.dp,
                            color = Color(0xFF505050), // border color
                            shape = episodeShape
                        )
                    } else {
                        Modifier
                    }
                )

                AsyncImage(
                    model = stillUrl,
                    contentDescription = episode.title,
                    modifier = stillModifier,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (!isEpisodeFocused) darkenFilter else null
                )

                // ✅ Episode badge – bottom-left overlay, on top of still
                EpisodeBadge(
                    current = episode.episodeNumber,
                    total = totalEpisodes,
                    isFocused = isEpisodeFocused,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 76.5.dp)
                )

                // ✅ Watched marker — corner fold + tick, TOP-RIGHT so it
                // never collides with the episode-number badge. Drawn in a
                // single Canvas (no Material-Icons dependency) and clipped
                // to the card shape so it follows the rounded corner.
                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(episodeShape)
                    ) {
                        WatchedCornerFold(
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            }


            // ✅ UNAIRED — dark scrim over the still, saying why.
            //
            // Over the still rather than instead of it: the card still
            // has to read as the episode it is, so the artwork stays
            // visible underneath. Drawn after the badge and the watched
            // fold so it covers both.
            if (isUnaired) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(episodeShape)
                        .background(Color(0xD9101014))
                        // Border re-added ON TOP, or the scrim squares the corners.
                        .border(
                            width = 1.1.dp,
                            color = Color(0xFF505050),
                            shape = episodeShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EPISODE NOT AIRED",
                        fontFamily = passionFont,
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        color = Color(0xFFD6D6DB),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }



        // ✅ Episode title under each respective image
        // Darken state whenever the episode isn't focused. The bounce is read
        // while placing (offsetWithY), so it moves the title without
        // recomposing the card on every frame (v515).
        val titleYOffset = animateDpAsState(
            targetValue = if (isEpisodeFocused) 1.5.dp else (-1.5).dp,
            label = "episodeTitleYOffset"
        )

        if (!isEpisodeSelectOverlay && isFullyVisible) {
            Text(
                text = titleText,
                fontFamily = dongleRegular,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (!isEpisodeFocused) Color(0xFF737373) else Color.White,
                modifier = Modifier
                    .offsetWithY(4.dp) { titleYOffset.value }
            )
        }

    }
}

// ════════════════════════════════════════════════════════════════════════════
//  @Preview — watched corner fold
//
//  Renders the REAL WatchedCornerFold composable (not a copy) on a mock episode
//  still, so tweaking WATCHED_FOLD_* above updates this preview immediately.
//  Open this file in Android Studio and hit "Split" / "Design" to see it.
// ════════════════════════════════════════════════════════════════════════════
@Preview(name = "Watched fold — on card", widthDp = 420, heightDp = 190, showBackground = true, backgroundColor = 0xFF0A0A12)
@Composable
private fun WatchedCornerFoldPreview() {
    val episodeShape = RoundedCornerShape(7.5.dp)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Watched — unfocused card (the hard case: marker must read on a dim still)
        Box(
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
                .clip(episodeShape)
                .background(Color(0xFF16161E))
                .border(1.1.dp, Color(0xFF505050), episodeShape)
        ) {
            WatchedCornerFold(modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text = "6/10",
                color = Color(0xFFD6D6D6),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 5.dp)
            )
        }

        // Watched — focused card (white border + lighter surface)
        Box(
            modifier = Modifier
                .width(174.27.dp)
                .height(97.5.dp)
                .clip(episodeShape)
                .background(Color(0xFF2B2B36))
                .border(1.4.dp, Color.White, episodeShape)
        ) {
            WatchedCornerFold(modifier = Modifier.align(Alignment.TopEnd))
            Text(
                text = "7/10",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 5.dp)
            )
        }
    }
}

@Preview(name = "Watched fold — zoomed", widthDp = 150, heightDp = 150, showBackground = true, backgroundColor = 0xFF16161E)
@Composable
private fun WatchedCornerFoldZoomPreview() {
    // Isolated + enlarged so tick geometry is easy to judge while tuning
    // WATCHED_FOLD_TICK_SIZE / WATCHED_FOLD_TICK_STROKE.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16161E)),
        contentAlignment = Alignment.Center
    ) {
        WatchedCornerFold(modifier = Modifier.scale(3f))
    }
}
