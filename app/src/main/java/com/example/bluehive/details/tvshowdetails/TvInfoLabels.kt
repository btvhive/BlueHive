package com.example.bluehive.details.tvshowdetails

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.example.bluehive.details.shared.layout.DetailsInfoLine
import com.example.bluehive.details.shared.layout.DetailsInfoLines

/**
 * The lines that have a value, in their order: seasons, status, release
 * date, language. A missing one is left out and the rest close up, as on the
 * movie page (2026-10-27; they used to print "N/A" in a fixed slot). The
 * hand-tuned spacing inside the strings is the screen's as it was. Pure, so
 * TvInfoLabelsTest pins it.
 */
internal fun tvInfoLines(
    numberOfSeasons: Int,
    status: String?,
    releaseDate: String?,
    originalLanguage: String?,
): List<DetailsInfoLine> = buildList {
    if (numberOfSeasons > 0) add(DetailsInfoLine("Seasons:          $numberOfSeasons", 17))
    if (status != null && isKnown(status)) add(DetailsInfoLine("Status:            $status", 17))
    if (releaseDate != null && isKnown(releaseDate)) {
        val dateParts = releaseDate.split("-")
        val text = if (dateParts.size == 3) {
            "Release Date:     ${dateParts[1]}/${dateParts[2]}/${dateParts[0]}"  // Format as MM/DD/YYYY
        } else {
            "Release Date:     $releaseDate"
        }
        add(DetailsInfoLine(text, 16))
    }
    if (originalLanguage != null && isKnown(originalLanguage)) {
        add(DetailsInfoLine("Language:        $originalLanguage", 17))
    }
}

private fun isKnown(value: String?) = !value.isNullOrBlank() && value != "N/A"

/**
 * How many lines [tvInfoLines] makes, without building them: the screen
 * places the genres line with it (DetailsInfo.genresTop). The strings are
 * built inside TvInfoLabels, out of the screen function's method budget.
 */
internal fun tvInfoLineCount(
    numberOfSeasons: Int,
    status: String?,
    releaseDate: String?,
    originalLanguage: String?,
): Int = (if (numberOfSeasons > 0) 1 else 0) + (if (isKnown(status)) 1 else 0) +
    (if (isKnown(releaseDate)) 1 else 0) + (if (isKnown(originalLanguage)) 1 else 0)

/**
 * The TV screen's metadata labels under the rating, stacked from the top with
 * no gaps -- see [tvInfoLines] and DetailsInfoLines, which the movie page
 * shares.
 *
 * They were the middle of TvDetailsInfoBlock, the first piece ever cut out of
 * TVShowsDetailsScreenContent (2026-09-21, after R8 made ART refuse that
 * class). A BoxScope extension so each label keeps its absolute position.
 */
@Composable
internal fun BoxScope.TvInfoLabels(
    numberOfSeasons:  Int,
    status:           String?,
    releaseDate:      String?,
    originalLanguage: String?,
    dongleRegular:    FontFamily,
) {
    val lines = remember(numberOfSeasons, status, releaseDate, originalLanguage) {
        tvInfoLines(numberOfSeasons, status, releaseDate, originalLanguage)
    }
    DetailsInfoLines(lines, dongleRegular)
}
