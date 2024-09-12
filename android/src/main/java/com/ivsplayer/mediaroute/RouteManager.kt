package com.ivsplayer.mediaroute

import android.content.Context
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.RouteInfo

class RouteManager(context: Context) {

    private val mediaRouter: MediaRouter = MediaRouter.getInstance(context)
    private val listeners: MutableList<RouteChangeListener> = mutableListOf()

    init {
        val routeSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()

        mediaRouter.addCallback(
            routeSelector,
            object : MediaRouter.Callback() {
                override fun onRouteChanged(router: MediaRouter, route: RouteInfo) {
                    notifyRouteChanged()
                }
            }
        )
    }

    private fun getAvailableRoutes(): List<RouteInfo> {
        return mediaRouter.routes
    }

    private fun getAvailableVideoRoutes(): List<RouteInfo> {
        val availableRoutes = mediaRouter.routes
        val videoRoutes = mutableListOf<RouteInfo>()

        for (routeInfo in availableRoutes) {
            if (RouteInfoUtil.isVideoCapable(routeInfo)) {
                videoRoutes.add(routeInfo)
            }
        }

        return videoRoutes
    }

    fun hasVideoCapableRoutes(): Boolean {
        return getAvailableVideoRoutes().isNotEmpty()
    }

    fun addRouteChangeListener(listener: RouteChangeListener?) {
        listener?.let {
            if (!listeners.contains(it)) {
                listeners.add(it)
            }
        }
    }

    fun removeRouteChangeListener(listener: RouteChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyRouteChanged() {
        val validRoutes = getAvailableRoutes()
        for (listener in listeners) {
            listener.onRouteChanged(validRoutes)
        }
    }

    interface RouteChangeListener {
        fun onRouteChanged(routes: List<RouteInfo>)
    }
}
