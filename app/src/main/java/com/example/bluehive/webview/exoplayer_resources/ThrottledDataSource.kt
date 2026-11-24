package com.example.bluehive.webview.exoplayer_resources

import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.example.bluehive.BuildConfig

// ─────────────────────────────────────────────────────────────────────────────
//  A SLOW CONNECTION ON DEMAND, FOR TEST BUILDS ONLY (2026-11-19).
//
//  The automatic quality step-down (QualityRules) has to be seen working on a
//  real TV, and a real TV cannot be given a slow line on request. This caps the
//  player's download speed, all streams together, at the rate set with
//
//      adb shell setprop debug.bluehive.kbps 3000     (kilobits per second)
//      adb shell setprop debug.bluehive.kbps 0        (off)
//
//  It exists ONLY in a LIVE_TEST build: wrap() returns the factory untouched in
//  every other build, so a published APK cannot be slowed by anything.
// ─────────────────────────────────────────────────────────────────────────────

/** The pacing arithmetic, pure so it is unit-tested. */
object ThrottleMath {
    /**
     * Reserve [bytes] at [bitsPerSecond] on a shared line that is next free at
     * [nextFreeNs]. Returns (nanoseconds to wait now, when the line is free
     * again). Readers on several threads share one line, so the TOTAL rate is
     * the cap, as on a real slow connection.
     */
    fun reserve(nowNs: Long, bytes: Int, bitsPerSecond: Long, nextFreeNs: Long): Pair<Long, Long> {
        if (bitsPerSecond <= 0 || bytes <= 0) return 0L to nextFreeNs
        val start = maxOf(nowNs, nextFreeNs)
        val end = start + bytes * 8L * 1_000_000_000L / bitsPerSecond
        return (end - nowNs) to end
    }

    /** Read in slices of about 1/20 s of the cap, so the pacing stays smooth. */
    fun slice(bitsPerSecond: Long, wanted: Int): Int =
        if (bitsPerSecond <= 0) wanted else minOf(wanted, maxOf(1024, (bitsPerSecond / 8 / 20).toInt()))
}

@UnstableApi
class ThrottledDataSource private constructor(private val upstream: DataSource) : DataSource by upstream {

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bps = limitBps()
        if (bps <= 0) return upstream.read(buffer, offset, length)
        val n = upstream.read(buffer, offset, ThrottleMath.slice(bps, length))
        if (n > 0) {
            val waitNs = synchronized(Line) {
                val (wait, next) = ThrottleMath.reserve(SystemClock.elapsedRealtimeNanos(), n, bps, Line.nextFreeNs)
                Line.nextFreeNs = next
                wait
            }
            if (waitNs > 0) Thread.sleep(waitNs / 1_000_000, (waitNs % 1_000_000).toInt())
        }
        return n
    }

    private object Line {
        var nextFreeNs = 0L
        @Volatile var cachedBps = 0L
        @Volatile var readAtMs = 0L
    }

    @UnstableApi
    class Factory(private val inner: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ThrottledDataSource(inner.createDataSource())
    }

    companion object {
        private const val PROP = "debug.bluehive.kbps"

        /** The factory, throttled in a test build; untouched in any other. */
        fun wrap(factory: DataSource.Factory): DataSource.Factory =
            if (BuildConfig.LIVE_TEST) Factory(factory) else factory

        /** The cap in bits/s (0 = off), re-read at most every 2 s. */
        fun limitBps(): Long {
            val now = SystemClock.elapsedRealtime()
            if (now - Line.readAtMs > 2_000) {
                Line.readAtMs = now
                Line.cachedBps = runCatching {
                    val sp = Class.forName("android.os.SystemProperties")
                    val v = sp.getMethod("get", String::class.java, String::class.java)
                        .invoke(null, PROP, "0") as String
                    (v.trim().toLongOrNull() ?: 0L) * 1000L
                }.getOrDefault(0L)
            }
            return Line.cachedBps
        }
    }
}
