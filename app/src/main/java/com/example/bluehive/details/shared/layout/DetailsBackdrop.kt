package com.example.bluehive.details.shared.layout

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bluehive.R
import com.example.bluehive.utilities.AppShapes

/**
 * The bottom layers of both details screens, in the order they are drawn: the
 * page background, the content rating, the star and score, and the backdrop
 * picture -- hidden once the trailer has played, so the trailer player drawn
 * next shows in its place. The gradient goes on top of the trailer
 * ([DetailsGradient]), so it is a separate call.
 *
 * Moved here word for word from both screens in Stage 2 of the details
 * redesign (docs/details-redesign-architecture.md). A BoxScope extension so
 * everything lands in the screen's own Box at the same absolute positions as
 * before -- never wrap it in a layout of its own.
 */
@SuppressLint("DefaultLocale")
@Composable
fun BoxScope.DetailsBackdrop(
    contentRating: String?,
    voteAverage: Double,
    backdropUrl: String?,
    /** False once the trailer has played: the player shows instead. */
    showBackdrop: Boolean,
    /** The picture's accessibility label. The two screens word it differently. */
    backdropDescription: String,
    dongleRegular: FontFamily,
) {
    // Background image layer
    Image(
        painter = painterResource(id = R.drawable.backdrop_details),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )


    // ========================================================================================================================================= > // Content Rating
    if (!contentRating.isNullOrBlank() && contentRating != "N/A") {
        Text(
            text = if (contentRating.isNotBlank()) {
                "Rated: $contentRating"
            } else {
                "Rated: N/A"
            },  // Changed this line
            color = Color(0xFFFFFFFF),
            fontSize = 16.sp,
            fontFamily = dongleRegular,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 258.8.dp, top = 16.75.dp)
        )
    }
    // ========================================================================================================================================= >




    // ========================================================================================================================================= > // vote_average
    Image(
        painter = painterResource(id = R.drawable.star),
        contentDescription = "Rating star",
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 337.dp, top = 21.dp)   // ⬅️ star position (edit this independently)
            .size(11.dp)
    )

    Text(
        text = "${String.format("%.1f", voteAverage)}/10",  // Format to 1 decimal place
        color = Color(0xFFFFFFFF),
        fontSize = 16.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 352.dp, top = 16.75.dp)  // Adjust position as needed
    )
    // ========================================================================================================================================= >


    // ========================================================================================================================================= > // BACKDROP OVERLAY - Top-right corner with custom rounded bottom-left corner
    if (!backdropUrl.isNullOrBlank() && backdropUrl != "N/A" && showBackdrop) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = backdropDescription,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(519.dp)
                .height(292.dp)
                .clip(AppShapes.bottomLeftRoundedShape),
            contentScale = ContentScale.Crop,
            onLoading = { Log.d("BackdropDebug", "⏳ Backdrop loading...") },
            onSuccess = { Log.d("BackdropDebug", "✅ Backdrop loaded!") },
            onError = { e -> Log.e("BackdropDebug", "❌ Failed: ${e.result.throwable.message}") }
        )
    }
}

/** The gradient over the top-right backdrop, drawn above the trailer player. */
@Composable
fun BoxScope.DetailsGradient(backdropUrl: String?) {
    if (!backdropUrl.isNullOrBlank() && backdropUrl != "N/A") {
        // GRADIENT OVERLAY - Add this right after the backdrop
        Image(
            painter = painterResource(id = R.drawable.backdrop_details_gradient),
            contentDescription = "Gradient overlay",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(569.dp)
                .height(362.dp),
            contentScale = ContentScale.FillBounds
        )
    }
}
