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
 * profile, and the profile's own "Recently watched" row first. Local News shows
 * the stations of the state picked on this TV -- none until one is picked, and
 * the row stays even then, so its state pill can be reached.
 */
internal object LiveTvData {

    const val RECENT_ROW = "Recently watched"
    const val KIDS_ROW = "Kids"
    private const val RECENT_MAX = 10
    private const val PREFS = "bluehive_live_tv"
    private const val RECENT_COLOR = 0xFF2644A6
    private const val LOCAL_STATE = "local_state"

    /** The channel the player last tuned, so the page puts its cursor there on return. */
    @Volatile var lastWatchedId: String? = null

    // The last list from the server, so picking a state rebuilds the rows at once.
    @Volatile private var lastServer: List<LiveRowDto> = emptyList()
    @Volatile private var lastKids = false

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
    fun buildRows(
        server: List<LiveRowDto>,
        kids: Boolean,
        recentIds: List<String>,
        localState: String? = null,
    ): List<Row> {
        val numbered = server.mapIndexed { r, row ->
            val color = parseColor(row.color)
            val local = row.name == LocalNews.ROW
            val listed = if (local) LocalNews.stationsFor(row, localState) else row.channels
            val channels = listed.mapIndexed { i, c ->
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
            }
            Row(row.name, color, channels)
        }.filter { (it.channels.isNotEmpty() || it.name == LocalNews.ROW) && (!kids || it.name == KIDS_ROW) }
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
        lastServer = channels.await().rows
        lastKids = kids.await()
        rebuild(context, profileId)
    }

    /** The last list again, with the state picked just now: no network. */
    fun rebuild(context: Context, profileId: Int): List<Row> =
        buildRows(lastServer, lastKids, recentIds(context, profileId), localState(context))

    /** The states the Local News picker offers, from the last list. */
    fun stateChoices(): List<LocalNews.StateChoice> =
        LocalNews.choices(lastServer.firstOrNull { it.name == LocalNews.ROW })

    /** The state picked on this TV (a household's TVs share a place, so not per profile). */
    fun localState(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LOCAL_STATE, null)

    fun setLocalState(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LOCAL_STATE, code).apply()
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
