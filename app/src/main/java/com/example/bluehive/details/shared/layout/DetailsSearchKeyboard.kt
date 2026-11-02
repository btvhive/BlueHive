package com.example.bluehive.details.shared.layout

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.example.bluehive.searchBarComponent.HoneycombKeyboardPopup
import com.example.bluehive.searchBarComponent.openSearchScreen
import com.example.bluehive.utilities.overlayScrimColor

/**
 * Whether the Search button's keyboard is open. The scaffold opens it
 * (stopping the trailer first, as opening a recommendation does); the
 * keyboard closes it. A holder rather than a screen variable so neither
 * screen function carries the press and close lambdas (checkMethodSizes).
 */
@Stable
class DetailsSearchState {
    var isOpen by mutableStateOf(false)
        private set

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
    }
}

@Composable
fun rememberDetailsSearch(): DetailsSearchState = remember { DetailsSearchState() }

/**
 * The Search button's keyboard over a details page, as home and the media
 * catalog show it: a pop-up window, so the page keeps its state and focus.
 * The page behind blurs on API 31+ (the onn box -- the screen passes
 * detailsBackdropBlur to the scaffold); older boxes (Fire TV, no RenderEffect)
 * get the dark scrim home puts behind its sidebar, faded in. A search opens
 * the search page; BACK closes it.
 *
 * The movie page's since 2026-10-25; shared with the TV page 2026-10-27.
 */
@Composable
fun BoxScope.DetailsSearchKeyboard(search: DetailsSearchState, profileId: Int) {
    val context = LocalContext.current
    val show = search.isOpen
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val dim by animateFloatAsState(
            targetValue = if (show) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "searchDim",
        )
        if (dim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dim }
                    .background(overlayScrimColor())
            )
        }
    }
    // Always called: it builds itself hidden once the page settles, so a
    // press only has to show it (v627).
    run {
        HoneycombKeyboardPopup(
            visible = show,
            onSearch = { query ->
                search.close()
                openSearchScreen(context, profileId, query)
            },
            onDismiss = { search.close() },
        )
    }
}
