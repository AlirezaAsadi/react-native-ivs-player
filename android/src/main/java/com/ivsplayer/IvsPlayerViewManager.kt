package com.ivsplayer

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import com.amazonaws.ivs.player.PlayerView
import com.amazonaws.ivs.player.ResizeMode
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

class IvsPlayerViewManager : SimpleViewManager<View>() {
  override fun getName() = "IvsPlayerView"

  @SuppressLint("ClickableViewAccessibility")
  override fun createViewInstance(reactContext: ThemedReactContext): View {
    val parentView = FrameLayout(reactContext)

    // Remove any existing playerView
    if (PlayerViewShared.playerView != null) {
      (PlayerViewShared.playerView?.parent as? ViewGroup)?.removeView(PlayerViewShared.playerView)
    }

    // Retrieve existing playerView instance or create a new on e
    val playerView = PlayerViewShared.playerView ?: PlayerView(reactContext).apply {
      resizeMode = ResizeMode.FIT
      controlsEnabled = false
    }
    parentView.addView(playerView)

    // Get the player(surface view) and re-attach it to correctly calculate the size
    val surfaceView = playerView.getChildAt(0) as? SurfaceView
    if (surfaceView != null) {
      playerView.removeViewAt(0)
      playerView.addView(surfaceView, 0, ViewGroup.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
      ))
    }

    // Save the instance to the shared object
    PlayerViewShared.playerView = playerView
    PlayerViewShared.parentLayout = parentView;

    return parentView
  }
}


