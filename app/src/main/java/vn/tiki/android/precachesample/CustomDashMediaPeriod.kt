/*
package vn.tiki.android.precachesample

import android.util.Pair
import android.util.SparseIntArray
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.FormatHolder
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.decoder.DecoderInputBuffer
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.metadata.emsg.EventMessageEncoder
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream.EmbeddedSampleStream
import com.google.android.exoplayer2.source.dash.DashChunkSource
import com.google.android.exoplayer2.source.dash.DashMediaPeriod
import com.google.android.exoplayer2.source.dash.DashMediaPeriod.TrackGroupInfo
import com.google.android.exoplayer2.source.dash.DashMediaPeriod.TrackGroupInfo.TrackGroupCategory
import com.google.android.exoplayer2.source.dash.EventSampleStream
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler
import com.google.android.exoplayer2.source.dash.manifest.*
import com.google.android.exoplayer2.trackselection.TrackSelection
import com.google.android.exoplayer2.upstream.Allocator
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoaderErrorThrower
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.ArrayList

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


*/
/** A DASH [MediaPeriod].  *//*
 */
/* package *//*

internal class DashMediaPeriod(
    */
/* package *//*

    val id: Int,
    private var manifest: DashManifest,
    private var periodIndex: Int,
    private val chunkSourceFactory: DashChunkSource.Factory,
    private val transferListener: TransferListener?,
    private val drmSessionManager: DrmSessionManager<*>,
    private val loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    private val eventDispatcher: MediaSourceEventListener.EventDispatcher,
    private val elapsedRealtimeOffsetMs: Long,
    private val manifestLoaderErrorThrower: LoaderErrorThrower,
    private val allocator: Allocator,
    private val compositeSequenceableLoaderFactory: CompositeSequenceableLoaderFactory,
    playerEmsgCallback: PlayerEmsgCallback?
) : MediaPeriod,
    SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource?>?>,
    ChunkSampleStream.ReleaseCallback<DashChunkSource> {
    private val trackGroups: TrackGroupArray
    private val trackGroupInfos: Array<TrackGroupInfo>
    private val playerEmsgHandler: PlayerEmsgHandler
    private val trackEmsgHandlerBySampleStream: IdentityHashMap<ChunkSampleStream<DashChunkSource>, PlayerTrackEmsgHandler?>
    private var callback: MediaPeriod.Callback? = null
    private var sampleStreams: Array<ChunkSampleStream<DashChunkSource>>?
    private var eventSampleStreams: Array<EventSampleStream?>
    private var compositeSequenceableLoader: SequenceableLoader
    private var eventStreams: List<EventStream>
    private var notifiedReadingStarted = false

    */
/**
     * Updates the [DashManifest] and the index of this period in the manifest.
     *
     * @param manifest The updated manifest.
     * @param periodIndex the new index of this period in the updated manifest.
     *//*

    fun updateManifest(manifest: DashManifest, periodIndex: Int) {
        this.manifest = manifest
        this.periodIndex = periodIndex
        playerEmsgHandler.updateManifest(manifest)
        if (sampleStreams != null) {
            for (sampleStream: ChunkSampleStream<DashChunkSource> in sampleStreams!!) {
                sampleStream.chunkSource.updateManifest(manifest, periodIndex)
            }
            callback!!.onContinueLoadingRequested(this)
        }
        eventStreams = manifest.getPeriod(periodIndex).eventStreams
        for (eventSampleStream: EventSampleStream? in eventSampleStreams) {
            for (eventStream: EventStream in eventStreams) {
                if (eventStream.id() == eventSampleStream!!.eventStreamId()) {
                    val lastPeriodIndex = manifest.periodCount - 1
                    eventSampleStream.updateEventStream(
                        eventStream,  */
/* eventStreamAppendable= *//*

                        manifest.dynamic && periodIndex == lastPeriodIndex
                    )
                    break
                }
            }
        }
    }

    fun release() {
        playerEmsgHandler.release()
        for (sampleStream: ChunkSampleStream<DashChunkSource> in sampleStreams!!) {
            sampleStream.release(this)
        }
        callback = null
        eventDispatcher.mediaPeriodReleased()
    }

    // ChunkSampleStream.ReleaseCallback implementation.
    @Synchronized
    override fun onSampleStreamReleased(stream: ChunkSampleStream<DashChunkSource>) {
        val trackEmsgHandler = trackEmsgHandlerBySampleStream.remove(stream)
        trackEmsgHandler?.release()
    }

    // MediaPeriod implementation.
    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        this.callback = callback
        callback.onPrepared(this)
    }

    @Throws(IOException::class)
    override fun maybeThrowPrepareError() {
        manifestLoaderErrorThrower.maybeThrowError()
    }

    override fun getTrackGroups(): TrackGroupArray {
        return trackGroups
    }

    override fun getStreamKeys(trackSelections: List<TrackSelection>): List<StreamKey> {
        val manifestAdaptationSets = manifest.getPeriod(
            periodIndex
        ).adaptationSets
        val streamKeys: MutableList<StreamKey> = ArrayList()
        for (trackSelection: TrackSelection in trackSelections) {
            val trackGroupIndex = trackGroups.indexOf(trackSelection.trackGroup)
            val trackGroupInfo = trackGroupInfos[trackGroupIndex]
            if (trackGroupInfo.trackGroupCategory != TrackGroupInfo.CATEGORY_PRIMARY) {
                // Ignore non-primary tracks.
                continue
            }
            val adaptationSetIndices = trackGroupInfo.adaptationSetIndices
            val trackIndices = IntArray(trackSelection.length())
            for (i in 0 until trackSelection.length()) {
                trackIndices[i] = trackSelection.getIndexInTrackGroup(i)
            }
            Arrays.sort(trackIndices)
            var currentAdaptationSetIndex = 0
            var totalTracksInPreviousAdaptationSets = 0
            var tracksInCurrentAdaptationSet =
                manifestAdaptationSets[adaptationSetIndices[0]].representations.size
            for (trackIndex: Int in trackIndices) {
                while (trackIndex >= totalTracksInPreviousAdaptationSets + tracksInCurrentAdaptationSet) {
                    currentAdaptationSetIndex++
                    totalTracksInPreviousAdaptationSets += tracksInCurrentAdaptationSet
                    tracksInCurrentAdaptationSet =
                        manifestAdaptationSets[adaptationSetIndices[currentAdaptationSetIndex]].representations
                            .size
                }
                streamKeys.add(
                    StreamKey(
                        periodIndex,
                        adaptationSetIndices[currentAdaptationSetIndex],
                        trackIndex - totalTracksInPreviousAdaptationSets
                    )
                )
            }
        }
        return streamKeys
    }

    override fun selectTracks(
        @NullableType selections: Array<TrackSelection>,
        mayRetainStreamFlags: BooleanArray,
        @NullableType streams: Array<SampleStream>,
        streamResetFlags: BooleanArray,
        positionUs: Long
    ): Long {
        val streamIndexToTrackGroupIndex = getStreamIndexToTrackGroupIndex(selections)
        releaseDisabledStreams(selections, mayRetainStreamFlags, streams)
        releaseOrphanEmbeddedStreams(selections, streams, streamIndexToTrackGroupIndex)
        selectNewStreams(
            selections, streams, streamResetFlags, positionUs, streamIndexToTrackGroupIndex
        )
        val sampleStreamList = ArrayList<ChunkSampleStream<DashChunkSource>>()
        val eventSampleStreamList = ArrayList<EventSampleStream>()
        for (sampleStream: SampleStream? in streams) {
            if (sampleStream is ChunkSampleStream<*>) {
                val stream = sampleStream as ChunkSampleStream<DashChunkSource>
                sampleStreamList.add(stream)
            } else if (sampleStream is EventSampleStream) {
                eventSampleStreamList.add(sampleStream)
            }
        }
        sampleStreams = DashMediaPeriod.Companion.newSampleStreamArray(sampleStreamList.size)
        sampleStreamList.toArray(sampleStreams)
        eventSampleStreams = arrayOfNulls(eventSampleStreamList.size)
        eventSampleStreamList.toArray(eventSampleStreams)
        compositeSequenceableLoader =
            compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(*sampleStreams)
        return positionUs
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
        for (sampleStream: ChunkSampleStream<DashChunkSource> in sampleStreams!!) {
            sampleStream.discardBuffer(positionUs, toKeyframe)
        }
    }

    override fun reevaluateBuffer(positionUs: Long) {
        compositeSequenceableLoader.reevaluateBuffer(positionUs)
    }

    override fun continueLoading(positionUs: Long): Boolean {
        return compositeSequenceableLoader.continueLoading(positionUs)
    }

    override fun isLoading(): Boolean {
        return compositeSequenceableLoader.isLoading
    }

    override fun getNextLoadPositionUs(): Long {
        return compositeSequenceableLoader.nextLoadPositionUs
    }

    override fun readDiscontinuity(): Long {
        if (!notifiedReadingStarted) {
            eventDispatcher.readingStarted()
            notifiedReadingStarted = true
        }
        return C.TIME_UNSET
    }

    override fun getBufferedPositionUs(): Long {
        return compositeSequenceableLoader.bufferedPositionUs
    }

    override fun seekToUs(positionUs: Long): Long {
        for (sampleStream: ChunkSampleStream<DashChunkSource> in sampleStreams!!) {
            sampleStream.seekToUs(positionUs)
        }
        for (sampleStream: EventSampleStream? in eventSampleStreams) {
            sampleStream!!.seekToUs(positionUs)
        }
        return positionUs
    }

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long {
        for (sampleStream: ChunkSampleStream<DashChunkSource> in sampleStreams!!) {
            if (sampleStream.primaryTrackType == C.TRACK_TYPE_VIDEO) {
                return sampleStream.getAdjustedSeekPositionUs(positionUs, seekParameters)
            }
        }
        return positionUs
    }

    // SequenceableLoader.Callback implementation.
    override fun onContinueLoadingRequested(sampleStream: ChunkSampleStream<DashChunkSource?>?) {
        callback!!.onContinueLoadingRequested(this)
    }

    // Internal methods.
    private fun getStreamIndexToTrackGroupIndex(selections: Array<TrackSelection>): IntArray {
        val streamIndexToTrackGroupIndex = IntArray(selections.size)
        for (i in selections.indices) {
            if (selections[i] != null) {
                streamIndexToTrackGroupIndex[i] = trackGroups.indexOf(selections[i].trackGroup)
            } else {
                streamIndexToTrackGroupIndex[i] = C.INDEX_UNSET
            }
        }
        return streamIndexToTrackGroupIndex
    }

    private fun releaseDisabledStreams(
        selections: Array<TrackSelection>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>
    ) {
        for (i in selections.indices) {
            if (selections[i] == null || !mayRetainStreamFlags[i]) {
                if (streams[i] is ChunkSampleStream<*>) {
                    val stream = streams[i] as ChunkSampleStream<DashChunkSource>?
                    stream!!.release(this)
                } else if (streams[i] is EmbeddedSampleStream) {
                    (streams[i] as EmbeddedSampleStream?)!!.release()
                }
                streams[i] = null
            }
        }
    }

    private fun releaseOrphanEmbeddedStreams(
        selections: Array<TrackSelection>,
        streams: Array<SampleStream?>,
        streamIndexToTrackGroupIndex: IntArray
    ) {
        for (i in selections.indices) {
            if (streams[i] is EmptySampleStream || streams[i] is EmbeddedSampleStream) {
                // We need to release an embedded stream if the corresponding primary stream is released.
                val primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex)
                var mayRetainStream: Boolean
                if (primaryStreamIndex == C.INDEX_UNSET) {
                    // If the corresponding primary stream is not selected, we may retain an existing
                    // EmptySampleStream.
                    mayRetainStream = streams[i] is EmptySampleStream
                } else {
                    // If the corresponding primary stream is selected, we may retain the embedded stream if
                    // the stream's parent still matches.
                    mayRetainStream = (streams[i] is EmbeddedSampleStream
                            && (streams[i] as EmbeddedSampleStream?)!!.parent === streams[primaryStreamIndex])
                }
                if (!mayRetainStream) {
                    if (streams[i] is EmbeddedSampleStream) {
                        (streams[i] as EmbeddedSampleStream?)!!.release()
                    }
                    streams[i] = null
                }
            }
        }
    }

    private fun selectNewStreams(
        selections: Array<TrackSelection>,
        streams: Array<SampleStream>,
        streamResetFlags: BooleanArray,
        positionUs: Long,
        streamIndexToTrackGroupIndex: IntArray
    ) {
        // Create newly selected primary and event streams.
        for (i in selections.indices) {
            val selection: TrackSelection = selections.get(i) ?: continue
            if (streams[i] == null) {
                // Create new stream for selection.
                streamResetFlags[i] = true
                val trackGroupIndex = streamIndexToTrackGroupIndex[i]
                val trackGroupInfo = trackGroupInfos[trackGroupIndex]
                if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_PRIMARY) {
                    streams[i] = buildSampleStream(trackGroupInfo, selection, positionUs)
                } else if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_MANIFEST_EVENTS) {
                    val eventStream = eventStreams[trackGroupInfo.eventStreamGroupIndex]
                    val format = selection.trackGroup.getFormat(0)
                    streams[i] = EventSampleStream(eventStream, format, manifest.dynamic)
                }
            } else if (streams[i] is ChunkSampleStream<*>) {
                // Update selection in existing stream.
                val stream = streams[i] as ChunkSampleStream<DashChunkSource>
                stream.chunkSource.updateTrackSelection(selection)
            }
        }
        // Create newly selected embedded streams from the corresponding primary stream. Note that this
        // second pass is needed because the primary stream may not have been created yet in a first
        // pass if the index of the primary stream is greater than the index of the embedded stream.
        for (i in selections.indices) {
            if (streams[i] == null && selections[i] != null) {
                val trackGroupIndex = streamIndexToTrackGroupIndex[i]
                val trackGroupInfo = trackGroupInfos[trackGroupIndex]
                if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_EMBEDDED) {
                    val primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex)
                    if (primaryStreamIndex == C.INDEX_UNSET) {
                        // If an embedded track is selected without the corresponding primary track, create an
                        // empty sample stream instead.
                        streams[i] = EmptySampleStream()
                    } else {
                        streams[i] = (streams[primaryStreamIndex] as ChunkSampleStream<*>)
                            .selectEmbeddedTrack(positionUs, trackGroupInfo.trackType)
                    }
                }
            }
        }
    }

    private fun getPrimaryStreamIndex(
        embeddedStreamIndex: Int,
        streamIndexToTrackGroupIndex: IntArray
    ): Int {
        val embeddedTrackGroupIndex = streamIndexToTrackGroupIndex[embeddedStreamIndex]
        if (embeddedTrackGroupIndex == C.INDEX_UNSET) {
            return C.INDEX_UNSET
        }
        val primaryTrackGroupIndex = trackGroupInfos[embeddedTrackGroupIndex].primaryTrackGroupIndex
        for (i in streamIndexToTrackGroupIndex.indices) {
            val trackGroupIndex = streamIndexToTrackGroupIndex[i]
            if (trackGroupIndex == primaryTrackGroupIndex
                && trackGroupInfos[trackGroupIndex].trackGroupCategory
                == TrackGroupInfo.CATEGORY_PRIMARY
            ) {
                return i
            }
        }
        return C.INDEX_UNSET
    }

    private fun buildSampleStream(
        trackGroupInfo: TrackGroupInfo,
        selection: TrackSelection, positionUs: Long
    ): ChunkSampleStream<DashChunkSource> {
        var embeddedTrackCount = 0
        val enableEventMessageTrack =
            trackGroupInfo.embeddedEventMessageTrackGroupIndex != C.INDEX_UNSET
        var embeddedEventMessageTrackGroup: TrackGroup? = null
        if (enableEventMessageTrack) {
            embeddedEventMessageTrackGroup =
                trackGroups[trackGroupInfo.embeddedEventMessageTrackGroupIndex]
            embeddedTrackCount++
        }
        val enableCea608Tracks = trackGroupInfo.embeddedCea608TrackGroupIndex != C.INDEX_UNSET
        var embeddedCea608TrackGroup: TrackGroup? = null
        if (enableCea608Tracks) {
            embeddedCea608TrackGroup = trackGroups[trackGroupInfo.embeddedCea608TrackGroupIndex]
            embeddedTrackCount += embeddedCea608TrackGroup.length
        }
        val embeddedTrackFormats = arrayOfNulls<Format>(embeddedTrackCount)
        val embeddedTrackTypes = IntArray(embeddedTrackCount)
        embeddedTrackCount = 0
        if (enableEventMessageTrack) {
            embeddedTrackFormats[embeddedTrackCount] = embeddedEventMessageTrackGroup!!.getFormat(0)
            embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_METADATA
            embeddedTrackCount++
        }
        val embeddedCea608TrackFormats: MutableList<Format?> = ArrayList()
        if (enableCea608Tracks) {
            for (i in 0 until embeddedCea608TrackGroup!!.length) {
                embeddedTrackFormats[embeddedTrackCount] = embeddedCea608TrackGroup.getFormat(i)
                embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_TEXT
                embeddedCea608TrackFormats.add(embeddedTrackFormats[embeddedTrackCount])
                embeddedTrackCount++
            }
        }
        val trackPlayerEmsgHandler =
            if (manifest.dynamic && enableEventMessageTrack) playerEmsgHandler.newPlayerTrackEmsgHandler() else null
        val chunkSource = chunkSourceFactory.createDashChunkSource(
            manifestLoaderErrorThrower,
            manifest,
            periodIndex,
            trackGroupInfo.adaptationSetIndices,
            selection,
            trackGroupInfo.trackType,
            elapsedRealtimeOffsetMs,
            enableEventMessageTrack,
            embeddedCea608TrackFormats,
            trackPlayerEmsgHandler,
            transferListener
        )
        val stream = ChunkSampleStream(
            trackGroupInfo.trackType,
            embeddedTrackTypes,
            embeddedTrackFormats,
            chunkSource,
            this,
            allocator,
            positionUs,
            drmSessionManager,
            loadErrorHandlingPolicy,
            eventDispatcher
        )
        synchronized(this) {
            // The map is also accessed on the loading thread so synchronize access.
            trackEmsgHandlerBySampleStream.put(stream, trackPlayerEmsgHandler)
        }
        return stream
    }

    private class TrackGroupInfo private constructor(
        val trackType: Int,
        @field:TrackGroupCategory @param:TrackGroupCategory val trackGroupCategory: Int,
        val adaptationSetIndices: IntArray,
        val primaryTrackGroupIndex: Int,
        val embeddedEventMessageTrackGroupIndex: Int,
        val embeddedCea608TrackGroupIndex: Int,
        val eventStreamGroupIndex: Int
    ) {
        @Documented
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(
            CATEGORY_PRIMARY, CATEGORY_EMBEDDED, CATEGORY_MANIFEST_EVENTS
        )
        annotation class TrackGroupCategory()
        companion object {
            */
/**
             * A normal track group that has its samples drawn from the stream.
             * For example: a video Track Group or an audio Track Group.
             *//*

            private const val CATEGORY_PRIMARY = 0

            */
/**
             * A track group whose samples are embedded within one of the primary streams. For example: an
             * EMSG track has its sample embedded in emsg atoms in one of the primary streams.
             *//*

            private const val CATEGORY_EMBEDDED = 1

            */
/**
             * A track group that has its samples listed explicitly in the DASH manifest file.
             * For example: an EventStream track has its sample (Events) included directly in the DASH
             * manifest file.
             *//*

            private const val CATEGORY_MANIFEST_EVENTS = 2
            fun primaryTrack(
                trackType: Int,
                adaptationSetIndices: IntArray,
                primaryTrackGroupIndex: Int,
                embeddedEventMessageTrackGroupIndex: Int,
                embeddedCea608TrackGroupIndex: Int
            ): TrackGroupInfo {
                return TrackGroupInfo(
                    trackType,
                    CATEGORY_PRIMARY,
                    adaptationSetIndices,
                    primaryTrackGroupIndex,
                    embeddedEventMessageTrackGroupIndex,
                    embeddedCea608TrackGroupIndex,  */
/* eventStreamGroupIndex= *//*

                    -1
                )
            }

            fun embeddedEmsgTrack(
                adaptationSetIndices: IntArray,
                primaryTrackGroupIndex: Int
            ): TrackGroupInfo {
                return TrackGroupInfo(
                    C.TRACK_TYPE_METADATA,
                    CATEGORY_EMBEDDED,
                    adaptationSetIndices,
                    primaryTrackGroupIndex,
                    C.INDEX_UNSET,
                    C.INDEX_UNSET,  */
/* eventStreamGroupIndex= *//*

                    -1
                )
            }

            fun embeddedCea608Track(
                adaptationSetIndices: IntArray,
                primaryTrackGroupIndex: Int
            ): TrackGroupInfo {
                return TrackGroupInfo(
                    C.TRACK_TYPE_TEXT,
                    CATEGORY_EMBEDDED,
                    adaptationSetIndices,
                    primaryTrackGroupIndex,
                    C.INDEX_UNSET,
                    C.INDEX_UNSET,  */
/* eventStreamGroupIndex= *//*

                    -1
                )
            }

            fun mpdEventTrack(eventStreamIndex: Int): TrackGroupInfo {
                return TrackGroupInfo(
                    C.TRACK_TYPE_METADATA,
                    CATEGORY_MANIFEST_EVENTS, IntArray(0),  */
/* primaryTrackGroupIndex= *//*

                    -1,
                    C.INDEX_UNSET,
                    C.INDEX_UNSET,
                    eventStreamIndex
                )
            }
        }
    }

    companion object {
        private val CEA608_SERVICE_DESCRIPTOR_REGEX = Pattern.compile("CC([1-4])=(.+)")
        private fun buildTrackGroups(
            drmSessionManager: DrmSessionManager<*>,
            adaptationSets: List<AdaptationSet>,
            eventStreams: List<EventStream>
        ): Pair<TrackGroupArray, Array<TrackGroupInfo>> {
            val groupedAdaptationSetIndices: Array<IntArray> = getGroupedAdaptationSetIndices(adaptationSets)?.apply {
                if (this.size == 0) return emptyArray<IntArray>()

            }
            val primaryGroupCount = groupedAdaptationSetIndices.size
            val primaryGroupHasEventMessageTrackFlags = BooleanArray(primaryGroupCount)
            val primaryGroupCea608TrackFormats: Array<Array<Format>?> =
                arrayOfNulls(primaryGroupCount)
            val totalEmbeddedTrackGroupCount: Int =
                identifyEmbeddedTracks(
                    primaryGroupCount,
                    adaptationSets,
                    groupedAdaptationSetIndices,
                    primaryGroupHasEventMessageTrackFlags,
                    primaryGroupCea608TrackFormats
                )
            val totalGroupCount =
                primaryGroupCount + totalEmbeddedTrackGroupCount + eventStreams.size
            val trackGroups = arrayOfNulls<TrackGroup>(totalGroupCount)
            val trackGroupInfos = arrayOfNulls<TrackGroupInfo>(totalGroupCount)
            val trackGroupCount: Int =
                buildPrimaryAndEmbeddedTrackGroupInfos(
                    drmSessionManager,
                    adaptationSets,
                    groupedAdaptationSetIndices,
                    primaryGroupCount,
                    primaryGroupHasEventMessageTrackFlags,
                    primaryGroupCea608TrackFormats,
                    trackGroups,
                    trackGroupInfos
                )
            buildManifestEventTrackGroupInfos(
                eventStreams,
                trackGroups,
                trackGroupInfos,
                trackGroupCount
            )
            return Pair.create(TrackGroupArray(*trackGroups), trackGroupInfos)
        }

        private fun getGroupedAdaptationSetIndices(adaptationSets: List<AdaptationSet>): Array<IntArray?> {
            val adaptationSetCount = adaptationSets.size
            val idToIndexMap = SparseIntArray(adaptationSetCount)
            for (i in 0 until adaptationSetCount) {
                idToIndexMap.put(adaptationSets[i].id, i)
            }
            val groupedAdaptationSetIndices = arrayOfNulls<IntArray>(adaptationSetCount)
            val adaptationSetUsedFlags = BooleanArray(adaptationSetCount)
            var groupCount = 0
            for (i in 0 until adaptationSetCount) {
                if (adaptationSetUsedFlags[i]) {
                    // This adaptation set has already been included in a group.
                    continue
                }
                adaptationSetUsedFlags[i] = true
                val adaptationSetSwitchingProperty: Descriptor? =
                    findAdaptationSetSwitchingProperty(
                        adaptationSets[i].supplementalProperties
                    )
                if (adaptationSetSwitchingProperty == null) {
                    groupedAdaptationSetIndices[groupCount++] = intArrayOf(i)
                } else {
                    val extraAdaptationSetIds =
                        Util.split(adaptationSetSwitchingProperty.value!!, ",")
                    var adaptationSetIndices = IntArray(1 + extraAdaptationSetIds.size)
                    adaptationSetIndices[0] = i
                    var outputIndex = 1
                    for (adaptationSetId: String in extraAdaptationSetIds) {
                        val extraIndex = idToIndexMap[adaptationSetId.toInt(), -1]
                        if (extraIndex != -1) {
                            adaptationSetUsedFlags[extraIndex] = true
                            adaptationSetIndices[outputIndex] = extraIndex
                            outputIndex++
                        }
                    }
                    if (outputIndex < adaptationSetIndices.size) {
                        adaptationSetIndices = Arrays.copyOf(adaptationSetIndices, outputIndex)
                    }
                    groupedAdaptationSetIndices[groupCount++] = adaptationSetIndices
                }
            }
            return if (groupCount < adaptationSetCount) Arrays.copyOf(
                groupedAdaptationSetIndices,
                groupCount
            ) else groupedAdaptationSetIndices
        }

        */
/**
         * Iterates through list of primary track groups and identifies embedded tracks.
         *
         * @param primaryGroupCount The number of primary track groups.
         * @param adaptationSets The list of [AdaptationSet] of the current DASH period.
         * @param groupedAdaptationSetIndices The indices of [AdaptationSet] that belongs to the
         * same primary group, grouped in primary track groups order.
         * @param primaryGroupHasEventMessageTrackFlags An output array to be filled with flags indicating
         * whether each of the primary track groups contains an embedded event message track.
         * @param primaryGroupCea608TrackFormats An output array to be filled with track formats for
         * CEA-608 tracks embedded in each of the primary track groups.
         * @return Total number of embedded track groups.
         *//*

        private fun identifyEmbeddedTracks(
            primaryGroupCount: Int,
            adaptationSets: List<AdaptationSet>,
            groupedAdaptationSetIndices: Array<IntArray>,
            primaryGroupHasEventMessageTrackFlags: BooleanArray,
            primaryGroupCea608TrackFormats: Array<Array<Format>>
        ): Int {
            var numEmbeddedTrackGroups = 0
            for (i in 0 until primaryGroupCount) {
                if (hasEventMessageTrack(
                        adaptationSets,
                        groupedAdaptationSetIndices[i]
                    )
                ) {
                    primaryGroupHasEventMessageTrackFlags[i] = true
                    numEmbeddedTrackGroups++
                }
                primaryGroupCea608TrackFormats[i] = getCea608TrackFormats(
                    adaptationSets,
                    groupedAdaptationSetIndices[i]
                )
                if (primaryGroupCea608TrackFormats[i].isNotEmpty()) {
                    numEmbeddedTrackGroups++
                }
            }
            return numEmbeddedTrackGroups
        }

        private fun buildPrimaryAndEmbeddedTrackGroupInfos(
            drmSessionManager: DrmSessionManager<*>,
            adaptationSets: List<AdaptationSet>,
            groupedAdaptationSetIndices: Array<IntArray>,
            primaryGroupCount: Int,
            primaryGroupHasEventMessageTrackFlags: BooleanArray,
            primaryGroupCea608TrackFormats: Array<Array<Format>>,
            trackGroups: Array<TrackGroup>,
            trackGroupInfos: Array<TrackGroupInfo>
        ): Int {
            var trackGroupCount = 0
            for (i in 0 until primaryGroupCount) {
                val adaptationSetIndices = groupedAdaptationSetIndices[i]
                val representations: MutableList<Representation> = ArrayList()
                for (adaptationSetIndex: Int in adaptationSetIndices) {
                    representations.addAll(adaptationSets[adaptationSetIndex].representations)
                }
                val formats = arrayOfNulls<Format>(representations.size)
                for (j in formats.indices) {
                    var format = representations[j].format
                    val drmInitData = format.drmInitData
                    if (drmInitData != null) {
                        format = format.copyWithExoMediaCryptoType(
                            drmSessionManager.getExoMediaCryptoType(drmInitData)
                        )
                    }
                    formats[j] = format
                }
                val firstAdaptationSet = adaptationSets[adaptationSetIndices[0]]
                val primaryTrackGroupIndex = trackGroupCount++
                val eventMessageTrackGroupIndex =
                    if (primaryGroupHasEventMessageTrackFlags[i]) trackGroupCount++ else C.INDEX_UNSET
                val cea608TrackGroupIndex =
                    if (primaryGroupCea608TrackFormats[i].isNotEmpty()) trackGroupCount++ else C.INDEX_UNSET
                trackGroups[primaryTrackGroupIndex] = TrackGroup(*formats)
                trackGroupInfos[primaryTrackGroupIndex] = TrackGroupInfo.primaryTrack(
                    firstAdaptationSet.type,
                    adaptationSetIndices,
                    primaryTrackGroupIndex,
                    eventMessageTrackGroupIndex,
                    cea608TrackGroupIndex
                )
                if (eventMessageTrackGroupIndex != C.INDEX_UNSET) {
                    val format = Format.createSampleFormat(
                        firstAdaptationSet.id.toString() + ":emsg",
                        MimeTypes.APPLICATION_EMSG, null, Format.NO_VALUE, null
                    )
                    trackGroups[eventMessageTrackGroupIndex] = TrackGroup(format)
                    trackGroupInfos[eventMessageTrackGroupIndex] = TrackGroupInfo.embeddedEmsgTrack(
                        adaptationSetIndices,
                        primaryTrackGroupIndex
                    )
                }
                if (cea608TrackGroupIndex != C.INDEX_UNSET) {
                    trackGroups[cea608TrackGroupIndex] =
                        TrackGroup(*primaryGroupCea608TrackFormats[i])
                    trackGroupInfos[cea608TrackGroupIndex] = TrackGroupInfo.embeddedCea608Track(
                        adaptationSetIndices,
                        primaryTrackGroupIndex
                    )
                }
            }
            return trackGroupCount
        }

        private fun buildManifestEventTrackGroupInfos(
            eventStreams: List<EventStream>,
            trackGroups: Array<TrackGroup>,
            trackGroupInfos: Array<TrackGroupInfo>,
            existingTrackGroupCount: Int
        ) {
            var existingTrackGroupCount = existingTrackGroupCount
            for (i in eventStreams.indices) {
                val eventStream = eventStreams[i]
                val format = Format.createSampleFormat(
                    eventStream.id(), MimeTypes.APPLICATION_EMSG, null,
                    Format.NO_VALUE, null
                )
                trackGroups[existingTrackGroupCount] = TrackGroup(format)
                trackGroupInfos[existingTrackGroupCount++] = TrackGroupInfo.mpdEventTrack(i)
            }
        }

        private fun findAdaptationSetSwitchingProperty(descriptors: List<Descriptor>): Descriptor? {
            for (i in descriptors.indices) {
                val descriptor = descriptors[i]
                if ("urn:mpeg:dash:adaptation-set-switching:2016" == descriptor.schemeIdUri) {
                    return descriptor
                }
            }
            return null
        }

        private fun hasEventMessageTrack(
            adaptationSets: List<AdaptationSet>,
            adaptationSetIndices: IntArray
        ): Boolean {
            for (i: Int in adaptationSetIndices) {
                val representations = adaptationSets[i].representations
                for (j in representations.indices) {
                    val representation = representations[j]
                    if (!representation.inbandEventStreams.isEmpty()) {
                        return true
                    }
                }
            }
            return false
        }

        private fun getCea608TrackFormats(
            adaptationSets: List<AdaptationSet>, adaptationSetIndices: IntArray
        ): Array<Format> {
            for (i: Int in adaptationSetIndices) {
                val adaptationSet = adaptationSets[i]
                val descriptors = adaptationSets[i].accessibilityDescriptors
                for (j in descriptors.indices) {
                    val descriptor = descriptors[j]
                    if ("urn:scte:dash:cc:cea-608:2015" == descriptor.schemeIdUri) {
                        val value = descriptor.value
                            ?: // There are embedded CEA-608 tracks, but service information is not declared.
                            return arrayOf(buildCea608TrackFormat(adaptationSet.id))
                        val services = Util.split(value, ";")
                        val formats = Array<Format>(services.size)
                        for (k in services.indices) {
                            val matcher: Matcher =
                                CEA608_SERVICE_DESCRIPTOR_REGEX.matcher(
                                    services[k]
                                )
                            if (!matcher.matches()) {
                                // If we can't parse service information for all services, assume a single track.
                                return arrayOf(buildCea608TrackFormat(adaptationSet.id))
                            }
                            formats[k] = buildCea608TrackFormat(
                                adaptationSet.id,  */
/* language= *//*

                                matcher.group(2), matcher.group(1).toInt()
                            )
                        }
                        return formats
                    }
                }
            }
            return emptyArray()
        }

        private fun buildCea608TrackFormat(
            adaptationSetId: Int,
            language: String? =  */
/* language= *//*
null,
            accessibilityChannel: Int =  */
/* accessibilityChannel= *//*
Format.NO_VALUE
        ): Format {
            return Format.createTextSampleFormat(
                adaptationSetId
                    .toString() + ":cea608"
                        + if (accessibilityChannel != Format.NO_VALUE) ":$accessibilityChannel" else "",
                MimeTypes.APPLICATION_CEA608,  */
/* codecs= *//*

                null,  */
/* bitrate= *//*

                Format.NO_VALUE,  */
/* selectionFlags= *//*

                0,
                language,
                accessibilityChannel,  */
/* drmInitData= *//*

                null,
                Format.OFFSET_SAMPLE_RELATIVE,  */
/* initializationData= *//*

                null
            )
        }

        // We won't assign the array to a variable that erases the generic type, and then write into it.
        private fun newSampleStreamArray(length: Int): Array<ChunkSampleStream<DashChunkSource>> {
            return arrayOfNulls<ChunkSampleStream<*>>(length)
        }
    }

    init {
        playerEmsgHandler = PlayerEmsgHandler(manifest, (playerEmsgCallback)!!, allocator)
        sampleStreams = newSampleStreamArray(0)
        eventSampleStreams = arrayOfNulls(0)
        trackEmsgHandlerBySampleStream = IdentityHashMap()
        compositeSequenceableLoader =
            compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(*sampleStreams)
        val period = manifest.getPeriod(
            periodIndex
        )
        eventStreams = period.eventStreams
        val result: Pair<TrackGroupArray, Array<TrackGroupInfo>> =
            buildTrackGroups(
                drmSessionManager, period.adaptationSets, eventStreams
            )
        trackGroups = result.first
        trackGroupInfos = result.second
        eventDispatcher.mediaPeriodCreated()
    }
}

internal class EventSampleStream(
    private var eventStream: EventStream,
    private val upstreamFormat: Format,
    eventStreamAppendable: Boolean
) :
    SampleStream {
    private val eventMessageEncoder: EventMessageEncoder
    private var eventTimesUs: LongArray
    private var eventStreamAppendable = false
    private var isFormatSentDownstream = false
    private var currentIndex = 0
    private var pendingSeekPositionUs: Long
    fun eventStreamId(): String {
        return eventStream.id()
    }

    fun updateEventStream(eventStream: EventStream, eventStreamAppendable: Boolean) {
        val lastReadPositionUs =
            if (currentIndex == 0) C.TIME_UNSET else eventTimesUs[currentIndex - 1]
        this.eventStreamAppendable = eventStreamAppendable
        this.eventStream = eventStream
        eventTimesUs = eventStream.presentationTimesUs
        if (pendingSeekPositionUs != C.TIME_UNSET) {
            seekToUs(pendingSeekPositionUs)
        } else if (lastReadPositionUs != C.TIME_UNSET) {
            currentIndex = Util.binarySearchCeil(
                eventTimesUs, lastReadPositionUs,  */
/* inclusive= *//*
false,  */
/* stayInBounds= *//*
false
            )
        }
    }

    */
/**
     * Seeks to the specified position in microseconds.
     *
     * @param positionUs The seek position in microseconds.
     *//*

    fun seekToUs(positionUs: Long) {
        currentIndex = Util.binarySearchCeil(
            eventTimesUs, positionUs,  */
/* inclusive= *//*
true,  */
/* stayInBounds= *//*
false
        )
        val isPendingSeek = eventStreamAppendable && currentIndex == eventTimesUs.size
        pendingSeekPositionUs = if (isPendingSeek) positionUs else C.TIME_UNSET
    }

    override fun isReady(): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun maybeThrowError() {
        // Do nothing.
    }

    override fun readData(
        formatHolder: FormatHolder, buffer: DecoderInputBuffer,
        formatRequired: Boolean
    ): Int {
        if (formatRequired || !isFormatSentDownstream) {
            formatHolder.format = upstreamFormat
            isFormatSentDownstream = true
            return C.RESULT_FORMAT_READ
        }
        if (currentIndex == eventTimesUs.size) {
            return if (!eventStreamAppendable) {
                buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                C.RESULT_BUFFER_READ
            } else {
                C.RESULT_NOTHING_READ
            }
        }
        val sampleIndex = currentIndex++
        val serializedEvent = eventMessageEncoder.encode(eventStream.events[sampleIndex])
        return if (serializedEvent != null) {
            buffer.ensureSpaceForWrite(serializedEvent.size)
            buffer.data!!.put(serializedEvent)
            buffer.timeUs = eventTimesUs[sampleIndex]
            buffer.setFlags(C.BUFFER_FLAG_KEY_FRAME)
            C.RESULT_BUFFER_READ
        } else {
            C.RESULT_NOTHING_READ
        }
    }

    override fun skipData(positionUs: Long): Int {
        val newIndex =
            Math.max(currentIndex, Util.binarySearchCeil(eventTimesUs, positionUs, true, false))
        val skipped = newIndex - currentIndex
        currentIndex = newIndex
        return skipped
    }

    init {
        eventMessageEncoder = EventMessageEncoder()
        pendingSeekPositionUs = C.TIME_UNSET
        eventTimesUs = eventStream.presentationTimesUs
        updateEventStream(eventStream, eventStreamAppendable)
    }
}

*/
