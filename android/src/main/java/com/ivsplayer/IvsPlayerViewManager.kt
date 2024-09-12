package com.ivsplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.amazonaws.ivs.player.PlayerView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class IvsPlayerViewManager : SimpleViewManager<View>() {
  override fun getName() = "IvsPlayerView"

  @SuppressLint("ClickableViewAccessibility")
  override fun createViewInstance(reactContext: ThemedReactContext): View {
    val frameLayout = FrameLayout(reactContext).apply {
      // Apply the Layout Parameters to frameLayout
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    val playerView = PlayerView(reactContext)

    // Set the player view in the singleton
    PlayerViewShared.mPlayerView = playerView
    PlayerViewShared.parentLayout = frameLayout
    PlayerViewShared.parentLayoutId = frameLayout.id
//    Log.i("RNPlayer", "XXXX")
//    Log.i("RNPlayer", playerView.player.state.toString())
//

    frameLayout.removeView(playerView)
    frameLayout.addView(playerView)

    // Override the onTouchEvent to always return false
//    playerView.setOnTouchListener { _, _ ->
//      // Return false to let React Native handle the touch event
//      Log.i("RNPlayer", "playerView is touched")
//      false
//    }

    return frameLayout
  }

}
