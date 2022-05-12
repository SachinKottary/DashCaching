package vn.tiki.android.precachesample

import android.net.Uri
import android.util.Log
import androidx.annotation.IntDef
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.DataSpec.HttpMethod
import com.google.android.exoplayer2.upstream.cache.*
import com.google.android.exoplayer2.upstream.cache.Cache.CacheException
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource.CacheIgnoredReason
import com.google.android.exoplayer2.util.Assertions
import java.io.IOException
import java.io.InterruptedIOException
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

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
*/
/**
 * A [DataSource] that reads and writes a [Cache]. Requests are fulfilled from the cache
 * when possible. When data is not cached it is requested from an upstream [DataSource] and
 * written into the cache.
 */
class AsuraCacheDataSource @JvmOverloads constructor(
    private val cache: Cache,
    upstream: DataSource,
    private val cacheReadDataSource: DataSource,
    cacheWriteDataSink: DataSink?,
    @CacheDataSource.Flags flags: Int,
    eventListener: CacheDataSource.EventListener?,
    cacheKeyFactory: CacheKeyFactory? =  /* cacheKeyFactory= */
        null
) : DataSource {
    var videoStateChange: OnVideoLoadStateChange? = null
    /**
     * Flags controlling the CacheDataSource's behavior. Possible flag values are [ ][.FLAG_BLOCK_ON_CACHE], [.FLAG_IGNORE_CACHE_ON_ERROR] and [ ][.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
        flag = true,
        value = [FLAG_BLOCK_ON_CACHE, FLAG_IGNORE_CACHE_ON_ERROR, FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS]
    )
    annotation class Flags

    /**
     * Reasons the cache may be ignored. One of [.CACHE_IGNORED_REASON_ERROR] or [ ][.CACHE_IGNORED_REASON_UNSET_LENGTH].
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
        CACHE_IGNORED_REASON_ERROR,
        CACHE_IGNORED_REASON_UNSET_LENGTH
    )
    annotation class CacheIgnoredReason

    /**
     * Listener of [CacheDataSource] events.
     */
    interface EventListener {
        /**
         * Called when bytes have been read from the cache.
         *
         * @param cacheSizeBytes Current cache size in bytes.
         * @param cachedBytesRead Total bytes read from the cache since this method was last called.
         */
        fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long)

        /**
         * Called when the current request ignores cache.
         *
         * @param reason Reason cache is bypassed.
         */
        fun onCacheIgnored(@CacheIgnoredReason reason: Int)
    }

    private var cacheWriteDataSource: DataSource? = null
    private val upstreamDataSource: DataSource
    private val cacheKeyFactory: CacheKeyFactory
    private val eventListener: CacheDataSource.EventListener?
    private val blockOnCache: Boolean
    private val ignoreCacheOnError: Boolean
    private val ignoreCacheForUnsetLengthRequests: Boolean
    private var currentDataSource: DataSource? = null
    private var currentDataSpecLengthUnset = false
    private var uri: Uri? = null
    private var actualUri: Uri? = null

    @HttpMethod
    private var httpMethod = 0
    private var httpBody: ByteArray? = null
    private var httpRequestHeaders = emptyMap<String, String>()

    @DataSpec.Flags
    private var flags = 0
    private var key: String? = null
    private var readPosition: Long = 0
    private var bytesRemaining: Long = 0
    private var currentHoleSpan: CacheSpan? = null
    private var seenCacheError = false
    private var currentRequestIgnoresCache = false
    private var totalCachedBytesRead: Long = 0
    private var checkCachePosition: Long = 0
    /**
     * Constructs an instance with default [DataSource] and [DataSink] instances for
     * reading and writing the cache.
     *
     * @param cache The cache.
     * @param upstream A [DataSource] for reading data not in the cache.
     * @param flags A combination of [.FLAG_BLOCK_ON_CACHE], [.FLAG_IGNORE_CACHE_ON_ERROR]
     * and [.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS], or 0.
     */
    /**
     * Constructs an instance with default [DataSource] and [DataSink] instances for
     * reading and writing the cache.
     *
     * @param cache The cache.
     * @param upstream A [DataSource] for reading data not in the cache.
     */
    @JvmOverloads
    constructor(
        cache: Cache,
        upstream: DataSource,
        @CacheDataSource.Flags flags: Int =  /* flags= */0
    ) : this(
        cache,
        upstream,
        FileDataSource(),
        CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
        flags,  /* eventListener= */
        null
    ) {
    }

    override fun addTransferListener(transferListener: TransferListener) {
        cacheReadDataSource.addTransferListener(transferListener)
        upstreamDataSource.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        return try {
            key = cacheKeyFactory.buildCacheKey(dataSpec)
            uri = dataSpec.uri
            actualUri = getRedirectedUriOrDefault(
                cache, key!!,  /* defaultUri= */uri!!
            )
            httpMethod = dataSpec.httpMethod
            httpBody = dataSpec.httpBody
            httpRequestHeaders = dataSpec.httpRequestHeaders
            flags = dataSpec.flags
            readPosition = dataSpec.position
            val reason = shouldIgnoreCacheForRequest(dataSpec)
            currentRequestIgnoresCache = reason != CACHE_NOT_IGNORED
            if (currentRequestIgnoresCache) {
                notifyCacheIgnored(reason)
            }
            if (dataSpec.length != C.LENGTH_UNSET.toLong() || currentRequestIgnoresCache) {
                bytesRemaining = dataSpec.length
            } else {
                bytesRemaining = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= dataSpec.position
                    if (bytesRemaining <= 0) {
                        throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
                    }
                }
            }
            openNextSource(false)
            bytesRemaining
        } catch (e: Throwable) {
            handleBeforeThrow(e)
            throw e
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) {
            return 0
        }
        return if (bytesRemaining == 0L) {
            C.RESULT_END_OF_INPUT
        } else try {
            if (readPosition >= checkCachePosition) {
                openNextSource(true)
            }
            val bytesRead = currentDataSource!!.read(buffer, offset, readLength)
            if (bytesRead != C.RESULT_END_OF_INPUT) {
                if (isReadingFromCache) {
                    totalCachedBytesRead += bytesRead.toLong()
                }
                readPosition += bytesRead.toLong()
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= bytesRead.toLong()
                }
            } else if (currentDataSpecLengthUnset) {
                setNoBytesRemainingAndMaybeStoreLength()
            } else if (bytesRemaining > 0 || bytesRemaining == C.LENGTH_UNSET.toLong()) {
                closeCurrentSource()
                openNextSource(false)
                return read(buffer, offset, readLength)
            }
            bytesRead
        } catch (e: IOException) {
            if (currentDataSpecLengthUnset && isCausedByPositionOutOfRange(e)) {
                setNoBytesRemainingAndMaybeStoreLength()
                return C.RESULT_END_OF_INPUT
            }
            handleBeforeThrow(e)
            throw e
        } catch (e: Throwable) {
            handleBeforeThrow(e)
            throw e
        }
    }

    override fun getUri(): Uri? {
        return actualUri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        // TODO: Implement.
        return if (isReadingFromUpstream) upstreamDataSource.responseHeaders else emptyMap()
    }

    @Throws(IOException::class)
    override fun close() {
        uri = null
        actualUri = null
        httpMethod = DataSpec.HTTP_METHOD_GET
        httpBody = null
        httpRequestHeaders = emptyMap()
        flags = 0
        readPosition = 0
        key = null
        notifyBytesRead()
        try {
            closeCurrentSource()
        } catch (e: Throwable) {
            handleBeforeThrow(e)
            throw e
        }
    }

    /**
     * Opens the next source. If the cache contains data spanning the current read position then
     * [.cacheReadDataSource] is opened to read from it. Else [.upstreamDataSource] is
     * opened to read from the upstream source and write into the cache.
     *
     *
     * There must not be a currently open source when this method is called, except in the case
     * that `checkCache` is true. If `checkCache` is true then there must be a currently
     * open source, and it must be [.upstreamDataSource]. It will be closed and a new source
     * opened if it's possible to switch to reading from or writing to the cache. If a switch isn't
     * possible then the current source is left unchanged.
     *
     * @param checkCache If true tries to switch to reading from or writing to cache instead of
     * reading from [.upstreamDataSource], which is the currently open source.
     */
    @Throws(IOException::class)
    private fun openNextSource(checkCache: Boolean) {
        var nextSpan: CacheSpan?
        nextSpan = if (currentRequestIgnoresCache) {
            null
        } else if (blockOnCache) {
            try {
                cache.startReadWrite(key, readPosition)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
        } else {
            cache.startReadWriteNonBlocking(key, readPosition)
        }
        val nextDataSpec: DataSpec
        var nextDataSource: DataSource? = null
        if (nextSpan == null) {
            // The data is locked in the cache, or we're ignoring the cache. Bypass the cache and read
            // from upstream.
            nextDataSource = upstreamDataSource
            nextDataSpec = DataSpec(
                uri,
                httpMethod,
                httpBody,
                readPosition,
                readPosition,
                bytesRemaining,
                key,
                flags,
                httpRequestHeaders
            )
        } else if (nextSpan.isCached) {
            Log.d("MainAc"," Cache available")
            // Data is cached, read from cache.
            val fileUri = Uri.fromFile(nextSpan.file)
            val filePosition = readPosition - nextSpan.position
            var length = nextSpan.length - filePosition
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                length = Math.min(length, bytesRemaining)
            }
            // Deliberately skip the HTTP-related parameters since we're reading from the cache, not
            // making an HTTP request.
            nextDataSpec = DataSpec(fileUri, readPosition, filePosition, length, key, flags)
            nextDataSource = cacheReadDataSource
            if (true) {
                Log.d("SACHINKOTTARY", "Cache: $uri => File: $fileUri")
            }
        } else {
            // Data is not cached, and data is not locked, read from upstream with cache backing.
            var length: Long
            if (nextSpan.isOpenEnded) {
                length = bytesRemaining
            } else {
                length = nextSpan.length
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    length = Math.min(length, bytesRemaining)
                }
            }
            nextDataSpec = DataSpec(
                uri,
                httpMethod,
                httpBody,
                readPosition,
                readPosition,
                length,
                key,
                flags,
                httpRequestHeaders
            )
            cacheWriteDataSource?.let { nextDataSource = it } ?: kotlin.run {
                nextDataSource = upstreamDataSource
                cache.releaseHoleSpan(nextSpan)
                nextSpan = null
            }
            if (true) {
                Log.d("SACHINKOTTARY", "UpStream: $uri")
            }

        }
        checkCachePosition =
            if (!currentRequestIgnoresCache && nextDataSource === upstreamDataSource) readPosition + MIN_READ_BEFORE_CHECKING_CACHE else Long.MAX_VALUE
        if (checkCache) {
            Assertions.checkState(isBypassingCache)
            if (nextDataSource === upstreamDataSource) {
                // Continue reading from upstream.
                return
            }
            // We're switching to reading from or writing to the cache.
            try {
                closeCurrentSource()
            } catch (e: Throwable) {
                if (nextSpan!!.isHoleSpan) {
                    // Release the hole span before throwing, else we'll hold it forever.
                    cache.releaseHoleSpan(nextSpan)
                }
                throw e
            }
        }
        if (nextSpan != null && nextSpan?.isHoleSpan == true) {
            currentHoleSpan = nextSpan
        }
        currentDataSource = nextDataSource
        currentDataSpecLengthUnset = nextDataSpec.length == C.LENGTH_UNSET.toLong()
        val resolvedLength = nextDataSource?.open(nextDataSpec)

        // Update bytesRemaining, actualUri and (if writing to cache) the cache metadata.
        val mutations = ContentMetadataMutations()
        if (currentDataSpecLengthUnset && resolvedLength != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = resolvedLength ?: 0
            ContentMetadataMutations.setContentLength(mutations, readPosition + bytesRemaining)
        }
        if (isReadingFromUpstream) {
            actualUri = currentDataSource!!.uri
            val isRedirected = uri != actualUri
            ContentMetadataMutations.setRedirectedUri(
                mutations,
                if (isRedirected) actualUri else null
            )
        }
        if (isWritingToCache) {
            cache.applyContentMetadataMutations(key, mutations)
        }
    }

    @Throws(IOException::class)
    private fun setNoBytesRemainingAndMaybeStoreLength() {
        bytesRemaining = 0
        if (isWritingToCache) {
            val mutations = ContentMetadataMutations()
            ContentMetadataMutations.setContentLength(mutations, readPosition)
            cache.applyContentMetadataMutations(key, mutations)
        }
    }

    val isReadingFromUpstream: Boolean
         get() = !isReadingFromCache
    private val isBypassingCache: Boolean
        private get() = currentDataSource === upstreamDataSource
    val isReadingFromCache: Boolean
         get() = currentDataSource === cacheReadDataSource
    private val isWritingToCache: Boolean
        private get() = currentDataSource === cacheWriteDataSource

    @Throws(IOException::class)
    private fun closeCurrentSource() {
        if (currentDataSource == null) {
            return
        }
        try {
            currentDataSource!!.close()
        } finally {
            currentDataSource = null
            currentDataSpecLengthUnset = false
            if (currentHoleSpan != null) {
                cache.releaseHoleSpan(currentHoleSpan)
                currentHoleSpan = null
            }
        }
    }

    private fun handleBeforeThrow(exception: Throwable) {
        if (isReadingFromCache || exception is CacheException) {
            seenCacheError = true
        }
    }

    private fun shouldIgnoreCacheForRequest(dataSpec: DataSpec): Int {
        return if (ignoreCacheOnError && seenCacheError) {
            CACHE_IGNORED_REASON_ERROR
        } else if (ignoreCacheForUnsetLengthRequests && dataSpec.length == C.LENGTH_UNSET.toLong()) {
            CACHE_IGNORED_REASON_UNSET_LENGTH
        } else {
            CACHE_NOT_IGNORED
        }
    }

    private fun notifyCacheIgnored(@CacheIgnoredReason reason: Int) {
        eventListener?.onCacheIgnored(reason)
    }

    private fun notifyBytesRead() {
        if (eventListener != null && totalCachedBytesRead > 0) {
            eventListener.onCachedBytesRead(cache.cacheSpace, totalCachedBytesRead)
            totalCachedBytesRead = 0
        } else {
/*            Log.d("MainAc"," Switching to network")
            videoStateChange?.changedToNetworkDownload()*/
        }
    }

    companion object {
        /**
         * A flag indicating whether we will block reads if the cache key is locked. If unset then data is
         * read from upstream if the cache key is locked, regardless of whether the data is cached.
         */
        const val FLAG_BLOCK_ON_CACHE = 1

        /**
         * A flag indicating whether the cache is bypassed following any cache related error. If set
         * then cache related exceptions may be thrown for one cycle of open, read and close calls.
         * Subsequent cycles of these calls will then bypass the cache.
         */
        const val FLAG_IGNORE_CACHE_ON_ERROR = 1 shl 1 // 2

        /**
         * A flag indicating that the cache should be bypassed for requests whose lengths are unset. This
         * flag is provided for legacy reasons only.
         */
        const val FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS = 1 shl 2 // 4

        /** Cache not ignored.  */
        private const val CACHE_NOT_IGNORED = -1

        /** Cache ignored due to a cache related error.  */
        const val CACHE_IGNORED_REASON_ERROR = 0

        /** Cache ignored due to a request with an unset length.  */
        const val CACHE_IGNORED_REASON_UNSET_LENGTH = 1

        /** Minimum number of bytes to read before checking cache for availability.  */
        private const val MIN_READ_BEFORE_CHECKING_CACHE = (100 * 1024).toLong()
        private fun getRedirectedUriOrDefault(cache: Cache, key: String, defaultUri: Uri): Uri {
            val redirectedUri = ContentMetadata.getRedirectedUri(cache.getContentMetadata(key))
            return redirectedUri ?: defaultUri
        }
    }

    fun isCausedByPositionOutOfRange(e: IOException?): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is DataSourceException) {
                val reason = cause.reason
                if (reason == DataSourceException.POSITION_OUT_OF_RANGE) {
                    return true
                }
            }
            cause = cause.cause
        }
        return false
    }
    /**
     * Constructs an instance with arbitrary [DataSource] and [DataSink] instances for
     * reading and writing the cache. One use of this constructor is to allow data to be transformed
     * before it is written to disk.
     *
     * @param cache The cache.
     * @param upstream A [DataSource] for reading data not in the cache.
     * @param cacheReadDataSource A [DataSource] for reading data from the cache.
     * @param cacheWriteDataSink A [DataSink] for writing data to the cache. If null, cache is
     * accessed read-only.
     * @param flags A combination of [.FLAG_BLOCK_ON_CACHE], [.FLAG_IGNORE_CACHE_ON_ERROR]
     * and [.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS], or 0.
     * @param eventListener An optional [EventListener] to receive events.
     * @param cacheKeyFactory An optional factory for cache keys.
     */
    /**
     * Constructs an instance with arbitrary [DataSource] and [DataSink] instances for
     * reading and writing the cache. One use of this constructor is to allow data to be transformed
     * before it is written to disk.
     *
     * @param cache The cache.
     * @param upstream A [DataSource] for reading data not in the cache.
     * @param cacheReadDataSource A [DataSource] for reading data from the cache.
     * @param cacheWriteDataSink A [DataSink] for writing data to the cache. If null, cache is
     * accessed read-only.
     * @param flags A combination of [.FLAG_BLOCK_ON_CACHE], [.FLAG_IGNORE_CACHE_ON_ERROR]
     * and [.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS], or 0.
     * @param eventListener An optional [EventListener] to receive events.
     */
    init {
        this.cacheKeyFactory = cacheKeyFactory ?: CacheUtil.DEFAULT_CACHE_KEY_FACTORY
        blockOnCache = flags and FLAG_BLOCK_ON_CACHE != 0
        ignoreCacheOnError = flags and FLAG_IGNORE_CACHE_ON_ERROR != 0
        ignoreCacheForUnsetLengthRequests =
            flags and FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS != 0
        upstreamDataSource = upstream
        if (cacheWriteDataSink != null) {
            cacheWriteDataSource = TeeDataSource(upstream, cacheWriteDataSink)
        } else {
            cacheWriteDataSource = null
        }
        this.eventListener = eventListener
    }
}

interface OnVideoLoadStateChange {
    fun changedToNetworkDownload()
}