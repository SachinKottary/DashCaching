package vn.tiki.android.precachesample

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper
import com.google.android.exoplayer2.offline.StreamKey
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.offline.DashDownloader
import kotlinx.android.synthetic.main.fragment_video.*
import kotlinx.coroutines.*

class VideoFragment : Fragment() {
    private val mainActivity: MainActivity
        get() = activity as MainActivity

    private val player: SimpleExoPlayer
        get() = mainActivity.player

/*    private val cacheManager: DashCacheManager
       get() = mainActivity.dashCacheManager*/

    private val uri by lazy { Uri.parse(mainActivity.videoDatas[position].streamUrl) }

    private val cacheStreamKeys = arrayListOf(
        StreamKey(0, 0),
        /*StreamKey(1, 1),
        StreamKey(2, 1),
        StreamKey(3, 1),
        StreamKey(4, 1)*/
    )

    private val mediaSource: MediaSource by lazy {
        DashMediaSource.Factory(DashCacheManager.getInstance(requireContext()).cacheDataSourceFactory)
            //.setStreamKeys(cacheStreamKeys)
            .createMediaSource(uri)
/*        HlsMediaSource.Factory(dataSourceFactory)
            .setStreamKeys(cacheStreamKeys)
            .setAllowChunklessPreparation(true)
            .createMediaSource(uri)*/
    }

    private var position = -2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = arguments?.getInt(KEY_POSITION) ?: -2

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity.pagerLastItem.observe(viewLifecycleOwner, {
            if (it == position) {

            }
        })

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            DashCacheManager.getInstance(requireContext()).startCaching(uri.toString())
        }
        playVideo?.setOnClickListener {
            with(DashCacheManager.getInstance(requireContext())) {
                stopCaching(uri.toString())
                resetBitrateSelection()
                forceLowestBitrate()
            }
            Log.d(TAG, "Cancel preload at position: $position")
            player.stop(true)
            player.setVideoSurfaceView(renderView)
            player.prepare(mediaSource)
            player.playWhenReady = true
        }
    }

    companion object {
        const val KEY_POSITION = "KEY_POSITION"
        private const val PRE_CACHE_SIZE: Long = (0.5 * 1024 * 1024L).toLong()
        private const val TAG = "VideoFragment"
    }
}
