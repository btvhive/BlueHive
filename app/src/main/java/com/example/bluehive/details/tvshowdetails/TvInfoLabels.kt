package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The TV screen's four metadata labels: seasons, status, release date and
 * language.
 *
 * They were the middle of TvDetailsInfoBlock, the first piece ever cut out of
 * TVShowsDetailsScreenContent (2026-09-21, after R8 made ART refuse that
 * class). Its logo and overview parts are shared since Stage 2 of the details
 * redesign (DetailsLogoOrTitle, DetailsOverview); these stay TV-only because
 * the movie screen shows revenue and budget instead, with its own spacing.
 * A BoxScope extension so each label keeps its absolute position.
 *
 * The four strings are worked out here, once per value. Until Stage 10 the
 * screen worked them out and passed them in.
 */
@Composable
internal fun BoxScope.TvInfoLabels(
    numberOfSeasons:  Int,
    status:           String?,
    releaseDate:      String?,
    originalLanguage: String?,
    dongleRegular:    FontFamily,
) {
    // 🔹 Precomputed display strings (recomputed only when inputs change)
    val seasonsLabel = remember(numberOfSeasons) {
        "Seasons:          " + if (numberOfSeasons > 0) numberOfSeasons.toString() else "N/A"
    }

    val statusLabel = remember(status) {
        "Status:            " + if (!status.isNullOrBlank() && status != "N/A") status else "N/A"
    }

    val languageLabel = remember(originalLanguage) {
        "Language:        " + (
                if (!originalLanguage.isNullOrBlank() && originalLanguage != "N/A")
                    originalLanguage
                else
                    "N/A"
                )
    }

    val releaseDateLabel = remember(releaseDate) {
        if (!releaseDate.isNullOrBlank() && releaseDate != "N/A") {
            val dateParts = releaseDate.split("-")
            if (dateParts.size == 3) {
                // Format as MM/DD/YYYY
                "Release Date:     ${dateParts[1]}/${dateParts[2]}/${dateParts[0]}"
            } else {
                "Release Date:     $releaseDate"
            }
        } else {
            "Release Date:      N/A"
        }
    }

    // ========================================================================================================================================= > // Seasons
    Text(
        text = seasonsLabel,
        color = Color(0xFF8B8B8B),
        fontSize = 17.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 258.25.dp, top = 34.dp)
    )
    // ========================================================================================================================================= >


    // ========================================================================================================================================= > // Status
    Text(
        text = statusLabel,
        color = Color(0xFF8B8B8B),
        fontSize = 17.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 258.25.dp, top = 48.dp)
    )
    // ========================================================================================================================================= >


    // ========================================================================================================================================= > // release_date
    Text(
        text = releaseDateLabel,
        color = Color(0xFF8B8B8B),
        fontSize = 16.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 258.25.dp, top = 63.dp)
    )

    // ========================================================================================================================================= >


    // ========================================================================================================================================= > // original language
    Text(
        text = languageLabel,
        color = Color(0xFF8B8B8B),
        fontSize = 17.sp,
        fontFamily = dongleRegular,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 258.25.dp, top = 75.dp)
    )
    // ========================================================================================================================================= >
}
