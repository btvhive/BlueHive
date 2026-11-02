package com.example.bluehive.livetv

import java.time.ZoneId
import kotlin.math.pow

/**
 * The player's tuning screen (design B of live_tv_tuning_globe_designs_abc,
 * 2026-11-01): the movie loader's turning globe over the channel's own card --
 * its number and row as tags, its name, "Live · FHD · US Sports" -- and how far
 * the tune has got, as the movie loader shows its stages: the server finding
 * the stream (the session call), then the TV starting the picture. Its words
 * and colours; LiveTuningViews.kt draws it. Pure, so the JVM tests call it.
 */
internal object LiveTuning {

    /** What the bar and dots count: finding the stream, starting the picture, the picture. */
    const val STAGES = 3

    /** The first tag: the channel's number, or an On now game's league in its place. */
    fun lead(number: Int, league: String): String = if (number > 0) number.toString() else league

    /** Under the name: "Live · FHD · US Sports"; a game's "NFL · kicked off 1:00 PM" or "starts 4:25 PM". */
    fun meta(
        quality: String, row: String, start: Long, sport: String, league: String, nowS: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String =
        if (start > 0) LiveGames.startLine(start, sport, league, nowS, zone)
        else listOf("Live", quality, row).filter { it.isNotEmpty() }.joinToString(" · ")

    /** The red dot before [meta]: a channel always, a game once it has started. */
    fun liveDot(start: Long, nowS: Long): Boolean = start <= nowS

    /** The line under the card while the tune is at [stage]. */
    fun stageLine(stage: Int): String = if (stage <= 1) "Finding the live stream…" else "Starting the picture…"

    /**
     * The row tag's ink: white on the row's colour, unless that colour is too
     * pale for it (contrast under 3:1 -- News' gold, Local News' green), then
     * near-black.
     */
    fun ink(argb: Long): Long {
        fun lin(c: Long): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val l = 0.2126 * lin((argb shr 16) and 0xFFL) + 0.7152 * lin((argb shr 8) and 0xFFL) + 0.0722 * lin(argb and 0xFFL)
        return if (1.05 / (l + 0.05) >= 3.0) 0xFFFFFFFF else 0xFF121213
    }
}
