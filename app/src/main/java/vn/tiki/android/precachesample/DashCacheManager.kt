package vn.tiki.android.precachesample

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.source.dash.offline.DashDownloader
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DashCacheManager private constructor(
    private val appContext: Context,
    private val cacheDirectory: String,
    private val cacheEvictor: CacheEvictor,
    private val cacheStreamKey: ArrayList<StreamKey>

) {
    private val upstreamDataSourceFactory by lazy { DefaultDataSourceFactory(appContext, "Android") }

    val cache: Cache by lazy {
        SimpleCache(
            File("${appContext.cacheDir.absolutePath}$cacheDirectory"),
            cacheEvictor,
            ExoDatabaseProvider(appContext)
        )
    }

    val trackSelector by lazy {
        val adaptiveTrackSelectionFactory: AdaptiveTrackSelection.Factory = AdaptiveTrackSelection.Factory()
        DefaultTrackSelector(appContext, adaptiveTrackSelectionFactory)
    }

    val cacheDataSourceFactory by lazy {
        CacheDataSourceFactory(
            cache,
            upstreamDataSourceFactory,
            FileDataSource.Factory(),
            CacheDataSinkFactory(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    Log.d(TAG, "onCachedBytesRead(size:$cacheSizeBytes, cacheBytesRead:$cachedBytesRead)")
                }

                override fun onCacheIgnored(reason: Int) {
                    Log.d(TAG, "onCacheIgnored(reason: $reason)")
                }
            }
        )
    }

    private val downloadConstructorHelper by lazy {
        //cacheDataSourceFactory.createDataSourceForDownloading()
        DownloaderConstructorHelper(
            cache,
            upstreamDataSourceFactory,
            cacheDataSourceFactory,
            null,
            null
        )
    }

    private val streamDownloaderMap = HashMap<String, DashDownloader?>()

    fun forceLowestBitrate() {
        trackSelector.parameters
            .buildUpon()
            .setMaxVideoSizeSd()
            .setForceLowestBitrate(true)
            .build()
            .apply { trackSelector.parameters = this }
    }

    fun resetBitrateSelection() {
        trackSelector.parameters
            .buildUpon()
            .clearVideoSizeConstraints()
            .setForceLowestBitrate(false)
            .build()
            .apply { trackSelector.parameters = this }
    }

    private fun getDashDownloader(uri: String): DashDownloader =
        streamDownloaderMap[uri] ?: DashDownloader(Uri.parse(uri), cacheStreamKey, downloadConstructorHelper).apply { streamDownloaderMap[uri] = this}

    suspend fun startCaching(uri: String) = withContext(Dispatchers.IO) {
        runCatching {
            // do nothing if already cache enough
            if (cache.isCached(uri, 0, DEFAULT_MAX_DOWNLOAD_SIZE)) {
                Log.d(TAG, "video already cached")
                return@runCatching
            }
            Log.d(TAG, "Starting precache for => $uri")
            val downloader = getDashDownloader(uri)
            downloader.download { contentLength, bytesDownloaded, percentDownloaded ->
                if (bytesDownloaded >= DEFAULT_MAX_DOWNLOAD_SIZE) downloader.cancel()
                Log.d(TAG, "Downloaded: $bytesDownloaded, Percentage: $percentDownloaded")
            }
        }.onFailure {
            if (it is InterruptedException) return@onFailure
            Log.d(TAG, "Cache failed for $uri  reason $it}")
            it.printStackTrace()
        }.onSuccess {
            Log.d(TAG, "Cache success for $uri")
        }
        Unit
    }

    fun stopCaching(uri: String) {
        streamDownloaderMap[uri]?.let { downloader -> downloader.cancel() }
        streamDownloaderMap.remove(uri)
    }

    fun reset() {
        streamDownloaderMap.clear()
        cache.release()
    }

    companion object {
        private const val TAG = "DashCacheManager"
        private var cacheManager: DashCacheManager? = null
        private val defaultStreamKeys = arrayListOf(StreamKey(0, 0), StreamKey(1, 0))
        private const val DEFAULT_CACHE_SIZE = 30 * 1024 * 1024L
        private const val DEFAULT_MAX_DOWNLOAD_SIZE = (1* 1024 * 1024L).toLong()

        @Synchronized
        fun getInstance(
            appContext: Context,
            cacheDirectory: String = "/dash_cache",
            cacheEvictor: CacheEvictor? = null,
            cacheStreamKey: ArrayList<StreamKey>? = null
        ): DashCacheManager = cacheManager ?: DashCacheManager(
            appContext,
            cacheDirectory,
            cacheEvictor ?: LeastRecentlyUsedCacheEvictor(DEFAULT_CACHE_SIZE),
            cacheStreamKey ?: defaultStreamKeys
        ).apply { cacheManager = this }
    }

}