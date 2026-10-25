package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.example.bluehive.utilities.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.focus.focusRequester
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CoroutineScope

// Reliable focus for a season button. A season button lives in a LazyColumn,
// so its FocusRequester only attaches once the row is composed AND placed.
// Presence in visibleItemsInfo == laid out == requester attached. We scroll
// it in, wait for that, then requestFocus — with retries so a busy main
// thread (entering from Continue Watching) can't make Back silently no-op.
internal suspend fun TvFocusMap.focusSeasonReliably(targetIndex: Int) {
    if (targetIndex < 0 || targetIndex >= seasons.size) return
    seasonList.scrollToItem(targetIndex)
    var attempts = 0
    while (attempts < 40) {
        awaitFrame()
        val laidOut = seasonList.layoutInfo
            .visibleItemsInfo.any { it.index == targetIndex }
        if (laidOut) {
            seasons.getOrNull(targetIndex)?.requestFocus()
            return
        }
        attempts++
    }
    // Last-ditch: try anyway so focus is never stranded on the episode tile.
    seasons.getOrNull(targetIndex)?.requestFocus()
}

@Composable
fun SeasonButton(
    label: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    ModularButton(
        textConfig = ModularButtonTextConfig(
            text = label,
            fontSize = 16f
        ),
        isToggleable = true,
        fontFamily = fontFamily,
        focusRequester = focusRequester,
        modifier = modifier,
        dimensions = ModularButtonDimensions(
            mainWidth = 117.5.dp,
            mainHeight = 30.dp,
            mainYOffset = 0.7.dp,
            secondWidth = 117.5.dp,
            secondHeight = 22.dp,
            secondYOffset = 13.5.dp,
            shadowHeight = 12.dp,
            shadowOffset = (-3).dp,
            glowWidth = 135.6.dp,
            glowHeight = 51.dp,
            mainCornerRadius = 7f,
            secondCornerRadius = 8f,
        ),
        // Shared — see SeasonButtonColors in utilities/ModularButton.kt. It is
        // derived from StreamSourceButtonColors, so these chips and the watch
        // buttons brighten identically on focus.
        colors = SeasonButtonColors,
        glowConfig = ModularButtonGlowConfig(
            enabled = true,
            defaultRes = R.drawable.button_focus_wide_glow,
            toggledRes = R.drawable.button_focus_wide_nonglow,
            offsetX = (-9.25).dp,  // lesser the number the farther it moves left
            offsetY = (-2.5).dp,
            cornerRadius = 10.dp
        ),
        animationConfig = ModularButtonAnimationConfig(
            pressOffset = 2.5.dp,
            textOffsetDefault = 1.dp,
            textOffsetPressed = 2.4.dp,
            durationMillis = 110,
            bounceBackDelayMillis = 80
        ),
        externalToggled = isSelected,
        onClick = {
            BlueHiveApplication.playClickSound()
            onClick()
        }
    )
}

/**
 * The season column: Season 1 through the newest season with an aired
 * episode ([listedSeasons]), each a SeasonButton. Down and Up play the
 * navigation sound (Down is swallowed on the last season), Right goes to the
 * last-focused episode, and a press loads that season.
 *
 * Moved here from the screen in Stage 8 of the details redesign; drawn into
 * the play area's Box at the same offsets as before.
 */
@Composable
internal fun BoxScope.SeasonColumn(
    listedSeasons: Int?,
    numberOfSeasons: Int,
    episodesState: EpisodesState,
    focusMap: TvFocusMap,
    passionFont: FontFamily,
    coroutineScope: CoroutineScope,
) {
    // 🔴 Inline Seasons LazyRow (no separate TVSeasonsRow composable)
    // Lists Season 1..listedSeasons, so a season with nothing
    // aired yet never renders. Waits for that answer rather than
    // listing every season and then dropping one.
    val listed = listedSeasons ?: 0
    if (numberOfSeasons > 0 && listed > 0) {
        val seasons = remember(listed) { (1..listed).toList() }
        val listState = focusMap.seasonList

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 28.5.dp, y = 313.25.dp)   // tweak these to line up visually
                .width(180.25.dp)
                .height(201.5.dp)
                //.background(Color.Red)            // debug; remove later
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement  = Arrangement.spacedBy((-6).dp),   // ① between items
                contentPadding = PaddingValues(horizontal = 11.5.dp, vertical = 19.5.dp)      // ② padding at edges and top margin
            ) {

                itemsIndexed(seasons) { seasonIndex, seasonNumber ->
                    SeasonButton(
                        label = "Season $seasonNumber",
                        isSelected = (seasonNumber == episodesState.selectedSeason),
                        fontFamily = passionFont,
                        focusRequester = focusMap.seasons[seasonIndex],
                                modifier = Modifier.onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (seasonIndex == seasons.lastIndex) {
                                            // Last season — nothing below it. Swallow the
                                            // key so holding DOWN at the bottom stops
                                            // repeating the nav sound (and doesn't move).
                                            true
                                        } else {
                                            BlueHiveApplication.playTitleCardNavigation()
                                            false
                                        }
                                    }
                                    Key.DirectionUp -> {
                                        BlueHiveApplication.playTitleCardNavigation()
                                        false
                                    }
                                    Key.DirectionRight -> {
                                        if (episodesState.episodes.isNotEmpty() && episodesState.focusRequesters.isNotEmpty()) {
                                            BlueHiveApplication.playHoverSound()

                                            val targetIndex = episodesState.lastFocusedEpisodeIndex
                                                .coerceIn(0, episodesState.focusRequesters.lastIndex)

                                            coroutineScope.launch {
                                                episodesState.rowState.scrollToItem(targetIndex)
                                                episodesState.focusRequesters
                                                    .getOrNull(targetIndex)
                                                    ?.requestFocus()
                                                    ?: episodesState.focusRequesters.first().requestFocus()
                                            }

                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                        onClick = {
                            if (episodesState.selectedSeason != seasonNumber) {
                                episodesState.selectedSeason = seasonNumber
                                episodesState.loadSeasonEpisodes(seasonNumber)
                            }
                        },
                    )
                }

            }
        }
    }
}
