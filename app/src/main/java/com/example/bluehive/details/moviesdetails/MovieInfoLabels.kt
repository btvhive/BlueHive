package com.example.bluehive.details.moviesdetails

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The movie screen's metadata labels: revenue and budget (only when known),
 * release date and language. Moved here word for word in Stage 2 of the
 * details redesign; the hand-tuned spacing inside the strings differs from the
 * TV screen's on purpose. A BoxScope extension so each label keeps its
 * absolute position.
 */
@SuppressLint("DefaultLocale")
@Composable
internal fun BoxScope.MovieInfoLabels(
    displayRevenue: Long,
    displayBudget: Long,
    releaseDate: String?,
    originalLanguage: String?,
    dongleRegular: FontFamily,
) {
    // ========================================================================================================================================= > // Revenue
    if (displayRevenue > 0) {
        Text(
            text = "Revenue:          $${"%,d".format(displayRevenue)}.00",
            color = Color(0xFF8B8B8B),
            fontSize = 17.sp,
            fontFamily = dongleRegular,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 258.25.dp, top = 34.dp)
        )
    }
    // ========================================================================================================================================= >


    // ========================================================================================================================================= > // Budget
    if (displayBudget > 0) {
        Text(
            text = "Budget:            $${"%,d".format(displayBudget)}.00",
            color = Color(0xFF8B8B8B),
            fontSize = 17.sp,
            fontFamily = dongleRegular,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 258.25.dp, top = 48.dp)
        )
    }
    // ========================================================================================================================================= >



    // ========================================================================================================================================= > // release_date
    Text(
        text = if (!releaseDate.isNullOrBlank() && releaseDate != "N/A") {
            val dateParts = releaseDate.split("-")
            if (dateParts.size == 3) {
                "Release Date:      ${dateParts[1]}/${dateParts[2]}/${dateParts[0]}"  // Format as MM/DD/YYYY
            } else {
                releaseDate
            }
        } else {
            "Release Date:      N/A"
        },
        color = Color(0xFF8B8B8B),
        fontSize = 16.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 258.25.dp, top = 63.dp)  // Adjust position as needed
    )
    // ========================================================================================================================================= >


    // ========================================================================================================================================= > // original language
    // ✅ Always show language, fall back to N/A
    Text(
        text = "Language:         " + (
                if (!originalLanguage.isNullOrBlank() && originalLanguage != "N/A")
                    originalLanguage
                else
                    "N/A"
                ),
        color = Color(0xFF8B8B8B),
        fontSize = 17.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 258.25.dp, top = 75.dp)
    )

    // ========================================================================================================================================= >
}
