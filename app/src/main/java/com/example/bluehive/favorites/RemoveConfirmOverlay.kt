package com.example.bluehive.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.ModularButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Exit Remove Mode": how many titles were removed, Accept Changes and Undo
 * Changes. It swallows Back.
 *
 * Moved here from FavoritesScreen in Stage F3 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); the code is the code
 * it replaces.
 */
@Composable
internal fun BoxScope.RemoveConfirmOverlay(
    data: FavoritesData,
    filter: FavoritesFilter,
    focus: FavoritesGridFocus,
    profileId: Int,
    coroutineScope: CoroutineScope,
    gridFocusRequesters: List<FocusRequester>,
    acceptChangesFocus: FocusRequester,
    undoChangesFocus: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .zIndex(2f)
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f)
            .onPreviewKeyEvent { event ->
                // Trap all back presses while overlay is open
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    true
                } else false
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0xFF121213), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF3A3737), RoundedCornerShape(12.dp))
                .padding(horizontal = 40.dp, vertical = 28.dp)
                .width(520.dp),
        ) {
            Text(
                text       = "Exit Remove Mode",
                color      = Color.White,
                fontFamily = AppTypography.interBold,
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            )
            Text(
                text       = "${data.removed.size} item${if (data.removed.size == 1) "" else "s"} removed. Do you wish to keep these changes?",
                color      = Color(0xFFAAAAAA),
                fontFamily = AppTypography.interSemiBold,
                fontWeight = FontWeight.Normal,
                fontSize   = 13.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )

            // ── Buttons row ───────────────────────────────────────
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(
                    overlayButtonGap, Alignment.CenterHorizontally
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Accept Changes — blue 3D button
                ModularButton(
                    textConfig      = acceptChangesText,
                    isToggleable    = false,
                    isFocusable     = true,
                    fontFamily      = AppTypography.interBold,
                    focusRequester  = acceptChangesFocus,
                    onClick = {
                        BlueHiveApplication.playClickSound()
                        coroutineScope.launch {
                            data.commitRemovals(profileId, "Remove commit failed")
                        }
                        data.removeMode = false
                        coroutineScope.launch {
                            kotlinx.coroutines.android.awaitFrame()
                            kotlinx.coroutines.android.awaitFrame()
                            if (filter.filtered.isNotEmpty()) {
                                val target = focus.focusedGridIndex.coerceIn(0, filter.filtered.lastIndex)
                                focus.focusedGridIndex = target
                                focus.gridState.scrollToItem(target)
                                kotlinx.coroutines.android.awaitFrame()
                                gridFocusRequesters.getOrNull(target)?.requestFocus()
                            }
                            data.showDeleteConfirm = false
                        }
                    },
                    modifier = Modifier
                        .onFocusChanged {
                            if (it.isFocused && focus.hoverArmed) BlueHiveApplication.playHoverSound()
                        }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionRight -> {
                                        undoChangesFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown -> true
                                    else -> false
                                }
                            } else false
                        },
                    dimensions = overlayButtonDimensions,
                    colors = acceptChangesColors,
                    glowConfig      = overlayButtonGlow,
                    animationConfig = buttonAnimation,
                )

                // Undo Changes — red 3D button
                ModularButton(
                    textConfig      = undoChangesText,
                    isToggleable    = false,
                    isFocusable     = true,
                    fontFamily      = AppTypography.interBold,
                    focusRequester  = undoChangesFocus,
                    onClick = {
                        BlueHiveApplication.playClickSound()
                        data.undoRemovals()
                        data.removeMode = false
                        coroutineScope.launch {
                            kotlinx.coroutines.android.awaitFrame()
                            kotlinx.coroutines.android.awaitFrame()
                            if (filter.filtered.isNotEmpty()) {
                                val target = focus.focusedGridIndex.coerceIn(0, filter.filtered.lastIndex)
                                focus.focusedGridIndex = target
                                focus.gridState.scrollToItem(target)
                                kotlinx.coroutines.android.awaitFrame()
                                // The undone cards made the list longer, so the
                                // grid has new requesters by now (§9 #7).
                                focus.requestCardFocus(target)
                            }
                            data.showDeleteConfirm = false
                        }
                    },
                    modifier = Modifier
                        .onFocusChanged {
                            if (it.isFocused && focus.hoverArmed) BlueHiveApplication.playHoverSound()
                        }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        acceptChangesFocus.requestFocus()
                                        true
                                    }
                                    Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> true
                                    else -> false
                                }
                            } else false
                        },
                    dimensions = overlayButtonDimensions,
                    colors = undoChangesColors,
                    glowConfig      = overlayButtonGlow,
                    animationConfig = buttonAnimation,
                )
            }
        }
    }
}
