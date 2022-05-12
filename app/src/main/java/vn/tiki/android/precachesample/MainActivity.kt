package vn.tiki.android.precachesample

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelection
import com.google.android.exoplayer2.util.Assertions
import io.reactivex.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    val player by lazy {
        SimpleExoPlayer.Builder(this)
            .setTrackSelector(DashCacheManager.getInstance(applicationContext).trackSelector)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                addListener(object : Player.EventListener {
                    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        super.onPlayerStateChanged(playWhenReady, playbackState)
                        if (Player.STATE_BUFFERING == playbackState) {
                            progressBar?.visibility = View.VISIBLE
                        } else {
                            progressBar?.visibility = View.GONE
                        }
                        Log.d(
                            TAG,
                            "onPlayerStateChanged. playWhenReady: $playWhenReady, playbackState: $playbackState)"
                        )
                    }

                    override fun onPlayerError(error: ExoPlaybackException) {
                        super.onPlayerError(error)
                        Log.d(TAG, "onPlayerError")
                        error.printStackTrace()
                    }

                    override fun onTracksChanged(
                        trackGroups: TrackGroupArray,
                        trackSelections: TrackSelectionArray
                    ) {
                        super.onTracksChanged(trackGroups, trackSelections)
                        if (trackGroups == null || trackGroups.isEmpty || trackGroups[0]?.length ?: 0 == 0) return
                        Log.d(TAG, "TrackChanged: ${trackGroups.length}  ${trackGroups[0].getFormat(0)}")
                    }

                })
            }
    }

    var pagerLastItem = MutableLiveData(0)

    private var i = -1
    val videoDatas2 = listOf(
        VideoData(
            id = i++,
            streamUrl = "https://edge.tikicdn.com/data/hls/902297/1/3/1478/manifest.m3u8"
        ),
        VideoData(
            id = i++,
            streamUrl = "https://edge.tikicdn.com/data/hls/901262/1/3/1478/manifest.m3u8"
        ),
        VideoData(
            id = i++,
            streamUrl = "https://edge.tikicdn.com/data/hls/901261/1/3/1478/manifest.m3u8"
        )
    )
    val videoDatas = listOf(
        VideoData(
            id = i++,
            streamUrl = "http://rdmedia.bbc.co.uk/dash/ondemand/bbb/2/client_manifest-high_profile-common_init.mpd"
        ),
        VideoData(
            id = i++,
            streamUrl = "https://bitmovin-a.akamaihd.net/content/MI201109210084_1/mpds/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.mpd"
        ),

        VideoData(
            id = i++,
            streamUrl = "https://dash.akamaized.net/dash264/TestCasesHD/2b/qualcomm/1/MultiResMPEG2.mpd"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager.apply {
            adapter = VideoPagerAdapter(this@MainActivity).apply {
                showDetails = this@MainActivity.videoDatas
            }
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 3
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        if (currentItem != pagerLastItem.value) {
                            pagerLastItem.value = currentItem
                        }
                        player.playWhenReady = true
                    } else {
                        player.playWhenReady = false
                    }
                }
            })
        }

        lowBitrate?.setOnClickListener {
            DashCacheManager.getInstance(applicationContext).forceLowestBitrate()
        }

        resetBitrate?.setOnClickListener {
            DashCacheManager.getInstance(applicationContext).resetBitrateSelection()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        player.release()
        DashCacheManager.getInstance(applicationContext).reset()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
