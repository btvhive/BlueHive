package com.example.bluehive.singleShelfComponents

import android.util.Log
import com.example.bluehive.api.ApiClient
import com.example.bluehive.models.MediaBrowseResponse
import com.example.bluehive.models.MediaItem
import java.time.Instant

/**
 * FRESH PICKS (2026-11-20, the owner's design): the first row on the Movies and
 * TV Shows tabs. 40 random, well-rated titles from 2000 onwards (6.0+ with a
 * vote floor, genres taken in turn), the SAME for everyone, replaced every 2
 * days. The server builds it (/api/fresh-picks, bluehive_plugin/fresh_picks.py)
 * and hides what this profile has watched or favourited.
 *
 * The row is fetched ONCE per set and served to the shelf's two pages of 20
 * from memory, until the server's window_ends_at (then fetched again).
 * No countdown on screen (tried 2026-11-21, dropped as cluttered).
 */
object FreshPicks {
    private const val TAG = "FreshPicks"
    const val ROW_SIZE = 40

    private data class Entry(val profileId: Int, val endsAt: Instant?, val items: List<MediaItem>)
    private val cache = HashMap<MediaType, Entry>()

    /** The two pages of the row, from one fetch. Null on failure (the caller falls back). */
    suspend fun page(mediaType: MediaType, page: Int, pageSize: Int, profileId: Int): List<MediaItem>? {
        val now = Instant.now()
        val hit = cache[mediaType]?.takeIf { e ->
            e.profileId == profileId && (e.endsAt == null || now.isBefore(e.endsAt))
        }
        val entry = hit ?: run {
            val type = if (mediaType == MediaType.TV_SHOWS) "tv" else "movie"
            val resp: MediaBrowseResponse = try {
                ApiClient.bluehiveApi.getFreshPicks(type, profileId.takeIf { it > 0 }, ROW_SIZE)
            } catch (e: Exception) {
                Log.e(TAG, "fresh picks ($type) failed: ${e.message}")
                return null
            }
            val e = Entry(profileId, parseInstant(resp.windowEndsAt), resp.items)
            if (e.items.isEmpty()) return null
            cache[mediaType] = e
            Log.e(TAG, "fresh picks ($type): ${e.items.size} titles, new set at ${e.endsAt}")
            e
        }
        return slice(entry.items, page, pageSize)
    }

    /** Forget the cached row (a new profile, or the set rolled over). */
    fun clear() = cache.clear()

    // ── Pure (unit-tested) ──────────────────────────────────────────────────

    /** Page [page] of a row capped at [ROW_SIZE]; empty past the end. */
    fun <T> slice(items: List<T>, page: Int, pageSize: Int): List<T> {
        val row = items.take(ROW_SIZE)
        val from = page * pageSize
        if (page < 0 || pageSize <= 0 || from >= row.size) return emptyList()
        return row.subList(from, minOf(row.size, from + pageSize))
    }

    fun parseInstant(iso: String?): Instant? =
        iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
}
