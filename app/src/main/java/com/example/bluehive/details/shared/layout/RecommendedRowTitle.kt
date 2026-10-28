package com.example.bluehive.details.shared.layout

import com.example.bluehive.api.isJapaneseAnimation

/**
 * The recommended row's heading on a details page, named for what the page
 * is (the user's experiment, 2026-10-28; it said "Recommended Media"):
 *   - a film: "Movies You May Also Like"
 *   - a show: "TV Shows You May Also Like"
 *   - Japanese animation (isJapaneseAnimation, as the Watch buttons decide
 *     it): "Anime Movies ..." / "Anime Series ...".
 * Pure, so RecommendedRowTitleTest pins it.
 */
fun recommendedRowTitle(mediaType: String, genres: String?, originalLanguage: String?): String {
    val isTv = mediaType.equals("tv", ignoreCase = true)
    val kind = when {
        isJapaneseAnimation(genres, originalLanguage) -> if (isTv) "Anime Series" else "Anime Movies"
        isTv -> "TV Shows"
        else -> "Movies"
    }
    return "$kind You May Also Like"
}
