import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.amazonaws.ivs.player.PlayerView

class CustomPlayerLayout(context: Context, val playerView: PlayerView) : FrameLayout(context) {

    private val mLayoutRunnable = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        layout(left, top, right, bottom)
    }

    init {
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }


    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val containerWidth = right - left
        val containerHeight = bottom - top

        // Calculate aspect ratios
        val videoAspectRatio = 1.77777777778 // 16:9
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
