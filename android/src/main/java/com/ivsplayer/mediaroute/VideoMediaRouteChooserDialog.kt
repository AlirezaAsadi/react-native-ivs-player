package com.ivsplayer.mediaroute

import android.content.Context
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.media.MediaRouter
import com.ivsplayer.mediaroute.RouteInfoUtil.isVideoCapable


class VideoMediaRouteChooserDialog(context: Context?, theme: Int) :
    MediaRouteChooserDialog(context!!, theme) {
    override fun onFilterRoute(routeInfo: MediaRouter.RouteInfo): Boolean {
        if (!isVideoCapable(routeInfo)) {
            return false
        }

        return super.onFilterRoute(routeInfo)
    }
}
