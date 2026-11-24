package com.example.bluehive.webview.exoplayer_resources

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import java.util.concurrent.atomic.AtomicLong

// ─────────────────────────────────────────────────────────────────────────────
//  THE PLAYER'S DOWNLOAD SPEED WHILE A CHUNK IS STILL ARRIVING (2026-11-20).
//
//  media3's bandwidth meter only records a sample when a download ENDS. On a
//  slow line the first 4K chunk is still arriving 6 s in, so the slow-start
//  fallback read its starting guess instead -- MEASURED on the Fire Stick at
//  5 Mbps: "speed 1000 kbps", and the film restarted at 480p when 720p fit.
//  This counts every byte the player reads, as it reads it.
// ─────────────────────────────────────────────────────────────────────────────

object ByteMeter {
    private val total = AtomicLong()

    /** Bytes the player has read since the app started. */
    fun bytes(): Long = total.get()

    /** Bits per second between two readings; 0 when there is nothing to go on. */
    fun bps(oldBytes: Long, oldMs: Long, newBytes: Long, newMs: Long): Long {
        val ms = newMs - oldMs
        val b = newBytes - oldBytes
        return if (ms <= 0 || b <= 0) 0L else b * 8L * 1000L / ms
    }

    /** Count what the player reads (every build). */
    @UnstableApi
    fun wrap(factory: DataSource.Factory): DataSource.Factory =
        DataSource.Factory { Counting(factory.createDataSource()) }

    @UnstableApi
    private class Counting(private val upstream: DataSource) : DataSource by upstream {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = upstream.read(buffer, offset, length)
            if (n > 0) total.addAndGet(n.toLong())
            return n
        }
    }
}
