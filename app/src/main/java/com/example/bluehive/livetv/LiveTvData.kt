package com.example.bluehive.livetv

import android.content.Context
import com.example.bluehive.api.ApiClient
import com.example.bluehive.api.LiveRowDto
import com.example.bluehive.utilities.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Live TV's channel list as the page shows it: the server's rows, numbered like
 * a cable box (row 1 = 101, 102...; row 2 = 201...), Kids-only for a kids
 * profile, and the profile's own "Recently watched" row first.
 */
internal object LiveTvData {

    const val RECENT_ROW = "Recently watched"
    const val KIDS_ROW = "Kids"
    private const val RECENT_MAX = 10
    private const val PREFS = "bluehive_live_tv"
    private const val RECENT_COLOR = 0xFF2644A6

    /** The channel the player last tuned, so the page puts its cursor there on return. */
    @Volatile var lastWatchedId: String? = null

    data class Channel(
        val id: String,
        val name: String,
        val row: String,
        val rowColor: Long,
        val flag: String,
        val quality: String,
        val hasBackup: Boolean,
        /** The server's last try failed: shown dimmed, still playable. */
        val off: Boolean,
        val number: Int,
    )

    data class Row(val name: String, val color: Long, val channels: List<Channel>)

    /** Server rows to page rows. Pure, so the JVM tests call it. */
    fun buildRows(server: List<LiveRowDto>, kids: Boolean, recentIds: List<String>): List<Row> {
        val numbered = server.mapIndexed { r, row ->
            val color = parseColor(row.color)
            Row(row.name, color, row.channels.mapIndexed { i, c ->
                Channel(
                    id = c.id,
                    name = c.name,
                    row = row.name,
                    rowColor = color,
                    flag = c.flag.orEmpty(),
                    quality = c.quality ?: "HD",
                    hasBackup = !c.backup.isNullOrBlank(),
                    off = c.status == "off",
                    number = (r + 1) * 100 + i + 1,
                )
            })
        }.filter { it.channels.isNotEmpty() && (!kids || it.name == KIDS_ROW) }
        val byId = numbered.flatMap { it.channels }.associateBy { it.id }
        val recent = recentIds.mapNotNull { byId[it] }
        return if (recent.isEmpty()) numbered else listOf(Row(RECENT_ROW, RECENT_COLOR, recent)) + numbered
    }

    /** "#D64545" to ARGB; the recent-row blue when missing or malformed. */
    fun parseColor(hex: String?): Long {
        val h = hex?.trim()?.removePrefix("#") ?: return RECENT_COLOR
        return h.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: RECENT_COLOR
    }

    /**
     * The list for [profileId]. The profile's type is looked up alongside the
     * channels; if that lookup fails the page shows every row (the channel list
     * itself failing is the error the page reports).
     */
    suspend fun load(context: Context, profileId: Int): List<Row> = coroutineScope {
        val channels = async { ApiClient.bluehiveApi.getLiveChannels() }
        val kids = async {
            runCatchingCancellable { ApiClient.bluehiveApi.listProfiles() }.getOrNull()
                ?.firstOrNull { it.id == profileId }?.profile_type == "kids"
        }
        buildRows(channels.await().rows, kids.await(), recentIds(context, profileId))
    }

    fun recentIds(context: Context, profileId: Int): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("recent_$profileId", "").orEmpty()
            .split(',').filter { it.isNotBlank() }

    fun noteWatched(context: Context, profileId: Int, channelId: String) {
        lastWatchedId = channelId
        val list = (listOf(channelId) + recentIds(context, profileId).filter { it != channelId }).take(RECENT_MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("recent_$profileId", list.joinToString(",")).apply()
    }
}
