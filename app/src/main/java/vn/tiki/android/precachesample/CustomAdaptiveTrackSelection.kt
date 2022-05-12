/*
package vn.tiki.android.precachesample

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.chunk.MediaChunk
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.BaseTrackSelection
import com.google.android.exoplayer2.trackselection.FixedTrackSelection
import com.google.android.exoplayer2.trackselection.TrackSelection
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.Util

*/
/*
* Copyright (C) 2016 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*//*

import org.checkerframework.checker.nullness.compatqual.NullableType

*/
/**
 * A bandwidth based adaptive [TrackSelection], whose selected track is updated to be the one
 * of highest quality given the current network conditions and the state of the buffer.
 *//*

class AdaptiveTrackSelection private constructor(
    group: TrackGroup,
    tracks: IntArray,
    private val bandwidthProvider: BandwidthProvider,
    minDurationForQualityIncreaseMs: Long,
    maxDurationForQualityDecreaseMs: Long,
    minDurationToRetainAfterDiscardMs: Long,
    bufferedFractionToLiveEdgeForQualityIncrease: Float,
    minTimeBetweenBufferReevaluationMs: Long,
    clock: Clock
) : BaseTrackSelection(group, *tracks) {
    */
/** Factory for [AdaptiveTrackSelection] instances.  *//*

    class Factory @Deprecated(
        """Use {@link #Factory(int, int, int, float, float, long, Clock)} instead. Custom
          bandwidth meter should be directly passed to the player in {@link
     *     SimpleExoPlayer.Builder}."""
    ) constructor(
        private val bandwidthMeter: BandwidthMeter?,
        private val minDurationForQualityIncreaseMs: Int,
        private val maxDurationForQualityDecreaseMs: Int,
        private val minDurationToRetainAfterDiscardMs: Int,
        private val bandwidthFraction: Float,
        private val bufferedFractionToLiveEdgeForQualityIncrease: Float,
        private val minTimeBetweenBufferReevaluationMs: Long,
        private val clock: Clock?
    ) : TrackSelection.Factory {

        @Deprecated(
            """Use {@link #Factory()} instead. Custom bandwidth meter should be directly passed
          to the player in {@link SimpleExoPlayer.Builder}."""
        )
        constructor(bandwidthMeter: BandwidthMeter?) : this(
            bandwidthMeter,
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            AdaptiveTrackSelection.Companion.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            AdaptiveTrackSelection.Companion.DEFAULT_BANDWIDTH_FRACTION,
            AdaptiveTrackSelection.Companion.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
            Clock.DEFAULT
        ) {
        }

        @Deprecated(
            """Use {@link #Factory(int, int, int, float)} instead. Custom bandwidth meter should
          be directly passed to the player in {@link SimpleExoPlayer.Builder}."""
        )
        constructor(
            bandwidthMeter: BandwidthMeter?,
            minDurationForQualityIncreaseMs: Int,
            maxDurationForQualityDecreaseMs: Int,
            minDurationToRetainAfterDiscardMs: Int,
            bandwidthFraction: Float
        ) : this(
            bandwidthMeter,
            minDurationForQualityIncreaseMs,
            maxDurationForQualityDecreaseMs,
            minDurationToRetainAfterDiscardMs,
            bandwidthFraction,
            AdaptiveTrackSelection.Companion.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
            Clock.DEFAULT
        ) {
        }
        */
/**
         * Creates an adaptive track selection factory.
         *
         * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
         * selected track to switch to one of higher quality.
         * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
         * selected track to switch to one of lower quality.
         * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
         * quality, the selection may indicate that media already buffered at the lower quality can
         * be discarded to speed up the switch. This is the minimum duration of media that must be
         * retained at the lower quality.
         * @param bandwidthFraction The fraction of the available bandwidth that the selection should
         * consider available for use. Setting to a value less than 1 is recommended to account for
         * inaccuracies in the bandwidth estimator.
         * @param bufferedFractionToLiveEdgeForQualityIncrease For live streaming, the fraction of the
         * duration from current playback position to the live edge that has to be buffered before
         * the selected track can be switched to one of higher quality. This parameter is only
         * applied when the playback position is closer to the live edge than `minDurationForQualityIncreaseMs`, which would otherwise prevent switching to a higher
         * quality from happening.
         * @param minTimeBetweenBufferReevaluationMs The track selection may periodically reevaluate its
         * buffer and discard some chunks of lower quality to improve the playback quality if
         * network conditions have changed. This is the minimum duration between 2 consecutive
         * buffer reevaluation calls.
         * @param clock A [Clock].
         *//*

        */
/** Creates an adaptive track selection factory with default parameters.  *//*

        */
/**
         * Creates an adaptive track selection factory.
         *
         * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
         * selected track to switch to one of higher quality.
         * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
         * selected track to switch to one of lower quality.
         * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
         * quality, the selection may indicate that media already buffered at the lower quality can
         * be discarded to speed up the switch. This is the minimum duration of media that must be
         * retained at the lower quality.
         * @param bandwidthFraction The fraction of the available bandwidth that the selection should
         * consider available for use. Setting to a value less than 1 is recommended to account for
         * inaccuracies in the bandwidth estimator.
         *//*

        @JvmOverloads
        constructor(
            minDurationForQualityIncreaseMs: Int =
                AdaptiveTrackSelection.Companion.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            maxDurationForQualityDecreaseMs: Int =
                AdaptiveTrackSelection.Companion.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            minDurationToRetainAfterDiscardMs: Int =
                AdaptiveTrackSelection.Companion.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            bandwidthFraction: Float =
                AdaptiveTrackSelection.Companion.DEFAULT_BANDWIDTH_FRACTION,
            bufferedFractionToLiveEdgeForQualityIncrease: Float =
                AdaptiveTrackSelection.Companion.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
            minTimeBetweenBufferReevaluationMs: Long =
                AdaptiveTrackSelection.Companion.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
            clock: Clock? =
                Clock.DEFAULT
        ) : this( */
/* bandwidthMeter= *//*

            null,
            minDurationForQualityIncreaseMs,
            maxDurationForQualityDecreaseMs,
            minDurationToRetainAfterDiscardMs,
            bandwidthFraction,
            bufferedFractionToLiveEdgeForQualityIncrease,
            minTimeBetweenBufferReevaluationMs,
            clock
        ) {
        }

        @NullableType
        override fun createTrackSelections(
            @NullableType definitions: Array<Definition>, bandwidthMeter: BandwidthMeter
        ): Array<TrackSelection> {
            var bandwidthMeter: BandwidthMeter? = bandwidthMeter
            if (this.bandwidthMeter != null) {
                bandwidthMeter = this.bandwidthMeter
            }
            val selections = arrayOfNulls<TrackSelection>(definitions.size)
            var totalFixedBandwidth = 0
            for (i in definitions.indices) {
                val definition = definitions[i]
                if (definition != null && definition.tracks.size == 1) {
                    // Make fixed selections first to know their total bandwidth.
                    selections[i] = FixedTrackSelection(
                        definition.group, definition.tracks[0], definition.reason, definition.data
                    )
                    val trackBitrate = definition.group.getFormat(definition.tracks[0]).bitrate
                    if (trackBitrate != Format.NO_VALUE) {
                        totalFixedBandwidth += trackBitrate
                    }
                }
            }
            val adaptiveSelections: MutableList<AdaptiveTrackSelection> = ArrayList()
            for (i in definitions.indices) {
                val definition = definitions[i]
                if (definition != null && definition.tracks.size > 1) {
                    val adaptiveSelection = createAdaptiveTrackSelection(
                        definition.group, bandwidthMeter, definition.tracks, totalFixedBandwidth
                    )
                    adaptiveSelections.add(adaptiveSelection)
                    selections[i] = adaptiveSelection
                }
            }
            if (adaptiveSelections.size > 1) {
                val adaptiveTrackBitrates = arrayOfNulls<LongArray>(adaptiveSelections.size)
                for (i in adaptiveSelections.indices) {
                    val adaptiveSelection = adaptiveSelections[i]
                    adaptiveTrackBitrates[i] = LongArray(adaptiveSelection.length())
                    for (j in 0 until adaptiveSelection.length()) {
                        adaptiveTrackBitrates[i]!![j] =
                            adaptiveSelection.getFormat(adaptiveSelection.length() - j - 1).bitrate.toLong()
                    }
                }
                val bandwidthCheckpoints: Array<Array<LongArray>> =
                    AdaptiveTrackSelection.Companion.getAllocationCheckpoints(adaptiveTrackBitrates)
                for (i in adaptiveSelections.indices) {
                    adaptiveSelections[i]
                        .experimental_setBandwidthAllocationCheckpoints(bandwidthCheckpoints[i])
                }
            }
            return selections
        }

        */
/**
         * Creates a single adaptive selection for the given group, bandwidth meter and tracks.
         *
         * @param group The [TrackGroup].
         * @param bandwidthMeter A [BandwidthMeter] which can be used to select tracks.
         * @param tracks The indices of the selected tracks in the track group.
         * @param totalFixedTrackBandwidth The total bandwidth used by all non-adaptive tracks, in bits
         * per second.
         * @return An [AdaptiveTrackSelection] for the specified tracks.
         *//*

        protected fun createAdaptiveTrackSelection(
            group: TrackGroup?,
            bandwidthMeter: BandwidthMeter?,
            tracks: IntArray?,
            totalFixedTrackBandwidth: Int
        ): AdaptiveTrackSelection {
            return com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection(
                group!!,
                tracks!!,
                DefaultBandwidthProvider(
                    bandwidthMeter, bandwidthFraction,
                    totalFixedTrackBandwidth.toLong()
                ),
                minDurationForQualityIncreaseMs.toLong(),
                maxDurationForQualityDecreaseMs.toLong(),
                minDurationToRetainAfterDiscardMs.toLong(),
                bufferedFractionToLiveEdgeForQualityIncrease,
                minTimeBetweenBufferReevaluationMs,
                clock!!
            )
        }

    }

    private val minDurationForQualityIncreaseUs: Long
    private val maxDurationForQualityDecreaseUs: Long

    */
/**
     * Called from [.evaluateQueueSize] to determine the minimum duration of buffer
     * to retain after discarding chunks.
     *
     * @return The minimum duration of buffer to retain after discarding chunks, in microseconds.
     *//*

    protected val minDurationToRetainAfterDiscardUs: Long
    private val bufferedFractionToLiveEdgeForQualityIncrease: Float
    private val minTimeBetweenBufferReevaluationMs: Long
    private val clock: Clock
    private var playbackSpeed: Float
    private var selectedIndex = 0
    private var reason: Int
    private var lastBufferEvaluationMs: Long
    */
/**
     * @param group The [TrackGroup].
     * @param tracks The indices of the selected tracks within the [TrackGroup]. Must not be
     * empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     * @param reservedBandwidth The reserved bandwidth, which shouldn't be considered available for
     * use, in bits per second.
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     * selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     * selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a track of significantly higher
     * quality, the selection may indicate that media already buffered at the lower quality can be
     * discarded to speed up the switch. This is the minimum duration of media that must be
     * retained at the lower quality.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     * consider available for use. Setting to a value less than 1 is recommended to account for
     * inaccuracies in the bandwidth estimator.
     * @param bufferedFractionToLiveEdgeForQualityIncrease For live streaming, the fraction of the
     * duration from current playback position to the live edge that has to be buffered before the
     * selected track can be switched to one of higher quality. This parameter is only applied
     * when the playback position is closer to the live edge than `minDurationForQualityIncreaseMs`, which would otherwise prevent switching to a higher
     * quality from happening.
     * @param minTimeBetweenBufferReevaluationMs The track selection may periodically reevaluate its
     * buffer and discard some chunks of lower quality to improve the playback quality if network
     * condition has changed. This is the minimum duration between 2 consecutive buffer
     * reevaluation calls.
     *//*

    */
/**
     * @param group The [TrackGroup].
     * @param tracks The indices of the selected tracks within the [TrackGroup]. Must not be
     * empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     *//*

    @JvmOverloads
    constructor(
        group: TrackGroup,
        tracks: IntArray,
        bandwidthMeter: BandwidthMeter?,
        reservedBandwidth: Long =  */
/* reservedBandwidth= *//*

            0,
        minDurationForQualityIncreaseMs: Long =
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS.toLong(),
        maxDurationForQualityDecreaseMs: Long =
            AdaptiveTrackSelection.Companion.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS.toLong(),
        minDurationToRetainAfterDiscardMs: Long =
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS.toLong(),
        bandwidthFraction: Float =
            AdaptiveTrackSelection.Companion.DEFAULT_BANDWIDTH_FRACTION,
        bufferedFractionToLiveEdgeForQualityIncrease: Float =
            AdaptiveTrackSelection.Companion.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        minTimeBetweenBufferReevaluationMs: Long =
            AdaptiveTrackSelection.Companion.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
        clock: Clock =
            Clock.DEFAULT
    ) : this(
        group,
        tracks,
        DefaultBandwidthProvider(bandwidthMeter, bandwidthFraction, reservedBandwidth),
        minDurationForQualityIncreaseMs,
        maxDurationForQualityDecreaseMs,
        minDurationToRetainAfterDiscardMs,
        bufferedFractionToLiveEdgeForQualityIncrease,
        minTimeBetweenBufferReevaluationMs,
        clock
    ) {
    }

    */
/**
     * Sets checkpoints to determine the allocation bandwidth based on the total bandwidth.
     *
     * @param allocationCheckpoints List of checkpoints. Each element must be a long[2], with [0]
     * being the total bandwidth and [1] being the allocated bandwidth.
     *//*

    fun experimental_setBandwidthAllocationCheckpoints(allocationCheckpoints: Array<LongArray>) {
        (bandwidthProvider as DefaultBandwidthProvider)
            .experimental_setBandwidthAllocationCheckpoints(allocationCheckpoints)
    }

    override fun enable() {
        lastBufferEvaluationMs = C.TIME_UNSET
    }

    override fun onPlaybackSpeed(playbackSpeed: Float) {
        this.playbackSpeed = playbackSpeed
    }

    override fun updateSelectedTrack(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        availableDurationUs: Long,
        queue: List<MediaChunk>,
        mediaChunkIterators: Array<MediaChunkIterator>
    ) {
        val nowMs = clock.elapsedRealtime()

        // Make initial selection
        if (reason == C.SELECTION_REASON_UNKNOWN) {
            reason = C.SELECTION_REASON_INITIAL
            selectedIndex = determineIdealSelectedIndex(nowMs)
            return
        }

        // Stash the current selection, then make a new one.
        val currentSelectedIndex = selectedIndex
        selectedIndex = determineIdealSelectedIndex(nowMs)
        if (selectedIndex == currentSelectedIndex) {
            return
        }
        if (!isBlacklisted(currentSelectedIndex, nowMs)) {
            // Revert back to the current selection if conditions are not suitable for switching.
            val currentFormat = getFormat(currentSelectedIndex)
            val selectedFormat = getFormat(selectedIndex)
            if (selectedFormat.bitrate > currentFormat.bitrate
                && bufferedDurationUs < minDurationForQualityIncreaseUs(availableDurationUs)
            ) {
                // The selected track is a higher quality, but we have insufficient buffer to safely switch
                // up. Defer switching up for now.
                selectedIndex = currentSelectedIndex
            } else if (selectedFormat.bitrate < currentFormat.bitrate
                && bufferedDurationUs >= maxDurationForQualityDecreaseUs
            ) {
                // The selected track is a lower quality, but we have sufficient buffer to defer switching
                // down for now.
                selectedIndex = currentSelectedIndex
            }
        }
        // If we adapted, update the trigger.
        if (selectedIndex != currentSelectedIndex) {
            reason = C.SELECTION_REASON_ADAPTIVE
        }
    }

    override fun getSelectedIndex(): Int {
        return selectedIndex
    }

    override fun getSelectionReason(): Int {
        return reason
    }

    override fun getSelectionData(): Any? {
        return null
    }

    override fun evaluateQueueSize(playbackPositionUs: Long, queue: List<MediaChunk>): Int {
        val nowMs = clock.elapsedRealtime()
        if (!shouldEvaluateQueueSize(nowMs)) {
            return queue.size
        }
        lastBufferEvaluationMs = nowMs
        if (queue.isEmpty()) {
            return 0
        }
        val queueSize = queue.size
        val lastChunk = queue[queueSize - 1]
        val playoutBufferedDurationBeforeLastChunkUs = Util.getPlayoutDurationForMediaDuration(
            lastChunk.startTimeUs - playbackPositionUs, playbackSpeed
        )
        val minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardUs
        if (playoutBufferedDurationBeforeLastChunkUs < minDurationToRetainAfterDiscardUs) {
            return queueSize
        }
        val idealSelectedIndex = determineIdealSelectedIndex(nowMs)
        val idealFormat = getFormat(idealSelectedIndex)
        // If the chunks contain video, discard from the first SD chunk beyond
        // minDurationToRetainAfterDiscardUs whose resolution and bitrate are both lower than the ideal
        // track.
        for (i in 0 until queueSize) {
            val chunk = queue[i]
            val format = chunk.trackFormat
            val mediaDurationBeforeThisChunkUs = chunk.startTimeUs - playbackPositionUs
            val playoutDurationBeforeThisChunkUs = Util.getPlayoutDurationForMediaDuration(
                mediaDurationBeforeThisChunkUs,
                playbackSpeed
            )
            if (playoutDurationBeforeThisChunkUs >= minDurationToRetainAfterDiscardUs && format.bitrate < idealFormat.bitrate && format.height != Format.NO_VALUE && format.height < 720 && format.width != Format.NO_VALUE && format.width < 1280 && format.height < idealFormat.height) {
                return i
            }
        }
        return queueSize
    }

    */
/**
     * Called when updating the selected track to determine whether a candidate track can be selected.
     *
     * @param format The [Format] of the candidate track.
     * @param trackBitrate The estimated bitrate of the track. May differ from [Format.bitrate]
     * if a more accurate estimate of the current track bitrate is available.
     * @param playbackSpeed The current playback speed.
     * @param effectiveBitrate The bitrate available to this selection.
     * @return Whether this [Format] can be selected.
     *//*

    protected fun canSelectFormat(
        format: Format?, trackBitrate: Int, playbackSpeed: Float, effectiveBitrate: Long
    ): Boolean {
        return Math.round(trackBitrate * playbackSpeed) <= effectiveBitrate
    }

    */
/**
     * Called from [.evaluateQueueSize] to determine whether an evaluation should be
     * performed.
     *
     * @param nowMs The current value of [Clock.elapsedRealtime].
     * @return Whether an evaluation should be performed.
     *//*

    protected fun shouldEvaluateQueueSize(nowMs: Long): Boolean {
        return (lastBufferEvaluationMs == C.TIME_UNSET
                || nowMs - lastBufferEvaluationMs >= minTimeBetweenBufferReevaluationMs)
    }

    */
/**
     * Computes the ideal selected index ignoring buffer health.
     *
     * @param nowMs The current time in the timebase of [Clock.elapsedRealtime], or [     ][Long.MIN_VALUE] to ignore blacklisting.
     *//*

    private fun determineIdealSelectedIndex(nowMs: Long): Int {
        val effectiveBitrate = bandwidthProvider.allocatedBandwidth
        var lowestBitrateNonBlacklistedIndex = 0
        for (i in 0 until length) {
            if (nowMs == Long.MIN_VALUE || !isBlacklisted(i, nowMs)) {
                val format = getFormat(i)
                lowestBitrateNonBlacklistedIndex =
                    if (canSelectFormat(format, format.bitrate, playbackSpeed, effectiveBitrate)) {
                        return i
                    } else {
                        i
                    }
            }
        }
        return lowestBitrateNonBlacklistedIndex
    }

    private fun minDurationForQualityIncreaseUs(availableDurationUs: Long): Long {
        val isAvailableDurationTooShort = (availableDurationUs != C.TIME_UNSET
                && availableDurationUs <= minDurationForQualityIncreaseUs)
        return if (isAvailableDurationTooShort) (availableDurationUs * bufferedFractionToLiveEdgeForQualityIncrease).toLong() else minDurationForQualityIncreaseUs
    }

    */
/** Provides the allocated bandwidth.  *//*

    private interface BandwidthProvider {
        */
/** Returns the allocated bitrate.  *//*

        val allocatedBandwidth: Long
    }

    private class DefaultBandwidthProvider  */
/* package *//*
 // the constructor does not initialize fields: allocationCheckpoints
    internal constructor(
        private val bandwidthMeter: BandwidthMeter?,
        private val bandwidthFraction: Float,
        private val reservedBandwidth: Long
    ) :
        BandwidthProvider {
        private var allocationCheckpoints: Array<LongArray>?

        // unboxing a possibly-null reference allocationCheckpoints[nextIndex][0]
        override val allocatedBandwidth: Long
            get() {
                val totalBandwidth = (bandwidthMeter!!.bitrateEstimate * bandwidthFraction).toLong()
                val allocatableBandwidth = Math.max(0L, totalBandwidth - reservedBandwidth)
                if (allocationCheckpoints == null) {
                    return allocatableBandwidth
                }
                var nextIndex = 1
                while (nextIndex < allocationCheckpoints!!.size - 1
                    && allocationCheckpoints!![nextIndex][0] < allocatableBandwidth
                ) {
                    nextIndex++
                }
                val previous = allocationCheckpoints!![nextIndex - 1]
                val next = allocationCheckpoints!![nextIndex]
                val fractionBetweenCheckpoints =
                    (allocatableBandwidth - previous[0]).toFloat() / (next[0] - previous[0])
                return previous[1] + (fractionBetweenCheckpoints * (next[1] - previous[1])).toLong()
            }

        */
/* package *//*

        fun experimental_setBandwidthAllocationCheckpoints(
            allocationCheckpoints: Array<LongArray>
        ) {
            Assertions.checkArgument(allocationCheckpoints.size >= 2)
            this.allocationCheckpoints = allocationCheckpoints
        }
    }

    companion object {
        const val DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10000
        const val DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25000
        const val DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25000
        const val DEFAULT_BANDWIDTH_FRACTION = 0.7f
        const val DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE = 0.75f
        const val DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS: Long = 2000

        */
/**
         * Returns allocation checkpoints for allocating bandwidth between multiple adaptive track
         * selections.
         *
         * @param trackBitrates Array of [selectionIndex][trackIndex] -> trackBitrate.
         * @return Array of allocation checkpoints [selectionIndex][checkpointIndex][2] with [0]=total
         * bandwidth at checkpoint and [1]=allocated bandwidth at checkpoint.
         *//*

        private fun getAllocationCheckpoints(trackBitrates: Array<LongArray>): Array<Array<LongArray>> {
            // Algorithm:
            //  1. Use log bitrates to treat all resolution update steps equally.
            //  2. Distribute switch points for each selection equally in the same [0.0-1.0] range.
            //  3. Switch up one format at a time in the order of the switch points.
            val logBitrates: Array<DoubleArray> =
                AdaptiveTrackSelection.Companion.getLogArrayValues(trackBitrates)
            val switchPoints: Array<DoubleArray> =
                AdaptiveTrackSelection.Companion.getSwitchPoints(logBitrates)

            // There will be (count(switch point) + 3) checkpoints:
            // [0] = all zero, [1] = minimum bitrates, [2-(end-1)] = up-switch points,
            // [end] = extra point to set slope for additional bitrate.
            val checkpointCount: Int =
                AdaptiveTrackSelection.Companion.countArrayElements(switchPoints) + 3
            val checkpoints = Array(logBitrates.size) {
                Array(checkpointCount) {
                    LongArray(2)
                }
            }
            val currentSelection = IntArray(logBitrates.size)
            AdaptiveTrackSelection.Companion.setCheckpointValues(
                checkpoints,  */
/* checkpointIndex= *//*

                1,
                trackBitrates,
                currentSelection
            )
            for (checkpointIndex in 2 until checkpointCount - 1) {
                var nextUpdateIndex = 0
                var nextUpdateSwitchPoint = Double.MAX_VALUE
                for (i in logBitrates.indices) {
                    if (currentSelection[i] + 1 == logBitrates[i].length) {
                        continue
                    }
                    val switchPoint = switchPoints[i][currentSelection[i]]
                    if (switchPoint < nextUpdateSwitchPoint) {
                        nextUpdateSwitchPoint = switchPoint
                        nextUpdateIndex = i
                    }
                }
                currentSelection[nextUpdateIndex]++
                AdaptiveTrackSelection.Companion.setCheckpointValues(
                    checkpoints,
                    checkpointIndex,
                    trackBitrates,
                    currentSelection
                )
            }
            for (points in checkpoints) {
                points[checkpointCount - 1][0] = 2 * points[checkpointCount - 2][0]
                points[checkpointCount - 1][1] = 2 * points[checkpointCount - 2][1]
            }
            return checkpoints
        }

        */
/** Converts all input values to Math.log(value).  *//*

        private fun getLogArrayValues(values: Array<LongArray>): Array<DoubleArray?> {
            val logValues = arrayOfNulls<DoubleArray>(values.size)
            for (i in values.indices) {
                logValues[i] = DoubleArray(values[i].length)
                for (j in 0 until values[i].length) {
                    logValues[i]!![j] = if (values[i][j] == Format.NO_VALUE) 0 else Math.log(
                        values[i][j].toDouble()
                    )
                }
            }
            return logValues
        }

        */
/**
         * Returns idealized switch points for each switch between consecutive track selection bitrates.
         *
         * @param logBitrates Log bitrates with [selectionCount][formatCount].
         * @return Linearly distributed switch points in the range of [0.0-1.0].
         *//*

        private fun getSwitchPoints(logBitrates: Array<DoubleArray>): Array<DoubleArray?> {
            val switchPoints = arrayOfNulls<DoubleArray>(logBitrates.size)
            for (i in logBitrates.indices) {
                switchPoints[i] = DoubleArray(logBitrates[i].length - 1)
                if (switchPoints[i].length == 0) {
                    continue
                }
                val totalBitrateDiff = logBitrates[i][logBitrates[i].length - 1] - logBitrates[i][0]
                for (j in 0 until logBitrates[i].length - 1) {
                    val switchBitrate = 0.5 * (logBitrates[i][j] + logBitrates[i][j + 1])
                    switchPoints[i]!![j] =
                        if (totalBitrateDiff == 0.0) 1.0 else (switchBitrate - logBitrates[i][0]) / totalBitrateDiff
                }
            }
            return switchPoints
        }

        */
/** Returns total number of elements in a 2D array.  *//*

        private fun countArrayElements(array: Array<DoubleArray>): Int {
            var count = 0
            for (subArray in array) {
                count += subArray.size
            }
            return count
        }

        */
/**
         * Sets checkpoint bitrates.
         *
         * @param checkpoints Output checkpoints with [selectionIndex][checkpointIndex][2] where [0]=Total
         * bitrate and [1]=Allocated bitrate.
         * @param checkpointIndex The checkpoint index.
         * @param trackBitrates The track bitrates with [selectionIndex][trackIndex].
         * @param selectedTracks The indices of selected tracks for each selection for this checkpoint.
         *//*

        private fun setCheckpointValues(
            checkpoints: Array<Array<LongArray>>,
            checkpointIndex: Int,
            trackBitrates: Array<LongArray>,
            selectedTracks: IntArray
        ) {
            var totalBitrate: Long = 0
            for (i in checkpoints.indices) {
                checkpoints[i][checkpointIndex][1] = trackBitrates[i][selectedTracks[i]]
                totalBitrate += checkpoints[i][checkpointIndex][1]
            }
            for (points in checkpoints) {
                points[checkpointIndex][0] = totalBitrate
            }
        }
    }

    init {
        minDurationForQualityIncreaseUs = minDurationForQualityIncreaseMs * 1000L
        maxDurationForQualityDecreaseUs = maxDurationForQualityDecreaseMs * 1000L
        minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardMs * 1000L
        this.bufferedFractionToLiveEdgeForQualityIncrease =
            bufferedFractionToLiveEdgeForQualityIncrease
        this.minTimeBetweenBufferReevaluationMs = minTimeBetweenBufferReevaluationMs
        this.clock = clock
        playbackSpeed = 1f
        reason = C.SELECTION_REASON_UNKNOWN
        lastBufferEvaluationMs = C.TIME_UNSET
    }
}*/
