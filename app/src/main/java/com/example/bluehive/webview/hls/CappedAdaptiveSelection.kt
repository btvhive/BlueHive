package com.example.bluehive.webview.hls

import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.AdaptationCheckpoint
import androidx.media3.exoplayer.upstream.BandwidthMeter
import com.google.common.collect.ImmutableList

/**
 * Auto quality WITHOUT A RELOAD AT EVERY STEP (2026-11-19).
 *
 * MEASURED on the Fire TV Stick 4K: moving Auto's cap by changing the track
 * selection parameters made media3's HLS discard everything buffered and
 * reload from the playhead (HlsSampleStreamWrapper.selectTracks: a changed
 * primary track selection with media buffered -> forceReset + seek). The
 * step down to 720p showed the loading spinner -- a stall caused by the very
 * thing meant to prevent stalls.
 *
 * So Auto keeps ONE adaptive selection for the whole film and moves a ceiling
 * INSIDE it: [cap] is read on every chunk, and the selection simply stops
 * choosing rungs above it. The switch lands at the next chunk boundary and the
 * buffer is kept. Within the cap it always takes the highest rung, whatever
 * ExoPlayer's own bandwidth guess says -- the sharp start FORCE_HIGHEST_BITRATE
 * gave -- because QualityRules, not ExoPlayer, decides when to step.
 */
@UnstableApi
class CappedAdaptiveSelection(
    group: TrackGroup,
    tracks: IntArray,
    type: Int,
    bandwidthMeter: BandwidthMeter,
    checkpoints: ImmutableList<AdaptationCheckpoint>,
    private val cap: () -> Int,
) : AdaptiveTrackSelection(
    group, tracks, type, bandwidthMeter,
    MIN_BUFFER_TO_STEP_UP_MS,
    // Step DOWN at once, however much is buffered: QualityRules already waited
    // for the evidence, and media3's default (keep the rung while 25 s are
    // buffered) would sit on it.
    STEP_DOWN_AT_ONCE_MS,
    MIN_BUFFER_TO_STEP_UP_MS,
    // STEPPING UP REPLACES BUFFERED HD. media3 only re-fetches buffered chunks
    // up to 720p by default; MEASURED on the Fire Stick, a step back up to 4K
    // then waited behind ~100 s of already-downloaded 1080p. Chunks up to
    // 1080p (any width: films are cropped, 2542x1080) beyond the first
    // MIN_BUFFER_TO_STEP_UP_MS are replaced instead.
    MAX_WIDTH_TO_DISCARD, MAX_HEIGHT_TO_DISCARD,
    AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION,
    AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
    checkpoints, Clock.DEFAULT,
) {
    override fun canSelectFormat(format: Format, trackBitrate: Int, effectiveBitrate: Long): Boolean =
        capAllows(format.height, cap())

    @UnstableApi
    class Factory(private val cap: () -> Int) : AdaptiveTrackSelection.Factory() {
        override fun createAdaptiveTrackSelection(
            group: TrackGroup,
            tracks: IntArray,
            type: Int,
            bandwidthMeter: BandwidthMeter,
            adaptationCheckpoints: ImmutableList<AdaptationCheckpoint>,
        ): AdaptiveTrackSelection = CappedAdaptiveSelection(group, tracks, type, bandwidthMeter, adaptationCheckpoints, cap)
    }

    companion object {
        /** Step UP only with this much buffered, so going up cannot cause a stall. */
        const val MIN_BUFFER_TO_STEP_UP_MS = 10_000L

        /** "Keep the rung while this much is buffered": a day, i.e. never keep it. */
        const val STEP_DOWN_AT_ONCE_MS = 86_400_000L

        /** Buffered chunks up to this size may be replaced by a better rung. */
        const val MAX_WIDTH_TO_DISCARD = 2600
        const val MAX_HEIGHT_TO_DISCARD = 1080

        /** A rung of unknown height is never ruled out by the cap. */
        fun capAllows(height: Int, cap: Int): Boolean = height <= 0 || height <= cap
    }
}
