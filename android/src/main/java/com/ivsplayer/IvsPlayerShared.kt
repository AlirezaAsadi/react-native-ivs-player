package com.ivsplayer

import android.widget.FrameLayout
import com.amazonaws.ivs.player.PlayerView

object PlayerViewShared {
    var mPlayerView: PlayerView? = null
    var parentLayout: FrameLayout? = null;
    var parentLayoutId: Int = 0;
}