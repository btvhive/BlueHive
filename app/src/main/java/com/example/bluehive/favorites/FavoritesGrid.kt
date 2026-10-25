package com.example.bluehive.favorites

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.exjunk.lib.thanos.vanish.VanishContainer
import com.exjunk.lib.thanos.vanish.VanishGLSurfaceView
import kotlinx.coroutines.CoroutineScope

/**
 * The grid: the vanish layer the removed cards disintegrate on, and the cards.
 * Back in the grid goes to the Filter dropdown, or in remove mode opens
 * "Exit Remove Mode".
 *
 * Moved here from FavoritesScreen in Stage F3 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); the code is the code
 * it replaces.
 */
@Composable
internal fun BoxScope.FavoritesGrid(
    data: FavoritesData,
    filter: FavoritesFilter,
    focus: FavoritesGridFocus,
    glSurfaceView: MutableState<VanishGLSurfaceView?>,
    filterDropdownFocus: FocusRequester,
    coroutineScope: CoroutineScope,
    gridFocusRequesters: List<FocusRequester>,
    backgroundPainter: Painter,
    focusedPainter: Painter,
    shadowPainter: Painter,
    navigateToDetails: (FavoriteEntry) -> Unit,
) {
    // ── Media card grid ───────────────────────────────────────────────
    // VanishContainer provides the OpenGL surface layer that the
    // particle disintegration effect renders onto when a card is removed.
    VanishContainer(
        modifier = Modifier.fillMaxSize(),
        onGLSurfaceViewCreated = {
            glSurfaceView.value = it
            it.setAnimationConfig(
                duration = 2500f,
                particleSize = 2f
            )
        }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = gridOffsetY)
                .width(860.dp)
                .height(gridHeight)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        if (data.removeMode) {
                            data.showDeleteConfirm = true
                        } else {
                            filterDropdownFocus.requestFocus()
                        }
                        true
                    } else false
                },
        ) {
            LazyVerticalGrid(
                columns           = GridCells.Fixed(CARDS_PER_ROW),
                state             = focus.gridState,
                userScrollEnabled = false,
                modifier          = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (filter.isAnyDropdownExpanded) 0.08f else 1f }
                    .focusGroup()
                    .onFocusChanged { fs -> focus.hasGridFocus = fs.hasFocus },
                contentPadding    = PaddingValues(bottom = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(cardHorizontalSpacing),
                verticalArrangement   = Arrangement.spacedBy(rowSpacing),
            ) {

            itemsIndexed(
                items = filter.filtered,
                key   = { _, entry -> entry.favoriteId },
            ) { index, entry ->
                FavoriteCard(
                    index               = index,
                    entry               = entry,
                    data                = data,
                    filter              = filter,
                    focus               = focus,
                    coroutineScope      = coroutineScope,
                    gridFocusRequesters = gridFocusRequesters,
                    glSurfaceView       = glSurfaceView,
                    backgroundPainter   = backgroundPainter,
                    focusedPainter      = focusedPainter,
                    shadowPainter       = shadowPainter,
                    filterDropdownFocus = filterDropdownFocus,
                    navigateToDetails   = navigateToDetails,
                )
            }
        }
        }   // closes inner Box
    }       // closes VanishContainer
}
