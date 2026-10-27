package com.example.bluehive.details.moviesdetails

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.example.bluehive.details.shared.layout.DetailsInfoLine
import com.example.bluehive.details.shared.layout.DetailsInfoLines

/**
 * The lines that have a value, in their order: revenue, budget, release date,
 * language. A missing one is left out, so the rest close up instead of leaving
 * its slot empty (the user's call, 2026-10-26) -- that includes a release date
 * or language the screen used to print as "N/A". The hand-tuned spacing inside
 * the strings is unchanged. Pure, so MovieInfoLabelsTest pins it.
 */
@SuppressLint("DefaultLocale")
internal fun movieInfoRows(
    displayRevenue: Long,
    displayBudget: Long,
    releaseDate: String?,
    originalLanguage: String?,
): List<DetailsInfoLine> = buildList {
    if (displayRevenue > 0) add(DetailsInfoLine("Revenue:          $${"%,d".format(displayRevenue)}.00", 17))
    if (displayBudget > 0) add(DetailsInfoLine("Budget:            $${"%,d".format(displayBudget)}.00", 17))
    if (releaseDate != null && hasReleaseDate(releaseDate)) {
        val dateParts = releaseDate.split("-")
        val text = if (dateParts.size == 3) {
            "Release Date:      ${dateParts[1]}/${dateParts[2]}/${dateParts[0]}"  // Format as MM/DD/YYYY
        } else {
            releaseDate
        }
        add(DetailsInfoLine(text, 16))
    }
    if (hasLanguage(originalLanguage)) {
        add(DetailsInfoLine("Language:         $originalLanguage", 17))
    }
}

private fun hasReleaseDate(releaseDate: String?) = !releaseDate.isNullOrBlank() && releaseDate != "N/A"
private fun hasLanguage(originalLanguage: String?) = !originalLanguage.isNullOrBlank() && originalLanguage != "N/A"

/**
 * How many lines [movieInfoRows] makes, without building them. The screen
 * places the genres line with it; the strings themselves are built inside
 * MovieInfoLabels, a restartable composable R8 keeps out of the screen
 * function (v445 built them in the screen and it grew 328 units).
 */
internal fun movieInfoRowCount(
    displayRevenue: Long,
    displayBudget: Long,
    releaseDate: String?,
    originalLanguage: String?,
): Int = (if (displayRevenue > 0) 1 else 0) + (if (displayBudget > 0) 1 else 0) +
    (if (hasReleaseDate(releaseDate)) 1 else 0) + (if (hasLanguage(originalLanguage)) 1 else 0)

/**
 * The movie screen's metadata labels, under the rating, stacked from the top
 * with no gaps (see [movieInfoRows] and DetailsInfoLines); the genres line
 * follows them through DetailsInfo.genresTop. Moved here word for word in
 * Stage 2 of the details redesign, made modular 2026-10-26.
 */
@Composable
internal fun BoxScope.MovieInfoLabels(
    displayRevenue: Long,
    displayBudget: Long,
    releaseDate: String?,
    originalLanguage: String?,
    dongleRegular: FontFamily,
) {
    val rows = remember(displayRevenue, displayBudget, releaseDate, originalLanguage) {
        movieInfoRows(displayRevenue, displayBudget, releaseDate, originalLanguage)
    }
    DetailsInfoLines(rows, dongleRegular)
}
