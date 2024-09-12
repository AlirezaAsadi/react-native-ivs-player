package com.ivsplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.amazonaws.ivs.player.PlayerView
import com.amazonaws.ivs.player.ResizeMode
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class IvsPlayerViewManager : SimpleViewManager<View>() {
  override fun getName() = "IvsPlayerView"

  @SuppressLint("ClickableViewAccessibility")
  override fun createViewInstance(reactContext: ThemedReactContext): View {
    val playerView = PlayerView(reactContext)

    // Ensure that PlayerView fills the parent
    playerView.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    playerView.resizeMode = ResizeMode.FIT;
    PlayerViewShared.mPlayerView = playerView
    playerView.setBackgroundColor(Color.BLACK)

    return playerView
  }
}
