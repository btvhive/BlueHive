package com.example.bluehive.webview.hls

// ─────────────────────────────────────────────────────────────────────────────
//  VIDEO QUALITY FOR THE WEBSITE PLAYER (2026-11-19, VidNest first).
//
//  VidNest's best servers hand out a real ladder -- Beta's is 480/720/1080/2160,
//  distinct encodes -- and until now the TV played the top rung at or below
//  1080p and stayed there whatever the connection did (FORCE_HIGHEST_BITRATE,
//  MAX_VIDEO_HEIGHT). These rules let it use 4K where the TV can show it, and
//  step DOWN when a slow connection makes playback choppy -- the owner's ask:
//  "if the internet speed is good enough for 4K, it should go to 4K. If not, it
//  should fall back to 1080p".
//
//  WHY NOT EXOPLAYER'S OWN ADAPTIVE SELECTION. It starts low and climbs, which on
//  a TV reads as "it started blurry" (why FORCE_HIGHEST_BITRATE exists), and its
//  decisions are invisible. These rules keep the sharp start -- the selector
//  still forces the highest rung, under a CAP -- and move the cap on evidence a
//  person would agree with: the picture stalled, or the measured speed cannot
//  carry the rung. Everything here is pure, so it is unit-tested.
// ─────────────────────────────────────────────────────────────────────────────

/** One video rendition: its height and declared bitrate in bits/s (0 if unknown). */
data class Rung(val height: Int, val bitrate: Int)

object QualityRules {
    const val HD = 1080
    const val UHD = 2160

    /** Two stalls this close together mean the connection cannot carry the rung. */
    const val REBUFFER_WINDOW_MS = 60_000L
    const val REBUFFERS_TO_DROP = 2

    /** One stall this long is enough on its own. */
    const val LONG_REBUFFER_MS = 4_000L

    /**
     * A seek, the start, or a quality change refills the buffer ON PURPOSE.
     * Buffering that begins this soon after one is not the connection's fault.
     */
    const val SETTLE_MS = 6_000L

    /** Start on a rung only if the last measured speed carries it with this much room. */
    const val START_HEADROOM = 1.3

    /** Step back UP only with this much room: going up and straight down again is worse than staying. */
    const val STEP_UP_HEADROOM = 1.6

    /** A speed sample below this multiple of the playing rung's bitrate counts as slow... */
    const val SLOW_FRACTION = 1.1

    /** ...and this many in a row (one every SAMPLE_MS) step down... */
    const val SLOW_SAMPLES_TO_DROP = 3

    /** ...but only while the buffer ahead is short: a full buffer rides a dip out. */
    const val LOW_BUFFER_MS = 20_000L

    /** How often the controller reads the speed estimate. */
    const val SAMPLE_MS = 5_000L

    /** Step up only after this long without trouble... */
    const val SMOOTH_BEFORE_STEP_UP_MS = 120_000L

    /** ...and this many fast samples in a row. */
    const val FAST_SAMPLES_TO_STEP_UP = 3

    /**
     * A SLOW START counts too (2026-11-20). MEASURED on the Fire Stick: Man of
     * Steel's 4K from a cold Beta took 36.8 s from the player opening to the
     * first picture, and the stall rules never fired -- they only start once
     * something is playing. No picture this long after the player opened, while
     * above HD, and Auto starts on HD instead.
     */
    const val STARTUP_LIMIT_MS = 6_000L

    /** After a slow-start fallback, 4K is tried again after this long without trouble (not 2 minutes). */
    const val STARTUP_STEP_UP_MS = 30_000L

    /**
     * The slow-start fallback takes a rung the speed carries with NO extra room:
     * the estimate 6 s in already reads low (MEASURED on the Fire Stick: a 5 Mbps
     * line read under 3.7 Mbps then, 4.6 later), and START_HEADROOM on top of it
     * restarted at 480p when 720p played smoothly.
     */
    const val STARTUP_FALLBACK_HEADROOM = 1.0

    /**
     * Typical bitrates when a stream does not declare its own, and for the
     * start cap before the real ladder is known. From Beta's 4K ladder as the
     * TV saw it: 2160p 16 Mbps, 1080p 5.5, 720p 2.8.
     */
    val TYPICAL = listOf(Rung(2160, 16_000_000), Rung(1080, 5_500_000), Rung(720, 2_800_000), Rung(480, 1_200_000))

    /**
     * The highest rung worth playing on this TV: 4K when its display is in a
     * 4K mode, else 1080p. The Fire TV Stick 4K draws its menus at 1080p but
     * outputs 3840x2160, and the video layer is shown at the output size --
     * so the APP size is the wrong thing to ask.
     */
    fun ceiling(displayModeHeight: Int): Int = if (displayModeHeight >= UHD) UHD else HD

    /** Distinct heights, highest first; a repeated height keeps its highest bitrate. */
    fun ladder(rungs: List<Rung>): List<Rung> =
        rungs.filter { it.height > 0 }
            .groupBy { it.height }
            .map { (_, same) -> same.maxByOrNull { it.bitrate }!! }
            .sortedByDescending { it.height }

    /**
     * The rung Auto starts on: the highest at or below [ceiling] that the last
     * measured speed carries with START_HEADROOM. No measurement yet: the
     * highest (the owner's "go to 4K if it can").
     */
    fun startHeight(rungs: List<Rung>, ceiling: Int, lastBps: Long?, headroom: Double = START_HEADROOM): Int {
        val fit = ladder(rungs).filter { it.height <= ceiling }
        if (fit.isEmpty()) return ceiling
        if (lastBps == null || lastBps <= 0) return fit.first().height
        return fit.firstOrNull { it.bitrate <= 0 || it.bitrate * headroom <= lastBps }?.height
            ?: fit.last().height
    }

    /** The next rung below [height], or null at the bottom. */
    fun lower(rungs: List<Rung>, height: Int): Int? =
        ladder(rungs).firstOrNull { it.height < height }?.height

    /** The next rung above [height] and within [ceiling], or null at the top. */
    fun higher(rungs: List<Rung>, height: Int, ceiling: Int): Int? =
        ladder(rungs).lastOrNull { it.height > height && it.height <= ceiling }?.height

    /** A rung's bitrate, or the typical one for its height when the stream declares none. */
    fun bitrateOf(rungs: List<Rung>, height: Int): Int =
        ladder(rungs).firstOrNull { it.height == height }?.bitrate?.takeIf { it > 0 }
            ?: TYPICAL.firstOrNull { it.height <= height }?.bitrate ?: 0

    /**
     * Which ladder rung the picture on screen belongs to. The decoder reports
     * the REAL frame size, and a wide film's rungs are cropped: MEASURED on
     * Gladiator, Beta's "2160p" rung decodes at 3840x1600 and reported 1600.
     * The rung's declared bitrate identifies it when known; else the lowest
     * rung at least as tall as the frame.
     */
    fun rungFor(rungs: List<Rung>, frameHeight: Int, bitrate: Int): Int {
        val l = ladder(rungs)
        if (l.isEmpty()) return frameHeight
        if (bitrate > 0 && l.any { it.bitrate > 0 }) {
            return l.filter { it.bitrate > 0 }.minByOrNull { kotlin.math.abs(it.bitrate.toLong() - bitrate) }!!.height
        }
        return l.lastOrNull { it.height >= frameHeight }?.height ?: l.first().height
    }

    /**
     * The Quality pill's text (design #2): "Quality: 4K", "Quality: HD",
     * "Quality: 720p", "Quality: SD" -- the level on screen in the words people
     * use; just "Quality" before anything plays.
     */
    fun pillText(playingHeight: Int): String = when {
        playingHeight >= UHD -> "Quality: 4K"
        playingHeight >= HD -> "Quality: HD"
        playingHeight >= 720 -> "Quality: 720p"
        playingHeight > 0 -> "Quality: SD"
        else -> "Quality"
    }

    /** The picker's label for a height. */
    fun label(height: Int): String = if (height >= UHD) "${height}p (4K)" else "${height}p"
}

/**
 * Auto mode's state for one film. Every method takes the clock as an argument,
 * so the tests drive time. Each returns the NEW cap when it changes the cap,
 * else null; the caller applies it and tells the viewer.
 */
class AutoQuality(
    private var rungs: List<Rung>,
    private val ceiling: Int,
    startCap: Int,
    now: Long,
) {
    var cap: Int = startCap
        private set

    /** Why the cap last moved, for the log and the notice. */
    var lastReason: String = "start"
        private set

    private val stalls = ArrayDeque<Long>()
    private var settleUntil = now + QualityRules.SETTLE_MS
    private var lastTroubleAt = now
    private var slowSamples = 0
    private var fastSamples = 0
    private var stallCounted = false   // this stall already caused a drop
    private var startupOver = false    // the first picture came, or the slow-start rule already fired

    /**
     * Still no picture [waitedMs] after the player opened. Above HD and past
     * STARTUP_LIMIT_MS: start on the best rung at or below HD instead, and let
     * the climb back to 4K begin after STARTUP_STEP_UP_MS rather than 2 minutes.
     * Fires once per film.
     */
    fun onStartupWait(now: Long, waitedMs: Long, bps: Long? = null): Int? {
        if (startupOver || cap <= QualityRules.HD || waitedMs < QualityRules.STARTUP_LIMIT_MS) return null
        startupOver = true
        if (QualityRules.ladder(rungs).none { it.height <= QualityRules.HD }) return null
        // THE RUNG THE SPEED CARRIES, not always HD. MEASURED on the Fire Stick
        // at 5 Mbps: an HD restart stalled 6 s and then dropped to 720p anyway.
        val to = QualityRules.startHeight(rungs, QualityRules.HD, bps, QualityRules.STARTUP_FALLBACK_HEADROOM)
        val speed = if (bps != null && bps > 0) ", speed ${bps / 1000} kbps" else ""
        val moved = move(now, to, "no picture after ${waitedMs / 1000}s at ${cap}p$speed") ?: return null
        // Step-up is allowed once STARTUP_STEP_UP_MS pass without trouble.
        lastTroubleAt = now - (QualityRules.SMOOTH_BEFORE_STEP_UP_MS - QualityRules.STARTUP_STEP_UP_MS)
        return moved
    }

    /** The first picture is on screen: the slow-start rule is over for this film. */
    fun onFirstFrame() {
        startupOver = true
    }

    /** The real ladder arrived (it replaces the typical one used to start). */
    fun updateRungs(newRungs: List<Rung>) {
        if (newRungs.isNotEmpty()) rungs = newRungs
    }

    /** Start (again) on [height]: the real ladder's start rung, chosen once it is known. */
    fun startAt(height: Int, now: Long) {
        cap = height
        lastReason = "start"
        settleUntil = now + QualityRules.SETTLE_MS
        stalls.clear()
        slowSamples = 0
        fastSamples = 0
    }

    /** The viewer sought: the refill that follows is not the connection's fault. */
    fun onSeek(now: Long) {
        settleUntil = now + QualityRules.SETTLE_MS
    }

    /** Playback stopped to buffer while it was meant to play. */
    fun onStallStart(now: Long, playingHeight: Int): Int? {
        stallCounted = false
        if (now < settleUntil) return null
        lastTroubleAt = now
        stalls.addLast(now)
        while (stalls.isNotEmpty() && now - stalls.first() > QualityRules.REBUFFER_WINDOW_MS) stalls.removeFirst()
        if (stalls.size >= QualityRules.REBUFFERS_TO_DROP) {
            return drop(now, playingHeight, "${stalls.size} stalls within ${QualityRules.REBUFFER_WINDOW_MS / 1000}s")
        }
        return null
    }

    /** Still stalled, [stalledForMs] so far (called while it lasts). */
    fun onStallTick(now: Long, stalledForMs: Long, playingHeight: Int): Int? {
        if (stallCounted || now - stalledForMs < settleUntil) return null
        if (stalledForMs >= QualityRules.LONG_REBUFFER_MS) {
            stallCounted = true
            lastTroubleAt = now
            return drop(now, playingHeight, "stalled ${stalledForMs / 1000}s")
        }
        return null
    }

    /**
     * One speed reading: [bps] the player's estimate, [bufferAheadMs] what is
     * already downloaded past the playhead.
     */
    fun onSample(now: Long, bps: Long, playingHeight: Int, bufferAheadMs: Long): Int? {
        if (bps <= 0 || now < settleUntil) return null
        // Judged against what is being DOWNLOADED now, i.e. the cap -- not the
        // rung still playing out of the buffer. MEASURED on the Fire Stick: after
        // a drop to 1080p, 14 s of 4K was still buffered, the old rule compared
        // 7.3 Mbps with 4K's 16 and dropped again to 720p for nothing.
        val loading = if (playingHeight > 0) minOf(playingHeight, cap) else cap
        val playingRate = QualityRules.bitrateOf(rungs, loading)
        if (playingRate > 0 && bps < playingRate * QualityRules.SLOW_FRACTION &&
            bufferAheadMs < QualityRules.LOW_BUFFER_MS) {
            fastSamples = 0
            if (++slowSamples >= QualityRules.SLOW_SAMPLES_TO_DROP) {
                lastTroubleAt = now
                return drop(now, playingHeight, "speed ${bps / 1000} kbps < ${playingRate / 1000} kbps needed")
            }
            return null
        }
        slowSamples = 0
        val up = QualityRules.higher(rungs, cap, ceiling) ?: return null.also { fastSamples = 0 }
        val upRate = QualityRules.bitrateOf(rungs, up)
        if (now - lastTroubleAt >= QualityRules.SMOOTH_BEFORE_STEP_UP_MS && upRate > 0 &&
            bps >= upRate * QualityRules.STEP_UP_HEADROOM) {
            if (++fastSamples >= QualityRules.FAST_SAMPLES_TO_STEP_UP) {
                return move(now, up, "speed ${bps / 1000} kbps carries ${up}p")
            }
        } else {
            fastSamples = 0
        }
        return null
    }

    private fun drop(now: Long, playingHeight: Int, why: String): Int? {
        val from = minOf(cap, if (playingHeight > 0) playingHeight else cap)
        val to = QualityRules.lower(rungs, from) ?: return null
        return move(now, to, why)
    }

    private fun move(now: Long, to: Int, why: String): Int? {
        if (to == cap) return null
        cap = to
        lastReason = why
        settleUntil = now + QualityRules.SETTLE_MS
        stalls.clear()
        slowSamples = 0
        fastSamples = 0
        return to
    }
}
