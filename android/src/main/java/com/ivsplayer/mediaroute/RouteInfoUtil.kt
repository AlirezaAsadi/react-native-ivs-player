package com.ivsplayer.mediaroute

import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice

object RouteInfoUtil {

    fun isVideoCapable(routeInfo: MediaRouter.RouteInfo): Boolean {
        val castDevice = CastDevice.getFromBundle(routeInfo.extras)
        return castDevice?.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT) ?: false
    }
}