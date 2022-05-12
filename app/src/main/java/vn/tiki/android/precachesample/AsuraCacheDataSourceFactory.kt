package vn.tiki.android.precachesample

import android.util.Log
import com.google.android.exoplayer2.upstream.DataSink
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.*


/** A [DataSource.Factory] that produces [CacheDataSource].  */
class AsuraCacheDataSourceFactory
/**
 * @see CacheDataSource.CacheDataSource
 */ @JvmOverloads constructor(
    private val cache: Cache,
    private val upstreamFactory: DataSource.Factory,
    private val cacheReadDataSourceFactory: DataSource.Factory,
    private val cacheWriteDataSinkFactory: DataSink.Factory?,
    @field:CacheDataSource.Flags @param:CacheDataSource.Flags private val flags: Int,
    private val eventListener: CacheDataSource.EventListener?,
    private val cacheKeyFactory: CacheKeyFactory? =  /* cacheKeyFactory= */
        null
) : DataSource.Factory {
    /** @see CacheDataSource.CacheDataSource
     */
    /**
     * Constructs a factory which creates [CacheDataSource] instances with default [ ] and [DataSink] instances for reading and writing the cache.
     *
     * @param cache The cache.
     * @param upstreamFactory A [DataSource.Factory] for creating upstream [DataSource]s
     * for reading data not in the cache.
     */
    @JvmOverloads
    constructor(
        cache: Cache,
        upstreamFactory: DataSource.Factory,
        @CacheDataSource.Flags flags: Int =  /* flags= */0
    ) : this(
        cache,
        upstreamFactory,
        FileDataSource.Factory(),
        CacheDataSinkFactory(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
        flags,  /* eventListener= */
        null
    ) {
    }

    var cacheDataSource: AsuraCacheDataSource? = null
    private var listener: OnVideoLoadStateChange? = null

    override fun createDataSource(): AsuraCacheDataSource {
        return AsuraCacheDataSource(
            cache,
            upstreamFactory.createDataSource(),
            cacheReadDataSourceFactory.createDataSource(),
            cacheWriteDataSinkFactory?.createDataSink(),
            flags,
            eventListener,
            cacheKeyFactory
        ).apply {
            cacheDataSource = this
            videoStateChange = listener
        }
    }

    fun setVideoLoadListener(listener: OnVideoLoadStateChange) {
        cacheDataSource?.videoStateChange = listener
        this.listener = listener
    }
    /**
     * @see CacheDataSource.CacheDataSource
     */
    suspend fun isUpStreaming(): Boolean {
        val isUpStream: Boolean = false
        return try {
            cacheDataSource?.responseHeaders?.isNotEmpty() ?: false
        } catch (e: Exception) {
            Log.d("SACHINKOTTARY", "Exception while reading ")
            e.printStackTrace()
            isUpStream
        }
    }

}
