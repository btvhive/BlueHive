package com.example.bluehive.details.moviesdetails

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One metadata line: its text and its font size (the hand-tuned 17 / 16 sp). */
internal data class MovieInfoRow(val text: String, val fontSizeSp: Int)

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
): List<MovieInfoRow> = buildList {
    if (displayRevenue > 0) add(MovieInfoRow("Revenue:          $${"%,d".format(displayRevenue)}.00", 17))
    if (displayBudget > 0) add(MovieInfoRow("Budget:            $${"%,d".format(displayBudget)}.00", 17))
    if (releaseDate != null && hasReleaseDate(releaseDate)) {
        val dateParts = releaseDate.split("-")
        val text = if (dateParts.size == 3) {
            "Release Date:      ${dateParts[1]}/${dateParts[2]}/${dateParts[0]}"  // Format as MM/DD/YYYY
        } else {
            releaseDate
        }
        add(MovieInfoRow(text, 16))
    }
    if (hasLanguage(originalLanguage)) {
        add(MovieInfoRow("Language:         $originalLanguage", 17))
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

/** The four slots the lines always sat in when all were present, from the top. */
private val INFO_ROW_TOPS = listOf(34.dp, 48.dp, 63.dp, 75.dp)

/** Language's slot to the genres line (75 -> 96), kept after whichever line is last. */
private val GENRES_GAP = 21.dp

/** Line [index]'s top: the same slots as before, filled from the first. */
internal fun movieInfoRowTop(index: Int): Dp = INFO_ROW_TOPS[index.coerceIn(0, INFO_ROW_TOPS.lastIndex)]

/** Where the genres line goes under [rowCount] lines: 96 dp when all four are there. */
internal fun movieGenresTop(rowCount: Int): Dp =
    if (rowCount == 0) INFO_ROW_TOPS[0] else movieInfoRowTop(rowCount - 1) + GENRES_GAP

/**
 * The movie screen's metadata labels, under the rating, stacked from the top
 * with no gaps (see [movieInfoRows]); the genres line follows them through
 * DetailsInfo.genresTop. Moved here word for word in Stage 2 of the details
 * redesign, made modular 2026-10-26. A BoxScope extension so each label keeps
 * its absolute position in the screen's Box.
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
    rows.forEachIndexed { index, row ->
        Text(
            text = row.text,
            color = Color(0xFF8B8B8B),
            fontSize = row.fontSizeSp.sp,
            fontFamily = dongleRegular,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 258.25.dp, top = movieInfoRowTop(index))
        )
    }
}
