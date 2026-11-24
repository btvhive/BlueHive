package com.example.bluehive.singleShelfComponents

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.example.bluehive.api.ApiClient
import com.example.bluehive.models.MediaItem
import com.example.bluehive.utilities.GoodCopyCountdown
import java.time.LocalDate

/**
 * NEW CONTENT (2026-11-21, the owner's design), two jobs:
 *
 *  1. The NEW RELEASES row, first on the Movies and TV Shows tabs: every new
 *     release (movies, only once in HD) or new season / episode (TV), newest
 *     on the left. No red tab inside this row.
 *  2. The red tab (NewContentTab) on these same titles WHEREVER ELSE they
 *     appear -- Fresh Picks, What's Popular, Netflix, Disney+, search... --
 *     WITHOUT adding, removing or moving a single card in those rows: a card
 *     simply looks its title up in [tabs].
 *
 * The server picks them (/api/new-content, bluehive_plugin/new_content.py).
 * Fetched once a day per type.
 */
object NewContent {
    private const val TAG = "NewContent"

    /** "movie:123" / "tv:456" -> (label, detail); read by MediaCard (state, so cards update when it arrives). */
    private val tabs = mutableStateMapOf<String, Pair<String, String?>>()

    private data class Entry(val day: LocalDate, val items: List<MediaItem>)
    private val cache = HashMap<String, Entry>()

    /** Both types, so a card on any tab or in search can show its tab. */
    suspend fun ensureLoaded() {
        items("movie"); items("tv")
    }

    /** The New Releases row's page [page] (all titles, no cap), tabs hidden. Null on failure. */
    suspend fun page(mediaType: MediaType, page: Int, pageSize: Int): List<MediaItem>? {
        val all = items(if (mediaType == MediaType.TV_SHOWS) "tv" else "movie") ?: return null
        if (all.isEmpty()) return null
        return slice(all, page, pageSize).map { it.copy(hideNewTab = true) }
    }

    /** The tab for a card anywhere but the New Releases row, or null. */
    fun tabFor(item: MediaItem): Pair<String, String?>? = tabs[key(item.mediaType, item.tmdbId)]

    private suspend fun items(type: String): List<MediaItem>? {
        val today = LocalDate.now()
        cache[type]?.takeIf { it.day == today }?.let { return it.items }
        val got = try {
            ApiClient.bluehiveApi.getNewContent(type).items
        } catch (e: Exception) {
            Log.e(TAG, "new content ($type) failed: ${e.message}")
            return null                            // not cached: tried again on the next load
        }
        val shown = eligible(got, today)
        cache[type] = Entry(today, shown)
        tabs.keys.filter { it.startsWith("$type:") }.forEach { tabs.remove(it) }
        shown.forEach { tabs[key(type, it.tmdbId)] = it.newLabel!! to it.newDetail }
        Log.e(TAG, "new content ($type): ${shown.size} titles, newest ${shown.firstOrNull()?.title}")
        return shown
    }

    // ── Pure (unit-tested) ──────────────────────────────────────────────────

    fun key(mediaType: String?, tmdbId: Int) = "${mediaType ?: "?"}:$tmdbId"

    /**
     * Titles that may count as new: a label, and NEVER a movie the app still
     * counts down to HD (GoodCopyCountdown) -- the server applies the same
     * rule with a day's margin; this is the app making sure.
     */
    fun eligible(items: List<MediaItem>, today: LocalDate): List<MediaItem> =
        items.filter { !it.newLabel.isNullOrBlank() && GoodCopyCountdown.labelFor(it, today) == null }

    /** Page [page] of the whole list; empty past the end. */
    fun <T> slice(items: List<T>, page: Int, pageSize: Int): List<T> {
        val from = page * pageSize
        if (page < 0 || pageSize <= 0 || from >= items.size) return emptyList()
        return items.subList(from, minOf(items.size, from + pageSize))
    }
}
