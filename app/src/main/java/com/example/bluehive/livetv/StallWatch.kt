package com.example.bluehive.livetv

/**
 * Spots a live channel that has stopped moving while the player still thinks it
 * is playing.
 *
 * Found on the TV 2026-10-29 (Comedy Central): the source broke, the audio clock
 * stalled and no frames reached the decoder, and ExoPlayer flipped between
 * playing and buffering ~770 times in 36 s WITHOUT raising an error, so nothing
 * recovered until a press re-tuned the channel. Fed one sample a second while a
 * channel should be playing, this answers "stuck" when, over the last
 * [WINDOW_MS], the position advanced less than [MIN_ADVANCE_MS] (frozen, or
 * crawling through a stall loop) or not one new video frame was rendered (sound,
 * frozen picture). Pure, so the JVM tests drive the clock.
 */
internal class StallWatch {

    private data class Sample(val at: Long, val position: Long, val frames: Int?)

    private val samples = ArrayDeque<Sample>()

    /** After a tune, a jump in position or a pause: watch a whole window afresh. */
    fun reset() = samples.clear()

    /**
     * @param position where playback is in the stream's period: unlike the window
     *                 position it does not jump back when a live window slides
     * @param frames   video frames rendered so far, null without a video decoder
     */
    fun stuck(now: Long, position: Long, frames: Int?): Boolean {
        samples.addLast(Sample(now, position, frames))
        while (samples.size > 2 && now - samples[1].at >= WINDOW_MS) samples.removeFirst()
        val old = samples.first()
        if (now - old.at < WINDOW_MS) return false
        val frozenPicture = frames != null && old.frames != null && frames == old.frames
        return position - old.position < MIN_ADVANCE_MS || frozenPicture
    }

    companion object {
        const val WINDOW_MS = 8_000L
        const val MIN_ADVANCE_MS = 4_000L
    }
}
