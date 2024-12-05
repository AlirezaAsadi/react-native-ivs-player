package com.ivsplayer

import CustomPlayerLayout
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.amazonaws.ivs.player.PlayerView
import com.amazonaws.ivs.player.ResizeMode
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

class IvsPlayerViewManager : SimpleViewManager<View>() {
  override fun getName() = "IvsPlayerView"

  @SuppressLint("ClickableViewAccessibility")
  override fun createViewInstance(reactContext: ThemedReactContext): View {

    // Remove any existing playerView
    if (PlayerViewShared.playerView != null) {
      (PlayerViewShared.playerView?.parent as? ViewGroup)?.removeView(PlayerViewShared.playerView)
    }

    // Retrieve existing playerView instance or create a new on e
    val playerView = PlayerViewShared.playerView ?: PlayerView(reactContext).apply {
      resizeMode = ResizeMode.FILL
      controlsEnabled = false
    }
    val customPlayerLayout = CustomPlayerLayout(reactContext, playerView)

    // Save the instance to the shared object
    PlayerViewShared.playerView = playerView
    PlayerViewShared.parentLayout = customPlayerLayout;

    return customPlayerLayout
  }
}


