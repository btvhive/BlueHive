package com.example.bluehive.favorites

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.bluehive.BlueHiveApplication
import com.example.bluehive.sidebarComponents.HistoryDropdown
import com.example.bluehive.utilities.AppTypography
import com.example.bluehive.utilities.ModularButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The control bar: the header, the Filter and Sort dropdowns and Remove Media.
 *
 * Moved here from FavoritesScreen in Stage F3 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); the code is the code
 * it replaces.
 */
@Composable
internal fun BoxScope.FavoritesControlBar(
    data: FavoritesData,
    filter: FavoritesFilter,
    focus: FavoritesGridFocus,
    profileId: Int,
    coroutineScope: CoroutineScope,
    gridFocusRequesters: List<FocusRequester>,
    filterDropdownFocus: FocusRequester,
    sortDropdownFocus: FocusRequester,
    deleteButtonFocus: FocusRequester,
) {
    // ── Header — switches to remove mode instruction when active ──────
    Text(
        text       = if (data.removeMode) "Click on any media you want to remove" else HEADER_TEXT,
        fontSize   = HEADER_FONT_SIZE.sp,
        fontFamily = AppTypography.lalezarRegular,
        color      = Color.White.copy(alpha = HEADER_OPACITY),
        modifier   = Modifier
            .align(Alignment.TopStart)
            .offset(x = headerOffsetX, y = headerOffsetY),
    )

    // ── Dropdowns ─────────────────────────────────────────────────────
    HistoryDropdown(
        label                    = "Filter",
        options                  = genreOptions,
        selectedOption           = filter.genre,
        onOptionSelected         = { filter.genre = it },
        buttonWidth              = 160.dp,
        dropdownMaxHeight        = 237.dp,
        externalFocusRequester   = filterDropdownFocus,
        suppressInitialFocusSound = true,
        onExpandedChanged        = { filter.isAnyDropdownExpanded = it },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = dropdownOffsetX, y = dropdownOffsetY)
            .zIndex(1f),
    )

    HistoryDropdown(
        label                  = "Sort",
        options                = sortOptions,
        selectedOption         = filter.sort,
        onOptionSelected       = { filter.sort = it },
        buttonWidth            = 140.dp,
        externalFocusRequester = sortDropdownFocus,
        onExpandedChanged      = { filter.isAnyDropdownExpanded = it },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = dropdownOffsetX + dropdownSpacing, y = dropdownOffsetY)
            .zIndex(1f),
    )

    // ── Remove Media button ───────────────────────────────────────────
    ModularButton(
        textConfig      = removeMediaText,
        isToggleable    = true,
        externalToggled = data.removeMode,
        isFocusable     = true,
        fontFamily      = AppTypography.interBold,
        focusRequester  = deleteButtonFocus,
        onClick = {
            data.removeMode = !data.removeMode
            if (data.removeMode) {
                // Entering remove mode — jump focus to first card
                coroutineScope.launch {
                    focus.focusedGridIndex = 0
                    focus.gridState.scrollToItem(0)
                    kotlinx.coroutines.android.awaitFrame()
                    gridFocusRequesters.getOrNull(0)?.requestFocus()
                }
            } else {
                // Manually toggling off — treat same as Accept, commit to backend
                coroutineScope.launch {
                    data.commitRemovals(profileId, "Delete failed")
                }
            }
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = dropdownOffsetX + dropdownSpacing + 150.dp, y = filterButtonOffsetY)
            .zIndex(1f)
            .onFocusChanged {
                if (it.isFocused && focus.hoverArmed) BlueHiveApplication.playHoverSound()
            },
        dimensions = removeMediaDimensions,
        colors = removeMediaColors,
        glowConfig = buttonGlow,
        animationConfig = buttonAnimation,
    )
}

/** The type tabs: All Media, Movies and TV Shows. Moved here with FavoritesControlBar. */
@Composable
internal fun BoxScope.FavoritesTypeTabs(
    filter: FavoritesFilter,
    focus: FavoritesGridFocus,
    allMediaFocus: FocusRequester,
    moviesFocus: FocusRequester,
    tvSeriesFocus: FocusRequester,
) {
    // ── All Media button ──────────────────────────────────────────────
    ModularButton(
        textConfig      = allMediaText,
        isToggleable    = true,
        externalToggled = filter.typeTab == "all",
        isFocusable     = filter.typeTab != "all",
        fontFamily      = AppTypography.interBold,
        focusRequester  = allMediaFocus,
        onClick = {
            filter.typeTab = "all"
            focus.suppressNextHover = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                moviesFocus.requestFocus()
            }, 50)
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = filterButtonOffsetX, y = filterButtonOffsetY)
            .onFocusChanged {
                if (it.isFocused) {
                    if (!focus.suppressNextHover) BlueHiveApplication.playHoverSound()
                    else focus.suppressNextHover = false
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    BlueHiveApplication.playHoverSound()
                }
                false
            },
        dimensions      = filterTabDimensions,
        colors          = filterTabColors,
        glowConfig      = buttonGlow,
        animationConfig = buttonAnimation,
    )

    // ── Movies button ─────────────────────────────────────────────────
    ModularButton(
        textConfig      = moviesText,
        isToggleable    = true,
        externalToggled = filter.typeTab == "movies",
        isFocusable     = filter.typeTab != "movies",
        fontFamily      = AppTypography.interBold,
        focusRequester  = moviesFocus,
        onClick = {
            filter.typeTab = "movies"
            focus.suppressNextHover = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tvSeriesFocus.requestFocus()
            }, 50)
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = filterButtonOffsetX + filterButtonSpacing, y = filterButtonOffsetY)
            .onFocusChanged {
                if (it.isFocused) {
                    if (!focus.suppressNextHover) BlueHiveApplication.playHoverSound()
                    else focus.suppressNextHover = false
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    BlueHiveApplication.playHoverSound()
                }
                false
            },
        dimensions      = filterTabDimensions,
        colors          = filterTabColors,
        glowConfig      = buttonGlow,
        animationConfig = buttonAnimation,
    )

    // ── TV Shows button ───────────────────────────────────────────────
    ModularButton(
        textConfig      = tvShowsText,
        isToggleable    = true,
        externalToggled = filter.typeTab == "tv",
        isFocusable     = filter.typeTab != "tv",
        fontFamily      = AppTypography.interBold,
        focusRequester  = tvSeriesFocus,
        onClick = {
            filter.typeTab = "tv"
            focus.suppressNextHover = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                moviesFocus.requestFocus()
            }, 50)
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(
                x = filterButtonOffsetX + (filterButtonSpacing * 2),
                y = filterButtonOffsetY
            )
            .onFocusChanged {
                if (it.isFocused) {
                    if (!focus.suppressNextHover) BlueHiveApplication.playHoverSound()
                    else focus.suppressNextHover = false
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    BlueHiveApplication.playHoverSound()
                }
                false
            },
        dimensions      = filterTabDimensions,
        colors          = filterTabColors,
        glowConfig      = buttonGlow,
        animationConfig = buttonAnimation,
    )
}
