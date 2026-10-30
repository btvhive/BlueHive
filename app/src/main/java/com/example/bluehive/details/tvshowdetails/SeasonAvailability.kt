package com.example.bluehive.details.tvshowdetails

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.*
import com.example.bluehive.repository.EpisodesRepository
import com.example.bluehive.models.Episode
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Today as a yyyyMMdd integer, in the DEVICE's own timezone.
 *
 * Calendar rather than java.time: minSdk is 25 and LocalDate needs 26 or
 * desugaring, and a plain calendar-day number needs neither.
 */
internal fun currentYmd(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.YEAR) * 10000 +
           (c.get(java.util.Calendar.MONTH) + 1) * 100 +
           c.get(java.util.Calendar.DAY_OF_MONTH)
}


/**
 * Has this episode not aired yet?
 *
 * Compared as yyyyMMdd INTEGERS. The input is a fixed ISO format and the
 * question is a pure calendar-day one, so there is no locale or timezone
 * conversion to get wrong -- and this runs per card during D-pad scrolling.
 *
 * FAILS OPEN IN BOTH AMBIGUOUS CASES, because wrongly blocking an episode that
 * would have played is worse than letting through one that fails:
 *
 *   * a missing or malformed date is not evidence of anything;
 *   * an episode airing TODAY is treated as available. TMDB dates carry no time
 *     of day, the box runs UTC-4, and by the time someone is looking at this row
 *     an episode dated today is usually already up.
 */
internal fun isEpisodeUnaired(raw: String?, todayYmd: Int): Boolean {
    val s = raw?.trim().orEmpty()
    if (s.length < 10) return false
    val y = s.substring(0, 4).toIntOrNull() ?: return false
    val m = s.substring(5, 7).toIntOrNull() ?: return false
    val d = s.substring(8, 10).toIntOrNull() ?: return false
    return (y * 10000 + m * 100 + d) > todayYmd
}

/**
 * Does this season have anything to watch yet? True once any episode has aired
 * by [isEpisodeUnaired]'s rule, so it fails open the same way: an episode with
 * no usable date counts. An empty season has nothing.
 */
internal fun seasonHasAiredEpisode(episodes: List<Episode>, todayYmd: Int): Boolean =
    episodes.any { !isEpisodeUnaired(it.airDate, todayYmd) }

/** How many of the newest seasons the season check looks at before listing the rest unchecked. */
internal const val MAX_SEASON_CHECKS = 3

/**
 * How many seasons the season column lists, counting from Season 1:
 * [seasonCount] less the newest seasons with nothing aired yet -- an announced
 * season with no episodes, or one whose every episode is dated after today.
 *
 * ONLY THE NEWEST SEASONS ARE EVER LEFT OFF. Seasons air in order, so "not out
 * yet" only happens at the end; an empty season in the middle is missing data,
 * not an unaired season. Keeping the list a prefix also keeps every index in
 * the column equal to season - 1, which the focus code relies on.
 *
 * Walks back from the newest season and stops at the first one that
 *   * has an aired episode -- it and every season before it are listed;
 *   * could not be checked ([episodesOf] returned null) -- unknown is listed;
 *   * is Season 1, which is always listed, so the column is never empty;
 *   * is past [maxChecks] seasons -- the rest are listed unchecked.
 *
 * Nothing carries over between calls: once an episode's date arrives, the next
 * call finds it aired and the season is listed again.
 */
internal suspend fun watchableSeasonCount(
    seasonCount: Int,
    todayYmd: Int,
    maxChecks: Int = MAX_SEASON_CHECKS,
    episodesOf: suspend (season: Int) -> List<Episode>?,
): Int {
    var listed = seasonCount
    var checks = 0
    while (listed > 1 && checks < maxChecks) {
        val episodes = episodesOf(listed) ?: return listed
        if (seasonHasAiredEpisode(episodes, todayYmd)) return listed
        listed--
        checks++
    }
    return listed
}

/** How long the season column waits for the season check before listing every season. */
private const val SEASON_CHECK_TIMEOUT_MS = 3_000L

/**
 * How many times this screen has come into view: once when it opens, then on
 * every return to it -- from the player, the screensaver, standby. Work keyed
 * on it is redone whenever the viewer comes back.
 */
@Composable
internal fun rememberResumeCount(): Int {
    val returns = remember { ReturnCounter() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> returns.onEvent(event) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return returns.count
}

/**
 * Counts the screen's RETURNS: a resume after it went away (paused). The
 * observer is added after the first frame, when the screen is already
 * resumed, and Lifecycle replays ON_CREATE / ON_START / ON_RESUME to it on
 * the spot -- counting that replay restarted the season check (cancelling
 * the first, in flight) and recomposed the screen as it opened (v515). A
 * replay never holds an ON_PAUSE, so it counts nothing. Plain, so it is tested.
 */
internal class ReturnCounter {
    var count by mutableIntStateOf(0)
        private set
    private var away = false

    fun onEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> away = true
            Lifecycle.Event.ON_RESUME -> if (away) {
                away = false
                count++
            }
            else -> Unit
        }
    }
}

/**
 * The season column's length -- it lists Season 1 through the returned season
 * ([watchableSeasonCount]). Null until the first check answers; the column
 * waits for it, so a season is never shown and then taken away.
 *
 * Checked again on every resume ([resumeCount]). A season whose first episode
 * airs while the app is open is listed the next time the viewer comes back to
 * this screen, with nothing to expire: each check asks the endpoint the episode
 * grid uses, and reads today's date fresh.
 *
 * FAILS OPEN. A check that fails or runs past [SEASON_CHECK_TIMEOUT_MS] lists
 * every season (or keeps the list it had): an empty season on the list is the
 * old behaviour, while a season left off with episodes in it is unreachable.
 */
@Composable
internal fun rememberListedSeasons(
    tmdbId: Int,
    seasonCount: Int,
    resumeCount: Int,
    repository: EpisodesRepository,
): Int? {
    var listed by remember(tmdbId, seasonCount) { mutableStateOf<Int?>(null) }
    LaunchedEffect(tmdbId, seasonCount, resumeCount) {
        if (seasonCount <= 0) return@LaunchedEffect
        val counted = withTimeoutOrNull(SEASON_CHECK_TIMEOUT_MS) {
            watchableSeasonCount(seasonCount, currentYmd()) { season ->
                seasonEpisodesForCheck(repository, tmdbId, season)
            }
        }
        listed = (counted ?: listed ?: seasonCount).coerceIn(1, seasonCount)
    }
    return listed
}

/**
 * One season's episodes for the season check, or null when the check could not
 * be made. A 404 is an answer, not a failure: the backend has no such season,
 * so there is nothing in it to watch. Anything else is unknown.
 */
private suspend fun seasonEpisodesForCheck(
    repository: EpisodesRepository,
    tmdbId: Int,
    season: Int,
): List<Episode>? = try {
    repository.getSeasonEpisodes(tmdbId, season).episodes
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: retrofit2.HttpException) {
    if (e.code() == 404) emptyList() else null
} catch (e: Exception) {
    null
}

/**
 * "2022-02-03" -> "02/03/2022" for the date shown above each episode still.
 *
 * The API hands us `air_date` as a Python `date.isoformat()` on both the normal
 * and the season-reconstruction paths, so the input is always ISO or null. Parsed
 * by substring rather than a DateFormat: this runs per episode card during D-pad
 * scrolling, and there's no locale question when both ends of the conversion are
 * fixed formats.
 *
 * Returns "" for a missing or malformed date — unaired episodes carry a null
 * air_date, and the caller renders the blank so the row keeps its height.
 */
internal fun formatEpisodeAirDate(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.length < 10) return ""
    val year  = s.substring(0, 4)
    val month = s.substring(5, 7)
    val day   = s.substring(8, 10)
    if (!year.all(Char::isDigit) || !month.all(Char::isDigit) || !day.all(Char::isDigit)) return ""
    return "$month/$day/$year"
}
