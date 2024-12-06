import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.amazonaws.ivs.player.Cue
import com.amazonaws.ivs.player.Player
import com.amazonaws.ivs.player.PlayerException
import com.amazonaws.ivs.player.PlayerView
import com.amazonaws.ivs.player.Quality
import java.nio.ByteBuffer

class CustomPlayerLayout(context: Context, private val playerView: PlayerView) : FrameLayout(context) {

    private var aspectRatio: Double = 1.77777777778 // 16:9

    private val mLayoutRunnable = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        layout(left, top, right, bottom)
    }

    init {
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        playerView.player.addListener(object : Player.Listener() {
            override fun onVideoSizeChanged(videoWidth: Int, videoHeight: Int) {
                Log.d(TAG, "Video size changed: width=$videoWidth, height=$videoHeight")
                val newAspectRatio = videoWidth * 1.0 / videoHeight
                if(aspectRatio != newAspectRatio) {
                    Log.d(TAG, "Aspect ratio changed: $aspectRatio -> $newAspectRatio")
                    aspectRatio = newAspectRatio
                    requestLayout() // Trigger a relayout with the new video dimensions
                }
            }

            override fun onStateChanged(state: Player.State) {
                Log.d(TAG, "Player state changed: $state")
            }

            override fun onError(error: PlayerException) {
                Log.e(TAG, "Player error: ${error.message}")
            }

            override fun onCue(cue: Cue) {
                Log.d(TAG, "Cue received: $cue")
            }

            override fun onDurationChanged(duration: Long) {
                Log.d(TAG, "Duration changed: $duration ms")
            }

            override fun onRebuffering() {
                Log.d(TAG, "Rebuffering event")
            }

            override fun onSeekCompleted(position: Long) {
                Log.d(TAG, "Seek completed to position: $position")
            }

            override fun onQualityChanged(quality: Quality) {
                Log.d(TAG, "Quality changed: $quality")
            }

            override fun onAnalyticsEvent(name: String, properties: String) {
                Log.d(TAG, "Analytics event: $name, properties: $properties")
            }

            override fun onMetadata(mediaType: String, data: ByteBuffer) {
                Log.d(TAG, "Metadata received: type=$mediaType, dataSize=${data.remaining()}")
            }

            override fun onNetworkUnavailable() {
                Log.d(TAG, "Network unavailable")
            }
        })
    }


    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val containerWidth = right - left
        val containerHeight = bottom - top

        // Calculate aspect ratios
        val videoAspectRatio = aspectRatio
        val containerAspectRatio = containerWidth.toFloat() / containerHeight

        // Calculate scaled dimensions
        val (scaledWidth, scaledHeight) = if (videoAspectRatio > containerAspectRatio) {
            // Video is wider than container
            val width = containerWidth
            val height = (width / videoAspectRatio).toInt()
            width to height
        } else {
            // Video is taller than container
            val height = containerHeight
            val width = (height * videoAspectRatio).toInt()
            width to height
        }

        // Center the scaled video within the container
        val leftOffset = (containerWidth - scaledWidth) / 2
        val topOffset = (containerHeight - scaledHeight) / 2

        playerView.layout(
            leftOffset,
            topOffset,
            leftOffset + scaledWidth,
            topOffset + scaledHeight
        )
        if(changed) {
            post(mLayoutRunnable)
        }

        Log.d(TAG, "Container layout: left=$left, top=$top, right=$right, bottom=$bottom")
        Log.d(TAG, "PlayerView layout: left=$leftOffset, top=$topOffset, width=$scaledWidth, height=$scaledHeight")
    }

    companion object {
        private const val TAG = "CustomPlayerLayout"
    }
}
