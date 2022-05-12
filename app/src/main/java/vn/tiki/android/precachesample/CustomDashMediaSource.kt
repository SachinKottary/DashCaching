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

package vn.tiki.android.precachesample

import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.text.TextUtils
import android.util.SparseArray
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.ParserException
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.ExoMediaCrypto
import com.google.android.exoplayer2.offline.FilteringManifestParser
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId
import com.google.android.exoplayer2.source.dash.*
import vn.tiki.android.precachesample.DashMediaSource.*
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback
import com.google.android.exoplayer2.source.dash.manifest.*
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction
import com.google.android.exoplayer2.upstream.LoaderErrorThrower.Dummy
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

*/
/** A DASH [MediaSource].  *//*

class DashMediaSource private constructor(
    private var manifest: DashManifest?,
    private var initialManifestUri: Uri?,
    manifestDataSourceFactory: DataSource.Factory?,
    manifestParser: ParsingLoadable.Parser<out DashManifest>?,
    chunkSourceFactory: DashChunkSource.Factory,
    compositeSequenceableLoaderFactory: CompositeSequenceableLoaderFactory,
    drmSessionManager: DrmSessionManager<*>,
    loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    livePresentationDelayMs: Long,
    livePresentationDelayOverridesManifest: Boolean,
    tag: Any?
) : BaseMediaSource() {
    companion object {
        */
/**
         * The default presentation delay for live streams. The presentation delay is the duration by
         * which the default start position precedes the end of the live window.
         *//*

        const val DEFAULT_LIVE_PRESENTATION_DELAY_MS: Long = 30000

        @Deprecated("Use {@link #DEFAULT_LIVE_PRESENTATION_DELAY_MS}. ")
        val DEFAULT_LIVE_PRESENTATION_DELAY_FIXED_MS = DEFAULT_LIVE_PRESENTATION_DELAY_MS

        @Deprecated("Use of this parameter is no longer necessary. ")
        val DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS: Long = -1

        */
/**
         * The interval in milliseconds between invocations of [ ][MediaSourceCaller.onSourceInfoRefreshed] when the source's [ ] is changing dynamically (for example, for incomplete live streams).
         *//*

        private const val NOTIFY_MANIFEST_INTERVAL_MS = 5000

        */
/**
         * The minimum default start position for live streams, relative to the start of the live window.
         *//*

        private const val MIN_LIVE_DEFAULT_START_POSITION_US: Long = 5000000
        private const val TAG = "DashMediaSource"

        init {
            ExoPlayerLibraryInfo.registerModule("goog.exo.dash")
        }
    }

    */
/** Factory for [DashMediaSource]s.  *//*

    class Factory(
        chunkSourceFactory: DashChunkSource.Factory?,
        manifestDataSourceFactory: DataSource.Factory?
    ) :
        MediaSourceFactory {
        private val chunkSourceFactory: DashChunkSource.Factory
        private val manifestDataSourceFactory: DataSource.Factory?
        private var drmSessionManager: DrmSessionManager<*>
        private var manifestParser: ParsingLoadable.Parser<out DashManifest>? = null
        private var streamKeys: List<StreamKey>? = null
        private var compositeSequenceableLoaderFactory: CompositeSequenceableLoaderFactory
        private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy
        private var livePresentationDelayMs: Long
        private var livePresentationDelayOverridesManifest = false
        private var isCreateCalled = false
        private var tag: Any? = null

        */
/**
         * Creates a new factory for [DashMediaSource]s.
         *
         * @param dataSourceFactory A factory for [DataSource] instances that will be used to load
         * manifest and media data.
         *//*

        constructor(dataSourceFactory: DataSource.Factory?) : this(
            DefaultDashChunkSource.Factory(
                dataSourceFactory!!
            ), dataSourceFactory
        ) {
        }

        */
/**
         * Sets a tag for the media source which will be published in the [ ] of the source as [ ][com.google.android.exoplayer2.Timeline.Window.tag].
         *
         * @param tag A tag for the media source.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        fun setTag(tag: Any?): Factory {
            Assertions.checkState(!isCreateCalled)
            this.tag = tag
            return this
        }

        */
/**
         * Sets the minimum number of times to retry if a loading error occurs. See [ ][.setLoadErrorHandlingPolicy] for the default value.
         *
         *
         * Calling this method is equivalent to calling [.setLoadErrorHandlingPolicy] with
         * [ DefaultLoadErrorHandlingPolicy(minLoadableRetryCount)][DefaultLoadErrorHandlingPolicy.DefaultLoadErrorHandlingPolicy]
         *
         * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        @Deprecated("Use {@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)} instead.")
        fun setMinLoadableRetryCount(minLoadableRetryCount: Int): Factory {
            return setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(minLoadableRetryCount))
        }

        */
/**
         * Sets the [LoadErrorHandlingPolicy]. The default value is created by calling [ ][DefaultLoadErrorHandlingPolicy.DefaultLoadErrorHandlingPolicy].
         *
         *
         * Calling this method overrides any calls to [.setMinLoadableRetryCount].
         *
         * @param loadErrorHandlingPolicy A [LoadErrorHandlingPolicy].
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): Factory {
            Assertions.checkState(!isCreateCalled)
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
            return this
        }

        @Deprecated("Use {@link #setLivePresentationDelayMs(long, boolean)}. ")
        fun setLivePresentationDelayMs(livePresentationDelayMs: Long): Factory {
            return if (livePresentationDelayMs == DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS) {
                setLivePresentationDelayMs(DEFAULT_LIVE_PRESENTATION_DELAY_MS, false)
            } else {
                setLivePresentationDelayMs(livePresentationDelayMs, true)
            }
        }

        */
/**
         * Sets the duration in milliseconds by which the default start position should precede the end
         * of the live window for live playbacks. The `overridesManifest` parameter specifies
         * whether the value is used in preference to one in the manifest, if present. The default value
         * is [.DEFAULT_LIVE_PRESENTATION_DELAY_MS], and by default `overridesManifest` is
         * false.
         *
         * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
         * default start position should precede the end of the live window.
         * @param overridesManifest Whether the value is used in preference to one in the manifest, if
         * present.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        fun setLivePresentationDelayMs(
            livePresentationDelayMs: Long, overridesManifest: Boolean
        ): Factory {
            Assertions.checkState(!isCreateCalled)
            this.livePresentationDelayMs = livePresentationDelayMs
            livePresentationDelayOverridesManifest = overridesManifest
            return this
        }

        */
/**
         * Sets the manifest parser to parse loaded manifest data when loading a manifest URI.
         *
         * @param manifestParser A parser for loaded manifest data.
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        fun setManifestParser(
            manifestParser: ParsingLoadable.Parser<out DashManifest?>?
        ): Factory {
            Assertions.checkState(!isCreateCalled)
            this.manifestParser = Assertions.checkNotNull(manifestParser)
            return this
        }

        */
/**
         * Sets the factory to create composite [SequenceableLoader]s for when this media source
         * loads data from multiple streams (video, audio etc...). The default is an instance of [ ].
         *
         * @param compositeSequenceableLoaderFactory A factory to create composite [     ]s for when this media source loads data from multiple streams (video,
         * audio etc...).
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        fun setCompositeSequenceableLoaderFactory(
            compositeSequenceableLoaderFactory: CompositeSequenceableLoaderFactory?
        ): Factory {
            Assertions.checkState(!isCreateCalled)
            this.compositeSequenceableLoaderFactory =
                Assertions.checkNotNull(compositeSequenceableLoaderFactory)
            return this
        }

        */
/**
         * Returns a new [DashMediaSource] using the current parameters and the specified
         * sideloaded manifest.
         *
         * @param manifest The manifest. [DashManifest.dynamic] must be false.
         * @return The new [DashMediaSource].
         * @throws IllegalArgumentException If [DashManifest.dynamic] is true.
         *//*

        fun createMediaSource(manifest: DashManifest): DashMediaSource {
            var manifest = manifest
            Assertions.checkArgument(!manifest.dynamic)
            isCreateCalled = true
            if (streamKeys != null && !streamKeys!!.isEmpty()) {
                manifest = manifest.copy(streamKeys!!)
            }
            return DashMediaSource(
                manifest,  */
/* manifestUri= *//*

                null,  */
/* manifestDataSourceFactory= *//*

                null,  */
/* manifestParser= *//*

                null,
                chunkSourceFactory,
                compositeSequenceableLoaderFactory,
                drmSessionManager,
                loadErrorHandlingPolicy,
                livePresentationDelayMs,
                livePresentationDelayOverridesManifest,
                tag
            )
        }

        @Deprecated(
            """Use {@link #createMediaSource(DashManifest)} and {@link
     *     #addEventListener(Handler, MediaSourceEventListener)} instead."""
        )
        fun createMediaSource(
            manifest: DashManifest,
            eventHandler: Handler?,
            eventListener: MediaSourceEventListener?
        ): DashMediaSource {
            val mediaSource = createMediaSource(manifest)
            if (eventHandler != null && eventListener != null) {
                mediaSource.addEventListener(eventHandler, eventListener)
            }
            return mediaSource
        }

        @Deprecated(
            """Use {@link #createMediaSource(Uri)} and {@link #addEventListener(Handler,
     *     MediaSourceEventListener)} instead."""
        )
        fun createMediaSource(
            manifestUri: Uri,
            eventHandler: Handler?,
            eventListener: MediaSourceEventListener?
        ): DashMediaSource {
            val mediaSource = createMediaSource(manifestUri)
            if (eventHandler != null && eventListener != null) {
                mediaSource.addEventListener(eventHandler, eventListener)
            }
            return mediaSource
        }

        */
/**
         * Sets the [DrmSessionManager] to use for acquiring [DrmSessions][DrmSession]. The
         * default value is [DrmSessionManager.DUMMY].
         *
         * @param drmSessionManager The [DrmSessionManager].
         * @return This factory, for convenience.
         * @throws IllegalStateException If one of the `create` methods has already been called.
         *//*

        override fun setDrmSessionManager(drmSessionManager: DrmSessionManager<*>): Factory {
            Assertions.checkState(!isCreateCalled)
            this.drmSessionManager = drmSessionManager
            return this
        }

        */
/**
         * Returns a new [DashMediaSource] using the current parameters.
         *
         * @param manifestUri The manifest [Uri].
         * @return The new [DashMediaSource].
         *//*

        override fun createMediaSource(manifestUri: Uri): DashMediaSource {
            isCreateCalled = true
            if (manifestParser == null) {
                manifestParser = DashManifestParser()
            }
            if (streamKeys != null) {
                manifestParser = FilteringManifestParser(manifestParser!!, streamKeys)
            }
            return DashMediaSource( */
/* manifest= *//*

                null,
                Assertions.checkNotNull(manifestUri),
                manifestDataSourceFactory,
                manifestParser,
                chunkSourceFactory,
                compositeSequenceableLoaderFactory,
                drmSessionManager,
                loadErrorHandlingPolicy,
                livePresentationDelayMs,
                livePresentationDelayOverridesManifest,
                tag
            )
        }

        override fun setStreamKeys(streamKeys: List<StreamKey>): Factory {
            Assertions.checkState(!isCreateCalled)
            this.streamKeys = streamKeys
            return this
        }

        override fun getSupportedTypes(): IntArray {
            return intArrayOf(C.TYPE_DASH)
        }

        */
/**
         * Creates a new factory for [DashMediaSource]s.
         *
         * @param chunkSourceFactory A factory for [DashChunkSource] instances.
         * @param manifestDataSourceFactory A factory for [DataSource] instances that will be used
         * to load (and refresh) the manifest. May be `null` if the factory will only ever be
         * used to create create media sources with sideloaded manifests via [     ][.createMediaSource].
         *//*

        init {
            this.chunkSourceFactory = Assertions.checkNotNull(chunkSourceFactory)
            this.manifestDataSourceFactory = manifestDataSourceFactory
            drmSessionManager = DrmSessionManager.getDummyDrmSessionManager<ExoMediaCrypto>()
            loadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy()
            livePresentationDelayMs = DEFAULT_LIVE_PRESENTATION_DELAY_MS
            compositeSequenceableLoaderFactory = DefaultCompositeSequenceableLoaderFactory()
        }
    }

    private val sideloadedManifest: Boolean
    private val manifestDataSourceFactory: DataSource.Factory?
    private val chunkSourceFactory: DashChunkSource.Factory
    private val compositeSequenceableLoaderFactory: CompositeSequenceableLoaderFactory
    private val drmSessionManager: DrmSessionManager<*>
    private val loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    private val livePresentationDelayMs: Long
    private val livePresentationDelayOverridesManifest: Boolean
    private val manifestEventDispatcher: MediaSourceEventListener.EventDispatcher
    private val manifestParser: ParsingLoadable.Parser<out DashManifest>?
    private var manifestCallback: ManifestCallback? = null
    private val manifestUriLock: Any
    private val periodsById: SparseArray<DashMediaPeriod>
    private var refreshManifestRunnable: Runnable? = null
    private var simulateManifestRefreshRunnable: Runnable? = null
    private val playerEmsgCallback: PlayerEmsgCallback
    private var manifestLoadErrorThrower: LoaderErrorThrower? = null
    private val tag: Any?
    private var dataSource: DataSource? = null
    private var loader: Loader? = null
    private var mediaTransferListener: TransferListener? = null
    private var manifestFatalError: IOException? = null
    private var handler: Handler? = null
    private var manifestUri: Uri? = null
    private var manifestLoadPending = false
    private var manifestLoadStartTimestampMs: Long = 0
    private var manifestLoadEndTimestampMs: Long = 0
    private var elapsedRealtimeOffsetMs: Long = 0
    private var staleManifestReloadAttempt = 0
    private var expiredManifestPublishTimeUs: Long
    private var firstPeriodId = 0

    */
/**
     * Constructs an instance to play a given [DashManifest], which must be static.
     *
     * @param manifest The manifest. [DashManifest.dynamic] must be false.
     * @param chunkSourceFactory A factory for [DashChunkSource] instances.
     * @param eventHandler A handler for events. May be null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     *//*

    @Deprecated("Use {@link Factory} instead.")
    constructor(
        manifest: DashManifest?,
        chunkSourceFactory: DashChunkSource.Factory,
        eventHandler: Handler?,
        eventListener: MediaSourceEventListener?
    ) : this(
        manifest,
        chunkSourceFactory,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT,
        eventHandler,
        eventListener
    ) {
    }

    */
/**
     * Constructs an instance to play a given [DashManifest], which must be static.
     *
     * @param manifest The manifest. [DashManifest.dynamic] must be false.
     * @param chunkSourceFactory A factory for [DashChunkSource] instances.
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @param eventHandler A handler for events. May be null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     *//*

    @Deprecated("Use {@link Factory} instead.")
    constructor(
        manifest: DashManifest?,
        chunkSourceFactory: DashChunkSource.Factory,
        minLoadableRetryCount: Int,
        eventHandler: Handler?,
        eventListener: MediaSourceEventListener?
    ) : this(
        manifest,  */
/* manifestUri= *//*

        null,  */
/* manifestDataSourceFactory= *//*

        null,  */
/* manifestParser= *//*

        null,
        chunkSourceFactory,
        DefaultCompositeSequenceableLoaderFactory(),
        DrmSessionManager.getDummyDrmSessionManager<ExoMediaCrypto>(),
        DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        DEFAULT_LIVE_PRESENTATION_DELAY_MS,  */
/* livePresentationDelayOverridesManifest= *//*

        false,  */
/* tag= *//*

        null
    ) {
        if (eventHandler != null && eventListener != null) {
            addEventListener(eventHandler, eventListener)
        }
    }

    */
/**
     * Constructs an instance to play the manifest at a given [Uri], which may be dynamic or
     * static.
     *
     * @param manifestUri The manifest [Uri].
     * @param manifestDataSourceFactory A factory for [DataSource] instances that will be used
     * to load (and refresh) the manifest.
     * @param chunkSourceFactory A factory for [DashChunkSource] instances.
     * @param eventHandler A handler for events. May be null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     *//*

    @Deprecated("Use {@link Factory} instead.")
    constructor(
        manifestUri: Uri?,
        manifestDataSourceFactory: DataSource.Factory?,
        chunkSourceFactory: DashChunkSource.Factory,
        eventHandler: Handler?,
        eventListener: MediaSourceEventListener?
    ) : this(
        manifestUri,
        manifestDataSourceFactory,
        chunkSourceFactory,
        DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT,
        DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS,
        eventHandler,
        eventListener
    ) {
    }

    */
/**
     * Constructs an instance to play the manifest at a given [Uri], which may be dynamic or
     * static.
     *
     * @param manifestUri The manifest [Uri].
     * @param manifestDataSourceFactory A factory for [DataSource] instances that will be used
     * to load (and refresh) the manifest.
     * @param chunkSourceFactory A factory for [DashChunkSource] instances.
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
     * default start position should precede the end of the live window. Use [     ][.DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS] to use the value specified by the
     * manifest, if present.
     * @param eventHandler A handler for events. May be null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     *//*

    @Deprecated("Use {@link Factory} instead.")
    constructor(
        manifestUri: Uri?,
        manifestDataSourceFactory: DataSource.Factory?,
        chunkSourceFactory: DashChunkSource.Factory,
        minLoadableRetryCount: Int,
        livePresentationDelayMs: Long,
        eventHandler: Handler?,
        eventListener: MediaSourceEventListener?
    ) : this(
        manifestUri,
        manifestDataSourceFactory,
        DashManifestParser(),
        chunkSourceFactory,
        minLoadableRetryCount,
        livePresentationDelayMs,
        eventHandler,
        eventListener
    ) {
    }

    */
/**
     * Constructs an instance to play the manifest at a given [Uri], which may be dynamic or
     * static.
     *
     * @param manifestUri The manifest [Uri].
     * @param manifestDataSourceFactory A factory for [DataSource] instances that will be used
     * to load (and refresh) the manifest.
     * @param manifestParser A parser for loaded manifest data.
     * @param chunkSourceFactory A factory for [DashChunkSource] instances.
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @param livePresentationDelayMs For live playbacks, the duration in milliseconds by which the
     * default start position should precede the end of the live window. Use [     ][.DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS] to use the value specified by the
     * manifest, if present.
     * @param eventHandler A handler for events. May be null if delivery of events is not required.
     * @param eventListener A listener of events. May be null if delivery of events is not required.
     *//*

    @Deprecated("Use {@link Factory} instead.")
    constructor(
        manifestUri: Uri?,
        manifestDataSourceFactory: DataSource.Factory?,
        manifestParser: ParsingLoadable.Parser<out DashManifest>?,
        chunkSourceFactory: DashChunkSource.Factory,
        minLoadableRetryCount: Int,
        livePresentationDelayMs: Long,
        eventHandler: Handler?,
        eventListener: MediaSourceEventListener?
    ) : this( */
/* manifest= *//*

        null,
        manifestUri,
        manifestDataSourceFactory,
        manifestParser,
        chunkSourceFactory,
        DefaultCompositeSequenceableLoaderFactory(),
        DrmSessionManager.getDummyDrmSessionManager<ExoMediaCrypto>(),
        DefaultLoadErrorHandlingPolicy(minLoadableRetryCount),
        if (livePresentationDelayMs == DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS) DEFAULT_LIVE_PRESENTATION_DELAY_MS else livePresentationDelayMs,
        livePresentationDelayMs != DEFAULT_LIVE_PRESENTATION_DELAY_PREFER_MANIFEST_MS,  */
/* tag= *//*

        null
    ) {
        if (eventHandler != null && eventListener != null) {
            addEventListener(eventHandler, eventListener)
        }
    }

    */
/**
     * Manually replaces the manifest [Uri].
     *
     * @param manifestUri The replacement manifest [Uri].
     *//*

    fun replaceManifestUri(manifestUri: Uri?) {
        synchronized(manifestUriLock) {
            initialManifestUri = manifestUri
            initialManifestUri = manifestUri
        }
    }

    // MediaSource implementation.
    override fun getTag(): Any? {
        return tag
    }

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        this.mediaTransferListener = mediaTransferListener
        drmSessionManager.prepare()
        if (sideloadedManifest) {
            processManifest(false)
        } else {
            dataSource = manifestDataSourceFactory!!.createDataSource()
            loader = Loader("Loader:DashMediaSource")
            handler = Handler()
            startLoadingManifest()
        }
    }

    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        manifestLoadErrorThrower!!.maybeThrowError()
    }

    override fun createPeriod(
        periodId: MediaPeriodId, allocator: Allocator, startPositionUs: Long
    ): MediaPeriod {
        val periodIndex = periodId.periodUid as Int - firstPeriodId
        val periodEventDispatcher =
            createEventDispatcher(periodId, manifest!!.getPeriod(periodIndex).startMs)
        val mediaPeriod = DashMediaPeriod(
            firstPeriodId + periodIndex,
            manifest!!,
            periodIndex,
            chunkSourceFactory,
            mediaTransferListener,
            drmSessionManager,
            loadErrorHandlingPolicy,
            periodEventDispatcher,
            elapsedRealtimeOffsetMs,
            manifestLoadErrorThrower!!,
            allocator,
            compositeSequenceableLoaderFactory,
            playerEmsgCallback
        )
        periodsById.put(mediaPeriod.id, mediaPeriod)
        return mediaPeriod
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        val dashMediaPeriod = mediaPeriod as DashMediaPeriod
        dashMediaPeriod.release()
        periodsById.remove(dashMediaPeriod.id)
    }

    override fun releaseSourceInternal() {
        manifestLoadPending = false
        dataSource = null
        if (loader != null) {
            loader!!.release()
            loader = null
        }
        manifestLoadStartTimestampMs = 0
        manifestLoadEndTimestampMs = 0
        manifest = if (sideloadedManifest) manifest else null
        initialManifestUri = initialManifestUri
        manifestFatalError = null
        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
            handler = null
        }
        elapsedRealtimeOffsetMs = 0
        staleManifestReloadAttempt = 0
        expiredManifestPublishTimeUs = C.TIME_UNSET
        firstPeriodId = 0
        periodsById.clear()
        drmSessionManager.release()
    }

    // PlayerEmsgCallback callbacks.
    */
/* package *//*

    fun onDashManifestRefreshRequested() {
        handler!!.removeCallbacks(simulateManifestRefreshRunnable!!)
        startLoadingManifest()
    }

    */
/* package *//*

    fun onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs: Long) {
        if (this.expiredManifestPublishTimeUs == C.TIME_UNSET
            || this.expiredManifestPublishTimeUs < expiredManifestPublishTimeUs
        ) {
            this.expiredManifestPublishTimeUs = expiredManifestPublishTimeUs
        }
    }

    // Loadable callbacks.
    */
/* package *//*

    fun onManifestLoadCompleted(
        loadable: ParsingLoadable<DashManifest?>,
        elapsedRealtimeMs: Long, loadDurationMs: Long
    ) {
        manifestEventDispatcher.loadCompleted(
            loadable.dataSpec,
            loadable.uri,
            loadable.responseHeaders,
            loadable.type,
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded()
        )
        val newManifest = loadable.result
        val oldPeriodCount = if (manifest == null) 0 else manifest!!.periodCount
        var removedPeriodCount = 0
        val newFirstPeriodStartTimeMs = newManifest!!.getPeriod(0).startMs
        while (removedPeriodCount < oldPeriodCount
            && manifest!!.getPeriod(removedPeriodCount).startMs < newFirstPeriodStartTimeMs
        ) {
            removedPeriodCount++
        }
        if (newManifest.dynamic) {
            var isManifestStale = false
            if (oldPeriodCount - removedPeriodCount > newManifest.periodCount) {
                // After discarding old periods, we should never have more periods than listed in the new
                // manifest. That would mean that a previously announced period is no longer advertised. If
                // this condition occurs, assume that we are hitting a manifest server that is out of sync
                // and
                // behind.
                Log.w(TAG, "Loaded out of sync manifest")
                isManifestStale = true
            } else if (expiredManifestPublishTimeUs != C.TIME_UNSET
                && newManifest.publishTimeMs * 1000 <= expiredManifestPublishTimeUs
            ) {
                // If we receive a dynamic manifest that's older than expected (i.e. its publish time has
                // expired, or it's dynamic and we know the presentation has ended), then this manifest is
                // stale.
                Log.w(
                    TAG,
                    "Loaded stale dynamic manifest: "
                            + newManifest.publishTimeMs
                            + ", "
                            + expiredManifestPublishTimeUs
                )
                isManifestStale = true
            }
            if (isManifestStale) {
                if (staleManifestReloadAttempt++
                    < loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type)
                ) {
                    scheduleManifestRefresh(manifestLoadRetryDelayMillis)
                } else {
                    manifestFatalError = DashManifestStaleException()
                }
                return
            }
            staleManifestReloadAttempt = 0
        }
        manifest = newManifest
        manifestLoadPending = manifestLoadPending and manifest!!.dynamic
        manifestLoadStartTimestampMs = elapsedRealtimeMs - loadDurationMs
        manifestLoadEndTimestampMs = elapsedRealtimeMs
        if (manifest!!.location != null) {
            synchronized(manifestUriLock) {

                // This condition checks that replaceManifestUri wasn't called between the start and end of
                // this load. If it was, we ignore the manifest location and prefer the manual replacement.
                val isSameUriInstance = loadable.dataSpec.uri === initialManifestUri
                if (isSameUriInstance) {
                    initialManifestUri = manifest!!.location
                }
            }
        }
        if (oldPeriodCount == 0) {
            if (manifest!!.dynamic && manifest!!.utcTiming != null) {
                resolveUtcTimingElement(manifest!!.utcTiming)
            } else {
                processManifest(true)
            }
        } else {
            firstPeriodId += removedPeriodCount
            processManifest(true)
        }
    }

    */
/* package *//*

    fun onManifestLoadError(
        loadable: ParsingLoadable<DashManifest?>,
        elapsedRealtimeMs: Long,
        loadDurationMs: Long,
        error: IOException?,
        errorCount: Int
    ): LoadErrorAction {
        val retryDelayMs = loadErrorHandlingPolicy.getRetryDelayMsFor(
            C.DATA_TYPE_MANIFEST, loadDurationMs, error, errorCount
        )
        val loadErrorAction =
            if (retryDelayMs == C.TIME_UNSET) Loader.DONT_RETRY_FATAL else Loader.createRetryAction( */
/* resetErrorCount= *//*

                false,
                retryDelayMs
            )
        manifestEventDispatcher.loadError(
            loadable.dataSpec,
            loadable.uri,
            loadable.responseHeaders,
            loadable.type,
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded(),
            error,
            !loadErrorAction.isRetry
        )
        return loadErrorAction
    }

    */
/* package *//*

    fun onUtcTimestampLoadCompleted(
        loadable: ParsingLoadable<Long>,
        elapsedRealtimeMs: Long, loadDurationMs: Long
    ) {
        manifestEventDispatcher.loadCompleted(
            loadable.dataSpec,
            loadable.uri,
            loadable.responseHeaders,
            loadable.type,
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded()
        )
        onUtcTimestampResolved(loadable.result!! - elapsedRealtimeMs)
    }

    */
/* package *//*

    fun onUtcTimestampLoadError(
        loadable: ParsingLoadable<Long>,
        elapsedRealtimeMs: Long,
        loadDurationMs: Long,
        error: IOException
    ): LoadErrorAction {
        manifestEventDispatcher.loadError(
            loadable.dataSpec,
            loadable.uri,
            loadable.responseHeaders,
            loadable.type,
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded(),
            error,
            true
        )
        onUtcTimestampResolutionError(error)
        return Loader.DONT_RETRY
    }

    */
/* package *//*

    fun onLoadCanceled(
        loadable: ParsingLoadable<*>, elapsedRealtimeMs: Long,
        loadDurationMs: Long
    ) {
        manifestEventDispatcher.loadCanceled(
            loadable.dataSpec,
            loadable.uri,
            loadable.responseHeaders,
            loadable.type,
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded()
        )
    }

    // Internal methods.
    private fun resolveUtcTimingElement(timingElement: UtcTimingElement?) {
        val scheme = timingElement!!.schemeIdUri
        if (Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2014")
            || Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2012")
        ) {
            resolveUtcTimingElementDirect(timingElement)
        } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2014")
            || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2012")
        ) {
            resolveUtcTimingElementHttp(timingElement, Iso8601Parser())
        } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2014")
            || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2012")
        ) {
            resolveUtcTimingElementHttp(timingElement, XsDateTimeParser())
        } else {
            // Unsupported scheme.
            onUtcTimestampResolutionError(IOException("Unsupported UTC timing scheme"))
        }
    }

    private fun resolveUtcTimingElementDirect(timingElement: UtcTimingElement?) {
        try {
            val utcTimestampMs = Util.parseXsDateTime(timingElement!!.value)
            onUtcTimestampResolved(utcTimestampMs - manifestLoadEndTimestampMs)
        } catch (e: ParserException) {
            onUtcTimestampResolutionError(e)
        }
    }

    private fun resolveUtcTimingElementHttp(
        timingElement: UtcTimingElement?,
        parser: ParsingLoadable.Parser<Long>
    ) {
        startLoading(
            ParsingLoadable(
                dataSource, Uri.parse(timingElement!!.value),
                C.DATA_TYPE_TIME_SYNCHRONIZATION, parser
            ), UtcTimestampCallback(), 1
        )
    }

    private fun onUtcTimestampResolved(elapsedRealtimeOffsetMs: Long) {
        this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs
        processManifest(true)
    }

    private fun onUtcTimestampResolutionError(error: IOException) {
        Log.e(TAG, "Failed to resolve UtcTiming element.", error)
        // Be optimistic and continue in the hope that the device clock is correct.
        processManifest(true)
    }

    private fun processManifest(scheduleRefresh: Boolean) {
        // Update any periods.
        for (i in 0 until periodsById.size()) {
            val id = periodsById.keyAt(i)
            if (id >= firstPeriodId) {
                periodsById.valueAt(i).updateManifest(manifest!!, id - firstPeriodId)
            } else {
                // This period has been removed from the manifest so it doesn't need to be updated.
            }
        }
        // Update the window.
        var windowChangingImplicitly = false
        val lastPeriodIndex = manifest!!.periodCount - 1
        val firstPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(
            manifest!!.getPeriod(0),
            manifest!!.getPeriodDurationUs(0)
        )
        val lastPeriodSeekInfo = PeriodSeekInfo.createPeriodSeekInfo(
            manifest!!.getPeriod(lastPeriodIndex), manifest!!.getPeriodDurationUs(lastPeriodIndex)
        )
        // Get the period-relative start/end times.
        var currentStartTimeUs = firstPeriodSeekInfo.availableStartTimeUs
        var currentEndTimeUs = lastPeriodSeekInfo.availableEndTimeUs
        if (manifest!!.dynamic && !lastPeriodSeekInfo.isIndexExplicit) {
            // The manifest describes an incomplete live stream. Update the start/end times to reflect the
            // live stream duration and the manifest's time shift buffer depth.
            val liveStreamDurationUs = nowUnixTimeUs - C.msToUs(manifest!!.availabilityStartTimeMs)
            val liveStreamEndPositionInLastPeriodUs = (liveStreamDurationUs
                    - C.msToUs(manifest!!.getPeriod(lastPeriodIndex).startMs))
            currentEndTimeUs = Math.min(liveStreamEndPositionInLastPeriodUs, currentEndTimeUs)
            if (manifest!!.timeShiftBufferDepthMs != C.TIME_UNSET) {
                val timeShiftBufferDepthUs = C.msToUs(manifest!!.timeShiftBufferDepthMs)
                var offsetInPeriodUs = currentEndTimeUs - timeShiftBufferDepthUs
                var periodIndex = lastPeriodIndex
                while (offsetInPeriodUs < 0 && periodIndex > 0) {
                    offsetInPeriodUs += manifest!!.getPeriodDurationUs(--periodIndex)
                }
                currentStartTimeUs = if (periodIndex == 0) {
                    Math.max(currentStartTimeUs, offsetInPeriodUs)
                } else {
                    // The time shift buffer starts after the earliest period.
                    // TODO: Does this ever happen?
                    manifest!!.getPeriodDurationUs(0)
                }
            }
            windowChangingImplicitly = true
        }
        var windowDurationUs = currentEndTimeUs - currentStartTimeUs
        for (i in 0 until manifest!!.periodCount - 1) {
            windowDurationUs += manifest!!.getPeriodDurationUs(i)
        }
        var windowDefaultStartPositionUs: Long = 0
        if (manifest!!.dynamic) {
            var presentationDelayForManifestMs = livePresentationDelayMs
            if (!livePresentationDelayOverridesManifest
                && manifest!!.suggestedPresentationDelayMs != C.TIME_UNSET
            ) {
                presentationDelayForManifestMs = manifest!!.suggestedPresentationDelayMs
            }
            // Snap the default position to the start of the segment containing it.
            windowDefaultStartPositionUs =
                windowDurationUs - C.msToUs(presentationDelayForManifestMs)
            if (windowDefaultStartPositionUs < MIN_LIVE_DEFAULT_START_POSITION_US) {
                // The default start position is too close to the start of the live window. Set it to the
                // minimum default start position provided the window is at least twice as big. Else set
                // it to the middle of the window.
                windowDefaultStartPositionUs = Math.min(
                    MIN_LIVE_DEFAULT_START_POSITION_US,
                    windowDurationUs / 2
                )
            }
        }
        var windowStartTimeMs = C.TIME_UNSET
        if (manifest!!.availabilityStartTimeMs != C.TIME_UNSET) {
            windowStartTimeMs = (manifest!!.availabilityStartTimeMs
                    + manifest!!.getPeriod(0).startMs
                    + C.usToMs(currentStartTimeUs))
        }
        val timeline = DashTimeline(
            manifest!!.availabilityStartTimeMs,
            windowStartTimeMs,
            firstPeriodId,
            currentStartTimeUs,
            windowDurationUs,
            windowDefaultStartPositionUs,
            manifest,
            tag
        )
        refreshSourceInfo(timeline)
        if (!sideloadedManifest) {
            // Remove any pending simulated refresh.
            handler!!.removeCallbacks(simulateManifestRefreshRunnable!!)
            // If the window is changing implicitly, post a simulated manifest refresh to update it.
            if (windowChangingImplicitly) {
                handler!!.postDelayed(
                    simulateManifestRefreshRunnable!!,
                    NOTIFY_MANIFEST_INTERVAL_MS.toLong()
                )
            }
            if (manifestLoadPending) {
                startLoadingManifest()
            } else if (scheduleRefresh
                && manifest!!.dynamic
                && manifest!!.minUpdatePeriodMs != C.TIME_UNSET
            ) {
                // Schedule an explicit refresh if needed.
                var minUpdatePeriodMs = manifest!!.minUpdatePeriodMs
                if (minUpdatePeriodMs == 0L) {
                    // TODO: This is a temporary hack to avoid constantly refreshing the MPD in cases where
                    // minimumUpdatePeriod is set to 0. In such cases we shouldn't refresh unless there is
                    // explicit signaling in the stream, according to:
                    // http://azure.microsoft.com/blog/2014/09/13/dash-live-streaming-with-azure-media-service
                    minUpdatePeriodMs = 5000
                }
                val nextLoadTimestampMs = manifestLoadStartTimestampMs + minUpdatePeriodMs
                val delayUntilNextLoadMs =
                    Math.max(0, nextLoadTimestampMs - SystemClock.elapsedRealtime())
                scheduleManifestRefresh(delayUntilNextLoadMs)
            }
        }
    }

    private fun scheduleManifestRefresh(delayUntilNextLoadMs: Long) {
        handler!!.postDelayed(refreshManifestRunnable!!, delayUntilNextLoadMs)
    }

    private fun startLoadingManifest() {
        handler!!.removeCallbacks(refreshManifestRunnable!!)
        if (loader!!.hasFatalError()) {
            return
        }
        if (loader!!.isLoading) {
            manifestLoadPending = true
            return
        }
        var manifestUri: Uri?
        synchronized(manifestUriLock) { manifestUri = initialManifestUri }
        manifestLoadPending = false
        startLoading(
            ParsingLoadable(dataSource, manifestUri, C.DATA_TYPE_MANIFEST, manifestParser),
            manifestCallback!!,
            loadErrorHandlingPolicy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST)
        )
    }

    private val manifestLoadRetryDelayMillis: Long
        private get() = Math.min((staleManifestReloadAttempt - 1) * 1000, 5000).toLong()

    private fun <T> startLoading(
        loadable: ParsingLoadable<T>,
        callback: Loader.Callback<ParsingLoadable<T>>?, minRetryCount: Int
    ) {
        val elapsedRealtimeMs = loader!!.startLoading(loadable, callback, minRetryCount)
        manifestEventDispatcher.loadStarted(loadable.dataSpec, loadable.type, elapsedRealtimeMs)
    }

    private val nowUnixTimeUs: Long
        private get() = if (elapsedRealtimeOffsetMs != 0L) {
            C.msToUs(SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs)
        } else {
            C.msToUs(System.currentTimeMillis())
        }

    private class PeriodSeekInfo private constructor(
        val isIndexExplicit: Boolean, val availableStartTimeUs: Long,
        val availableEndTimeUs: Long
    ) {
        companion object {
            fun createPeriodSeekInfo(
                period: Period, durationUs: Long
            ): PeriodSeekInfo {
                val adaptationSetCount = period.adaptationSets.size
                var availableStartTimeUs: Long = 0
                var availableEndTimeUs = Long.MAX_VALUE
                var isIndexExplicit = false
                var seenEmptyIndex = false
                var haveAudioVideoAdaptationSets = false
                for (i in 0 until adaptationSetCount) {
                    val type = period.adaptationSets[i].type
                    if (type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO) {
                        haveAudioVideoAdaptationSets = true
                        break
                    }
                }
                for (i in 0 until adaptationSetCount) {
                    val adaptationSet = period.adaptationSets[i]
                    // Exclude text adaptation sets from duration calculations, if we have at least one audio
                    // or video adaptation set. See: https://github.com/google/ExoPlayer/issues/4029
                    if (haveAudioVideoAdaptationSets && adaptationSet.type == C.TRACK_TYPE_TEXT) {
                        continue
                    }
                    val index = adaptationSet.representations[0].index
                        ?: return PeriodSeekInfo(true, 0, durationUs)
                    isIndexExplicit = isIndexExplicit or index.isExplicit
                    val segmentCount = index.getSegmentCount(durationUs)
                    if (segmentCount == 0) {
                        seenEmptyIndex = true
                        availableStartTimeUs = 0
                        availableEndTimeUs = 0
                    } else if (!seenEmptyIndex) {
                        val firstSegmentNum = index.firstSegmentNum
                        val adaptationSetAvailableStartTimeUs = index.getTimeUs(firstSegmentNum)
                        availableStartTimeUs =
                            Math.max(availableStartTimeUs, adaptationSetAvailableStartTimeUs)
                        if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED) {
                            val lastSegmentNum = firstSegmentNum + segmentCount - 1
                            val adaptationSetAvailableEndTimeUs = (index.getTimeUs(lastSegmentNum)
                                    + index.getDurationUs(lastSegmentNum, durationUs))
                            availableEndTimeUs =
                                Math.min(availableEndTimeUs, adaptationSetAvailableEndTimeUs)
                        }
                    }
                }
                return PeriodSeekInfo(isIndexExplicit, availableStartTimeUs, availableEndTimeUs)
            }
        }
    }

    private class DashTimeline(
        private val presentationStartTimeMs: Long,
        private val windowStartTimeMs: Long,
        private val firstPeriodId: Int,
        private val offsetInFirstPeriodUs: Long,
        private val windowDurationUs: Long,
        private val windowDefaultStartPositionUs: Long,
        private val manifest: DashManifest?,
        private val windowTag: Any?
    ) : Timeline() {
        override fun getPeriodCount(): Int {
            return manifest!!.periodCount
        }

        override fun getPeriod(periodIndex: Int, period: Period, setIdentifiers: Boolean): Period {
            Assertions.checkIndex(periodIndex, 0, periodCount)
            val id: Any? = if (setIdentifiers) manifest!!.getPeriod(periodIndex).id else null
            val uid: Any? = if (setIdentifiers) firstPeriodId + periodIndex else null
            return period.set(
                id, uid, 0, manifest!!.getPeriodDurationUs(periodIndex), C.msToUs(
                    manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs
                )
                        - offsetInFirstPeriodUs
            )
        }

        override fun getWindowCount(): Int {
            return 1
        }

        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long
        ): Window {
            Assertions.checkIndex(windowIndex, 0, 1)
            val windowDefaultStartPositionUs = getAdjustedWindowDefaultStartPositionUs(
                defaultPositionProjectionUs
            )
            return window.set(
                Window.SINGLE_WINDOW_UID,
                windowTag,
                manifest,
                presentationStartTimeMs,
                windowStartTimeMs,  */
/* isSeekable= *//*

                true,  */
/* isDynamic= *//*

                isMovingLiveWindow(manifest),  */
/* isLive= *//*

                manifest!!.dynamic,
                windowDefaultStartPositionUs,
                windowDurationUs,  */
/* firstPeriodIndex= *//*

                0,  */
/* lastPeriodIndex= *//*

                periodCount - 1,
                offsetInFirstPeriodUs
            )
        }

        override fun getIndexOfPeriod(uid: Any): Int {
            if (uid !is Int) {
                return C.INDEX_UNSET
            }
            val periodIndex = uid - firstPeriodId
            return if (periodIndex < 0 || periodIndex >= periodCount) C.INDEX_UNSET else periodIndex
        }

        private fun getAdjustedWindowDefaultStartPositionUs(defaultPositionProjectionUs: Long): Long {
            var windowDefaultStartPositionUs = windowDefaultStartPositionUs
            if (!isMovingLiveWindow(manifest)) {
                return windowDefaultStartPositionUs
            }
            if (defaultPositionProjectionUs > 0) {
                windowDefaultStartPositionUs += defaultPositionProjectionUs
                if (windowDefaultStartPositionUs > windowDurationUs) {
                    // The projection takes us beyond the end of the live window.
                    return C.TIME_UNSET
                }
            }
            // Attempt to snap to the start of the corresponding video segment.
            var periodIndex = 0
            var defaultStartPositionInPeriodUs =
                offsetInFirstPeriodUs + windowDefaultStartPositionUs
            var periodDurationUs = manifest!!.getPeriodDurationUs(periodIndex)
            while (periodIndex < manifest.periodCount - 1
                && defaultStartPositionInPeriodUs >= periodDurationUs
            ) {
                defaultStartPositionInPeriodUs -= periodDurationUs
                periodIndex++
                periodDurationUs = manifest.getPeriodDurationUs(periodIndex)
            }
            val period = manifest.getPeriod(periodIndex)
            val videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO)
            if (videoAdaptationSetIndex == C.INDEX_UNSET) {
                // No video adaptation set for snapping.
                return windowDefaultStartPositionUs
            }
            // If there are multiple video adaptation sets with unaligned segments, the initial time may
            // not correspond to the start of a segment in both, but this is an edge case.
            val snapIndex = period.adaptationSets[videoAdaptationSetIndex].representations[0].index
            if (snapIndex == null || snapIndex.getSegmentCount(periodDurationUs) == 0) {
                // Video adaptation set does not include a non-empty index for snapping.
                return windowDefaultStartPositionUs
            }
            val segmentNum =
                snapIndex.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs)
            return (windowDefaultStartPositionUs + snapIndex.getTimeUs(segmentNum)
                    - defaultStartPositionInPeriodUs)
        }

        override fun getUidOfPeriod(periodIndex: Int): Any {
            Assertions.checkIndex(periodIndex, 0, periodCount)
            return firstPeriodId + periodIndex
        }

        companion object {
            private fun isMovingLiveWindow(manifest: DashManifest?): Boolean {
                return (manifest!!.dynamic
                        && manifest.minUpdatePeriodMs != C.TIME_UNSET && manifest.durationMs == C.TIME_UNSET)
            }
        }
    }

    private inner class DefaultPlayerEmsgCallback : PlayerEmsgCallback {
        override fun onDashManifestRefreshRequested() {
            this@DashMediaSource.onDashManifestRefreshRequested()
        }

        override fun onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs: Long) {
            this@DashMediaSource.onDashManifestPublishTimeExpired(expiredManifestPublishTimeUs)
        }
    }

    private inner class ManifestCallback :
        Loader.Callback<ParsingLoadable<DashManifest?>> {
        override fun onLoadCompleted(
            loadable: ParsingLoadable<DashManifest?>,
            elapsedRealtimeMs: Long, loadDurationMs: Long
        ) {
            onManifestLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs)
        }

        override fun onLoadCanceled(
            loadable: ParsingLoadable<DashManifest?>,
            elapsedRealtimeMs: Long, loadDurationMs: Long, released: Boolean
        ) {
            this@DashMediaSource.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs)
        }

        override fun onLoadError(
            loadable: ParsingLoadable<DashManifest?>,
            elapsedRealtimeMs: Long,
            loadDurationMs: Long,
            error: IOException,
            errorCount: Int
        ): LoadErrorAction {
            return onManifestLoadError(
                loadable,
                elapsedRealtimeMs,
                loadDurationMs,
                error,
                errorCount
            )
        }
    }

    private inner class UtcTimestampCallback :
        Loader.Callback<ParsingLoadable<Long>> {
        override fun onLoadCompleted(
            loadable: ParsingLoadable<Long>, elapsedRealtimeMs: Long,
            loadDurationMs: Long
        ) {
            onUtcTimestampLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs)
        }

        override fun onLoadCanceled(
            loadable: ParsingLoadable<Long>, elapsedRealtimeMs: Long,
            loadDurationMs: Long, released: Boolean
        ) {
            this@DashMediaSource.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs)
        }

        override fun onLoadError(
            loadable: ParsingLoadable<Long>,
            elapsedRealtimeMs: Long,
            loadDurationMs: Long,
            error: IOException,
            errorCount: Int
        ): LoadErrorAction {
            return onUtcTimestampLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error)
        }
    }

    private class XsDateTimeParser : ParsingLoadable.Parser<Long> {
        @Throws(IOException::class)
        override fun parse(uri: Uri, inputStream: InputStream): Long {
            val firstLine = BufferedReader(InputStreamReader(inputStream)).readLine()
            return Util.parseXsDateTime(firstLine)
        }
    }

    */
/* package *//*

    internal class Iso8601Parser : ParsingLoadable.Parser<Long> {
        @Throws(IOException::class)
        override fun parse(uri: Uri, inputStream: InputStream): Long {
            val firstLine =
                BufferedReader(InputStreamReader(inputStream, Charset.forName(C.UTF8_NAME)))
                    .readLine()
            return try {
                val matcher = TIMESTAMP_WITH_TIMEZONE_PATTERN.matcher(firstLine)
                if (!matcher.matches()) {
                    throw ParserException("Couldn't parse timestamp: $firstLine")
                }
                // Parse the timestamp.
                val timestampWithoutTimezone = matcher.group(1)
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                var timestampMs = format.parse(timestampWithoutTimezone).time
                // Parse the timezone.
                val timezone = matcher.group(2)
                if ("Z" == timezone) {
                    // UTC (no offset).
                } else {
                    val sign = if ("+" == matcher.group(4)) 1 else -1.toLong()
                    val hours = matcher.group(5).toLong()
                    val minutesString = matcher.group(7)
                    val minutes =
                        if (TextUtils.isEmpty(minutesString)) 0 else minutesString.toLong()
                    val timestampOffsetMs = sign * ((hours * 60 + minutes) * 60 * 1000)
                    timestampMs -= timestampOffsetMs
                }
                timestampMs
            } catch (e: ParseException) {
                throw ParserException(e)
            }
        }

        companion object {
            private val TIMESTAMP_WITH_TIMEZONE_PATTERN =
                Pattern.compile("(.+?)(Z|((\\+|-|−)(\\d\\d)(:?(\\d\\d))?))")
        }
    }

    */
/**
     * A [LoaderErrorThrower] that throws fatal [IOException] that has occurred during
     * manifest loading from the manifest `loader`, or exception with the loaded manifest.
     *//*

    */
/* package *//*

    internal inner class ManifestLoadErrorThrower : LoaderErrorThrower {
        @Throws(IOException::class)
        override fun maybeThrowError() {
            loader!!.maybeThrowError()
            maybeThrowManifestError()
        }

        @Throws(IOException::class)
        override fun maybeThrowError(minRetryCount: Int) {
            loader!!.maybeThrowError(minRetryCount)
            maybeThrowManifestError()
        }

        @Throws(IOException::class)
        private fun maybeThrowManifestError() {
            if (manifestFatalError != null) {
                throw manifestFatalError
            }
        }
    }

    init {
        initialManifestUri = initialManifestUri
        this.manifestDataSourceFactory = manifestDataSourceFactory
        this.manifestParser = manifestParser
        this.chunkSourceFactory = chunkSourceFactory
        this.drmSessionManager = drmSessionManager
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
        this.livePresentationDelayMs = livePresentationDelayMs
        this.livePresentationDelayOverridesManifest = livePresentationDelayOverridesManifest
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory
        this.tag = tag
        sideloadedManifest = manifest != null
        manifestEventDispatcher = createEventDispatcher( */
/* mediaPeriodId= *//*
null)
        manifestUriLock = Any()
        periodsById = SparseArray()
        playerEmsgCallback = DefaultPlayerEmsgCallback()
        expiredManifestPublishTimeUs = C.TIME_UNSET
        if (sideloadedManifest) {
            Assertions.checkState(!manifest!!.dynamic)
            manifestCallback = null
            refreshManifestRunnable = null
            simulateManifestRefreshRunnable = null
            manifestLoadErrorThrower = Dummy()
        } else {
            manifestCallback = ManifestCallback()
            manifestLoadErrorThrower = ManifestLoadErrorThrower()
            refreshManifestRunnable = Runnable { startLoadingManifest() }
            simulateManifestRefreshRunnable = Runnable { processManifest(false) }
        }
    }
}*/
