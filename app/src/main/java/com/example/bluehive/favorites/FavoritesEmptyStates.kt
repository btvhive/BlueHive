package com.example.bluehive.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluehive.utilities.AppTypography

/**
 * No favourites at all: "No favorite content added yet".
 *
 * Moved here from FavoritesScreen in Stage F3 of the Favorites and Home
 * redesign (docs/favorites-home-redesign-architecture.md); the code is the code
 * it replaces.
 */
@Composable
internal fun BoxScope.NoFavoritesPanel() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0xFF121213), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF3A3737), RoundedCornerShape(12.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp)
                .height(130.dp)
                .width(480.dp),
        ) {
            Text(
                text       = "No favorite content added yet",
                color      = Color.White,
                fontFamily = AppTypography.interBold,
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
            Text(
                text       = "Open any movie or TV show and press the favorite button to see it here.",
                color      = Color(0xFFAAAAAA),
                fontFamily = AppTypography.interSemiBold,
                fontWeight = FontWeight.Normal,
                fontSize   = 13.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .offset(y = 8.dp),
            )
            Text(
                text       = "To leave just press the back button",
                color      = Color(0xFFA01D1D),
                fontFamily = AppTypography.interBold,
                fontWeight = FontWeight.Normal,
                fontSize   = 15.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .fillMaxWidth()
                    .offset(y = 36.dp, x = 2.dp),
            )
        }
    }
}

/** Favourites, but none for this type and genre: "No content found for this filter", over the grid. */
@Composable
internal fun BoxScope.NoMatchesPanel() {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = gridOffsetY)
            .width(860.dp)
            .height(gridHeight)
    ) {

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color(0xFF121213), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3A3737), RoundedCornerShape(12.dp))
                    .padding(horizontal = 40.dp, vertical = 32.dp)
                    .height(130.dp)
                    .width(520.dp),
            ) {
                Text(
                    text       = "No content found for this filter",
                    color      = Color.White,
                    fontFamily = AppTypography.interBold,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                Text(
                    text       = "There are no favorites matching the current genre and media type selection.",
                    color      = Color(0xFFAAAAAA),
                    fontFamily = AppTypography.interSemiBold,
                    fontWeight = FontWeight.Normal,
                    fontSize   = 13.sp,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .offset(y = 8.dp),
                )

                Text(
                    text       = "Try changing the filter or switching between Movies and TV Shows",
                    color      = Color(0xFFA01D1D),
                    fontFamily = AppTypography.interBold,
                    fontWeight = FontWeight.Normal,
                    fontSize   = 15.sp,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .offset(y = 36.dp, x = 2.dp),
                )
            }
        }
    }
}
