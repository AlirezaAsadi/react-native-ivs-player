package com.ivsplayer

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.RouteInfo
import com.amazonaws.ivs.player.Cue
import com.amazonaws.ivs.player.Player
import com.amazonaws.ivs.player.Player.State
import com.amazonaws.ivs.player.PlayerException
import com.amazonaws.ivs.player.PlayerView
import com.amazonaws.ivs.player.Quality
import com.amazonaws.ivs.player.ResizeMode
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.ivsplayer.mediaroute.RouteManager
import com.ivsplayer.mediaroute.VideoMediaRouteChooserDialogFragment
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask


class IvsPlayerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    init {
        // Register the lifecycle listener
        reactContext.addLifecycleEventListener(this)
    }

    private val context: Context = reactContext.applicationContext
    private val PLUGIN_VERSION = "0.13.34"
    private val TAG = "ReactNativeIVSPlayer"


    private val mainPiPFrameLayoutId = 257
    private var mPlayerView: PlayerView? = null
    private var marginButton = 20
    private var mediaRouteButton: MediaRouteButton? = null
    private var currentStateDisplayButton = true

    private val size = Point()
    private val aspectRatio = Rational(16, 9)
    private var mMediaInfo: MediaInfo? = null
    private var mMediaMetadata: MediaMetadata? = null
    private var autoPlay = false

    private var lastSeekPosBeforeSrcChange: Long? = null

    private var expandButton: ImageView? = null
    private var closeButton: ImageView? = null
    private var playPauseButton: ImageView? = null
    private var shadowView: View? = null
    private var expandAnimation: Animation? = null
    private var collapseAnimation: Animation? = null

    private var gestureDetector: GestureDetector? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var isFullScreen = false
    private var mSessionManagerListener: SessionManagerListener<CastSession>? = null

    var title: String = ""
    var description: String = ""
    var cover: String = ""
    private var mRouteManager: RouteManager? = null

    private val METADATA_KEY_STREAM_ID = "ee.forgr.ivsplayer.METADATA_KEY_STREAM_ID"
    private val METADATA_KEY_CHANNEL_SLUG = "ee.forgr.ivsplayer.METADATA_KEY_CHANNEL_SLUG"
    private val currentReactContext: ReactContext?
        get() = reactApplicationContext

    override fun getName(): String {
        return "IvsPlayerViewManager"
    }

    fun getContext(): Context {
        return context
    }


    // Implement the lifecycle methods
    override fun onHostResume() {
        Log.d(TAG, "onHostResume")
    }
    override fun onHostDestroy() {
        Log.d(TAG, "onHostDestroy")
        handleOnDestroy()
    }

    override fun onHostPause() {
        Log.d(TAG, "onHostPause")
    }

    private fun handleOnDestroy() {
        Log.d(TAG, "handleOnDestroy")
        mPlayerView?.player?.release()
        mPlayerView = null
    }



    private fun enterPipMode() {
        Log.d(TAG, "enterPipMode")
        val pipSupported = PictureInPictureUtil.isSupportPictureInPicture(reactApplicationContext)
        Log.d(TAG, "enterPipMode pipSupported: $pipSupported")

        mPlayerView?.let {
            Log.d(TAG, "it.player.state is ${it.player.state}")
            if (it.player.state != State.PLAYING) {
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val activity = currentActivity as? AppCompatActivity
                val supportsPiP =
                    activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                        ?: false

                Log.d(TAG, "enterPipMode supportsPiP: $supportsPiP, lifecycle.currentState: ${activity?.lifecycle?.currentState}")
                if (activity?.isInPictureInPictureMode == true) {
                    Log.d(TAG, "enterPipMode is already InPictureInPictureMode")
                } else if (supportsPiP && activity?.lifecycle?.currentState in listOf(Lifecycle.State.STARTED, Lifecycle.State.RESUMED)) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(aspectRatio)
                        .build()

                    activity?.enterPictureInPictureMode(params)

                    currentActivity?.runOnUiThread {
                        togglePip(true)
                    }
                    Log.d(TAG, "didWorked")
                }
            }
        }
    }


    private fun updatePlayerViewParent(newParent: ViewGroup) {
        Log.d(TAG, "updatePlayerViewParent")
        (mPlayerView?.parent as ViewGroup?)?.removeView(mPlayerView)
        val width = ViewGroup.LayoutParams.MATCH_PARENT; //convertDpToPixel(convertPixelsToDp(size.x.toFloat()).toDouble().toFloat()).toInt()
        val height = ViewGroup.LayoutParams.MATCH_PARENT; //convertDpToPixel(convertPixelsToDp(calcHeight(size.x).toFloat())).toInt()

        if (mPlayerView?.parent == null) {
            newParent.addView(mPlayerView)
        }
        mPlayerView?.layoutParams?.width = width
        mPlayerView?.layoutParams?.height = height
        mPlayerView?.invalidate();
    }

    private fun togglePip(pip: Boolean) {
        Log.d(TAG, "togglePip new pip status is $pip")

        val mainPiPFrameLayout = currentActivity?.findViewById<View>(mainPiPFrameLayoutId)

        if (!pip) {
            mainPiPFrameLayout?.visibility = View.GONE
            PlayerViewShared.parentLayout?.let {
                updatePlayerViewParent(it)
            }
            sendEvent("expandPip")
        } else {
            mainPiPFrameLayout?.visibility = View.VISIBLE
            mainPiPFrameLayout?.let {
                updatePlayerViewParent(it as ViewGroup)
            }

            sendEvent("startPip")
        }

        dismissCastRouteChooserDialog()
    }

    fun sendEvent(eventName: String, params: ReadableMap? = null) {
        currentReactContext
            ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, params)
    }

    private fun dismissCastRouteChooserDialog() {
        // Dismiss any cast route chooser dialog if open
        val activity = currentActivity as? AppCompatActivity
        val dialog = activity?.supportFragmentManager
            ?.findFragmentByTag("android.support.v7.mediarouter:MediaRouteChooserDialogFragment")
        (dialog as? DialogFragment)?.dismiss()
    }

    private fun isAppInPiPMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            currentActivity?.isInPictureInPictureMode() ?: false
        } else {
            false
        }
    }

    private fun closePip() {
        val ret = WritableNativeMap()
        togglePip(false)
        mPlayerView?.player?.pause()

        /**
         * This event will be fired when the native PiP (not the overlay one) is
         * closed while the app is in the background state.
         *
         * As it differs from when the user interacts with overlay PiP's close
         * button (which fires the "closePip" event), this gets the "stopPip" name
         */
        sendEvent("stopPip", ret)
        Log.d(TAG, "closePip")
    }

    // Function to calculate 16:9 ratio height
    private fun calcHeight(width: Int): Int {
        return (width * 9.0 / 16.0).toInt()
    }

    private fun addPipListener() {
        val activity = currentActivity as? AppCompatActivity
        activity
            ?.addOnPictureInPictureModeChangedListener { pictureInPictureModeChangedInfo ->
                val lifecycleState = activity.lifecycle.currentState
                Log.d(TAG, "lifecycleState is $lifecycleState")
                Log.d(TAG, "isInPictureInPictureMode is ${pictureInPictureModeChangedInfo.isInPictureInPictureMode}")
                when (lifecycleState) {
                    Lifecycle.State.CREATED -> {
                        //when user click on Close button of PIP this will trigger.
                        closePip()
                    }
                    Lifecycle.State.STARTED -> {
                        //when PIP maximize this will trigger
                        Log.d(TAG, "Closing ${pictureInPictureModeChangedInfo.isInPictureInPictureMode}")
                        // But only turn it off, as turning on is already triggered by setPip
                        if (!pictureInPictureModeChangedInfo.isInPictureInPictureMode) {
                            activity.runOnUiThread {
                                togglePip(false)
                            }
                        }
                    }
                    else -> {
                        // Handle other lifecycle states if necessary
                    }
                }
            }
    }

    private fun addPlayerListener() {
        mPlayerView?.player?.addListener(object : Player.Listener() {
            override fun onStateChanged(state: State) {
                if (getIsCastSessionActive()) return

                when (state) {
                    State.READY -> if (autoPlay) mPlayerView?.player?.play()
                    State.PLAYING -> applyLastSeekPosition()
                    else -> { /* Handle other states if necessary */
                    }
                }

                if (state == State.PLAYING && mPlayerView?.parent == null) {
                    val mainPiPFrameLayout =
                        currentActivity?.findViewById<FrameLayout>(mainPiPFrameLayoutId)
                    mainPiPFrameLayout?.addView(mPlayerView)
                    Log.d(TAG, "mainPiPFrameLayout")
                }

                Log.d(TAG, "addPlayerListener.onStateChanged, state: $state")
                notifyPlayerStatus()
            }

            override fun onCue(cue: Cue) {
                Log.d(TAG, "Player.Listener.onCue: $cue")
                val ret = WritableNativeMap().apply {
                    putString("cue", cue.toString()) // Assuming cue is converted to string
                }
                sendEvent("onCue", ret)
            }

            override fun onDurationChanged(duration: Long) {
                Log.d(TAG, "Player.Listener.onDurationChanged: $duration")
                val ret = WritableNativeMap().apply {
                    putDouble("duration", duration.toDouble())
                }
                sendEvent("onDuration", ret)
            }

            override fun onError(exception: PlayerException) {
                Log.e(TAG, "Player.Listener.onError: $exception")
                val ret = WritableNativeMap().apply {
                    putString(
                        "error",
                        exception.toString()
                    ) // Assuming exception is converted to string
                }
                sendEvent("onError", ret)
            }

            override fun onRebuffering() {
                Log.d(TAG, "Player.Listener.onRebuffering")
                val ret = WritableNativeMap()
                sendEvent("onRebuffering", ret)
            }

            override fun onSeekCompleted(time: Long) {
                Log.d(TAG, "Player.Listener.onSeekCompleted: $time")
                val ret = WritableNativeMap().apply {
                    putDouble("position", time / 1000.0)
                }
                sendEvent("onSeekCompleted", ret)
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                Log.d(TAG, "Player.Listener.onVideoSizeChanged: ${width}x$height")
                val ret = WritableNativeMap().apply {
                    putInt("width", width)
                    putInt("height", height)
                }
                sendEvent("onVideoSize", ret)
            }

            override fun onQualityChanged(quality: Quality) {
                Log.d(TAG, "Player.Listener.onQualityChanged: $quality")
                val ret = WritableNativeMap().apply {
                    putString(
                        "quality",
                        quality.toString()
                    ) // Assuming quality is converted to string
                }
                sendEvent("onQuality", ret)
            }
        })
    }

    private fun loadUrl(contentUrl: String) {
        createMediaInfo(contentUrl)

        if (getIsCastSessionActive()) {
            val castSession = getCastSession()
            loadCastSessionMedia(castSession)
            notifyCastStatus()
        } else {
            Log.d(TAG, "loadUrl: $contentUrl")
            mPlayerView?.player?.load(Uri.parse(contentUrl))
        }
    }

    private fun cyclePlayer(prevContentUrl: String, nextUrl: String) {
        var mainPiPFrameLayout = currentActivity?.findViewById<FrameLayout>(mainPiPFrameLayoutId)
        Log.d(TAG, "cyclePlayer mainPiPFrameLayout: $mainPiPFrameLayout")

        if (mainPiPFrameLayout != null) {
            Log.d(TAG, "FrameLayout for VideoPicker already exists")

            if (mPlayerView?.parent != null) {
                Log.d(TAG, "playerView is already in mainPiPFrameLayout")
                // check if playerView is already in mainPiPFrameLayout
                if (prevContentUrl == nextUrl) {
                    loadUrl(nextUrl)
                    return
                }
                mainPiPFrameLayout.removeView(mPlayerView)
                loadUrl(nextUrl)
            } else {
                Log.d(TAG, "playerView is not in mainPiPFrameLayout")
                // add playerView to mainPiPFrameLayout
                mainPiPFrameLayout.addView(mPlayerView)
                loadUrl(nextUrl)
            }
        } else {
            // Initialize a new FrameLayout as container for fragment
            mainPiPFrameLayout = FrameLayout(currentActivity?.applicationContext as Context).apply {
                id = mainPiPFrameLayoutId
                // Apply the Layout Parameters to frameLayout
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            mainPiPFrameLayout.visibility = View.GONE

            val finalMainPiPFrameLayout = mainPiPFrameLayout

            currentActivity?.runOnUiThread {
                val rootView = currentActivity?.findViewById<ViewGroup>(android.R.id.content)
                rootView?.addView(finalMainPiPFrameLayout)
                loadUrl(nextUrl)
            }
        }
    }

    // Function to get the root view of the current activity
    private fun getRootView(): View? {
        val activity = currentActivity
        return activity?.window?.decorView?.rootView
    }


    private val progressListener = RemoteMediaClient.ProgressListener { progress, duration ->
        // Check player state within this method since it gets called regularly during playback
        notifyPlayerStatus()
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            Log.d(TAG, "RemoteMediaClient.Callback.onStatusUpdated")
            notifyCastStatus()
        }
    }

    private fun loadCastSessionMedia(castSession: CastSession?) {
        if (castSession == null) {
            Log.e(TAG, "loadCastSessionMedia castSession is null")
            return
        }

        Log.d(TAG, "loadCastSessionMedia ${mMediaInfo?.contentUrl}")

        val mediaLoadOptions = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(mPlayerView?.player?.position ?: 0)
            .build()

        getRemoteMediaClient()
            ?.load(mMediaInfo as MediaInfo, mediaLoadOptions)
            ?.setResultCallback { result ->
                if (result.status.isSuccess) {
                    Log.d(TAG, "Media loaded successfully")
                } else {
                    Log.e(TAG, "Error loading media: ${result.status.statusCode}")
                }
            }

        val remoteMediaClient = getRemoteMediaClient()
        remoteMediaClient?.addProgressListener(progressListener, 1000)
        remoteMediaClient?.registerCallback(remoteMediaClientCallback)
    }

    private fun setupCastListener() {
        mSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(castSession: CastSession, sessionId: String) {
                Log.d(TAG ,"SessionManagerListener.onSessionStarted ${mMediaInfo?.contentUrl}"
                )

                loadCastSessionMedia(castSession)

                mPlayerView?.player?.pause()
                notifyCastStatus()
            }

            override fun onSessionStarting(castSession: CastSession) {
                Log.d(TAG, "cast onSessionStarting")
                notifyCastStatus()
            }

            override fun onSessionSuspended(castSession: CastSession, reason: Int) {
                Log.d(TAG, "SessionManagerListener.onSessionSuspended: $castSession")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                Log.d(TAG, "SessionManagerListener.onSessionResumed: $session")
                notifyCastStatus()
            }

            override fun onSessionResuming(castSession: CastSession, sessionId: String) {
                Log.d(
                    "ReactNativeIVSPlayer",
                    "SessionManagerListener.onSessionResuming: $castSession sessionId: $sessionId"
                )
                notifyCastStatus()
            }

            override fun onSessionStartFailed(castSession: CastSession, error: Int) {
                Log.e(
                    "ReactNativeIVSPlayer",
                    "SessionManagerListener.onSessionStartFailed: $castSession error: $error"
                )
                notifyCastStatus()
            }

            override fun onSessionEnded(castSession: CastSession, error: Int) {
                Log.d(
                    "ReactNativeIVSPlayer",
                    "SessionManagerListener.onSessionEnded: $castSession error: $error"
                )

                notifyCastStatus()
                mPlayerView?.player?.play()
            }

            override fun onSessionEnding(castSession: CastSession) {
                Log.d(TAG, "SessionManagerListener.onSessionEnding: $castSession")

                castSession.remoteMediaClient?.apply {
                    removeProgressListener(progressListener)
                    unregisterCallback(remoteMediaClientCallback)
                }

                val mediaInfo = getMediaInfoFromCastOrLocal()

                mediaInfo?.let {
                    val contentUrl = it.contentUrl
                    mPlayerView?.player?.apply {
                        load(Uri.parse(contentUrl))
                        pause()
                    }
                }

                notifyCastStatus()
            }

            override fun onSessionResumeFailed(castSession: CastSession, error: Int) {
                Log.e(
                    "ReactNativeIVSPlayer",
                    "SessionManagerListener.onSessionResumeFailed: $castSession error: $error"
                )
                notifyCastStatus()
            }
        }
    }

    private fun createMediaInfo(contentUrl: String?) {
        val localContentUrl = contentUrl ?: mMediaInfo?.contentUrl
        ?: throw IllegalStateException("contentUrl is null")

        val mediaInfoBuilder = MediaInfo.Builder(localContentUrl)
            .setContentUrl(localContentUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("application/vnd.apple.mpegurl")

        mMediaMetadata?.let {
            mediaInfoBuilder.setMetadata(it)
        }

        mMediaInfo = mediaInfoBuilder.build()

        Log.d(TAG, "createMediaInfo ${mMediaInfo?.contentUrl}")
    }

    @ReactMethod
    fun getPluginVersion(promise: Promise) {
        try {
            val ret = WritableNativeMap().apply {
                putString("version", PLUGIN_VERSION)
            }
            promise.resolve(ret)
        } catch (e: Exception) {
            promise.reject("Could not get plugin version", e)
        }
    }

    @ReactMethod
    fun cast(promise: Promise) {
        Log.d(TAG, "cast")
        performCastClick()
        promise.resolve(null)
    }

    private fun performCastClick() {
        currentActivity?.runOnUiThread {
            val castContext = getCastContext()

            Log.d(TAG, "CreateCast")
            if (castContext == null) {
                Log.d(TAG, "CastContext is null")
            } else {
                if (castContext.castState == CastState.NO_DEVICES_AVAILABLE) {
                    Log.d(TAG, "No devices available for casting")
                } else {
                    Log.d(TAG, "Devices available for casting")
                }
            }

            if (getHasVideoCapableRoutes()) {
                mediaRouteButton?.performClick()
                Log.d(TAG, "cast performClick")
            } else {
                Log.w(TAG, "cast NO video capable routes")
            }
        }
    }

    private fun getCastPlayerState(): State {
        val remoteMediaClient = getCastSession()?.remoteMediaClient
        var castPlayerState = State.ENDED

        remoteMediaClient?.let {
            val playbackStatus = it.playerState
            castPlayerState = when (playbackStatus) {
                MediaStatus.PLAYER_STATE_IDLE, MediaStatus.PLAYER_STATE_PAUSED -> State.IDLE
                MediaStatus.PLAYER_STATE_PLAYING -> State.PLAYING
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> State.BUFFERING
                else -> State.ENDED
            }
        }

        Log.d(TAG, "getCastPlayerState, state: $castPlayerState")
        return castPlayerState
    }

    @ReactMethod
    fun getCastStatus(promise: Promise) {
        val castStatusJSObject = getCastStatusJSObject()

        Log.d(TAG, "getCastStatus: $castStatusJSObject")
        promise.resolve(castStatusJSObject)
    }

    private fun notifyCastStatus() {
        val castStatusJSObject = getCastStatusJSObject()

        Log.d(TAG, "notifyCastStatus: $castStatusJSObject")

        sendEvent("onCastStatus", castStatusJSObject)
    }

    private fun getCastStatusJSObject(): WritableNativeMap {
        val jsObject = WritableNativeMap()

        var streamId: String? = null
        var channelSlug: String? = null

        val mediaInfo = getMediaInfoFromCastOrLocal()

        mediaInfo?.metadata?.let {
            streamId = it.getString(METADATA_KEY_STREAM_ID)
            channelSlug = it.getString(METADATA_KEY_CHANNEL_SLUG)
        }

        jsObject.apply {
            putString("streamId", streamId)
            putString("channelSlug", channelSlug)
            putBoolean("isActive", getIsCastSessionActive())
            putString("connectionState", getRouteConnectionState())
            putString("routeName", getCurrentRouteName())
            putBoolean("muted", getMuted())
            putBoolean("hasVideoCapableRoutes", getHasVideoCapableRoutes())
        }

        return jsObject
    }

    private fun getMediaInfoFromCastOrLocal(): MediaInfo? {
        var castMediaInfo: MediaInfo? = try {
            getResultFromUiThread {
                getRemoteMediaClient()?.mediaInfo
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getMediaInfoFromCastOrLocal", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getMediaInfoFromCastOrLocal", e)
        }

        if (castMediaInfo == null) {
            castMediaInfo = mMediaInfo
        }

        return castMediaInfo
    }

    private fun notifyPlayerStatus() {
        val state: State = if (getIsCastSessionActive()) {
            getCastPlayerState().also {
                Log.d(TAG, "notifyPlayerStatus from cast: $it")
            }
        } else {
            if (mPlayerView != null) {
                mPlayerView?.player?.state ?: State.IDLE
            } else {
                State.IDLE
            }.also {
                Log.d(TAG, "notifyPlayerStatus from player view: $it")
            }
        }

        val ret = WritableNativeMap().apply {
            putString("state", state.toString())
        }
        sendEvent("onState", ret)
    }

    private fun getIsCastSessionActive(): Boolean {
        return try {
            getResultFromUiThread {
                val castSession = getCastSession()
                castSession != null && castSession.isConnected
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "getIsCastSessionActive exception: ${e.message}")
            false
        } catch (e: InterruptedException) {
            Log.e(TAG, "getIsCastSessionActive exception: ${e.message}")
            false
        }
    }

    private fun getRouteConnectionState(): String {
        val connectionState: Int = try {
            getResultFromUiThread {
                getSelectedRoute().connectionState
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getRouteConnectionState", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getRouteConnectionState", e)
        }

        return when (connectionState) {
            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> "connected"
            RouteInfo.CONNECTION_STATE_CONNECTING -> "connecting"
            RouteInfo.CONNECTION_STATE_DISCONNECTED -> "disconnected"
            else -> "unknown"
        }
    }

    private fun getSelectedRoute(): RouteInfo {
        return try {
            getResultFromUiThread {
                getMediaRouter().selectedRoute
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getSelectedRoute", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getSelectedRoute", e)
        }
    }

    private fun getHasVideoCapableRoutes(): Boolean {
        return try {
            getResultFromUiThread {
                mRouteManager?.hasVideoCapableRoutes()
            } ?: false;
        } catch (e: ExecutionException) {
            throw IllegalStateException("getHasVideoCapableRoutes", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getHasVideoCapableRoutes", e)
        }
    }

    private fun getCastContext(): CastContext {
        return try {
            getResultFromUiThread {
                CastContext.getSharedInstance(context)
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getCastContext", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getCastContext", e)
        }
    }

    private fun getSessionManager(): SessionManager {
        return try {
            getResultFromUiThread {
                getCastContext().sessionManager
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getSessionManager", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getSessionManager", e)
        }
    }

    private fun getCastSession(): CastSession? {
        return try {
            getResultFromUiThread {
                getCastContext().sessionManager.currentCastSession
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getCastSession", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getCastSession", e)
        }
    }

    private fun getMediaRouter(): MediaRouter {
        return try {
            getResultFromUiThread {
                MediaRouter.getInstance(getContext())
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getMediaRouter", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getMediaRouter", e)
        }
    }

    private fun getCurrentRouteName(): String {
        return getSelectedRoute().name
    }

    private fun getRemoteMediaClient(): RemoteMediaClient? {
        val castSession = getCastSession() ?: return null

        return try {
            getResultFromUiThread {
                castSession.remoteMediaClient
            }
        } catch (e: ExecutionException) {
            throw IllegalStateException("getRemoteMediaClient", e)
        } catch (e: InterruptedException) {
            throw IllegalStateException("getRemoteMediaClient", e)
        }
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    fun <T> getResultFromUiThread(callable: Callable<T>): T {
        val futureTask = FutureTask(callable)
        currentActivity?.runOnUiThread(futureTask)
        return futureTask.get()
    }

    @ReactMethod
    fun create(options: ReadableMap, promise: Promise) {
        setupUI()

        val call = DefaultReadableMap(options)
        val playbackRate = call.getDouble("playbackRate", 1.0)
        if (playbackRate in 0.5..2.0) {
            mPlayerView?.player?.setPlaybackRate(playbackRate.toFloat())
        } else {
            mPlayerView?.player?.setPlaybackRate(1f)
        }

        getDisplaySize()
        val x = convertDpToPixel(call.getDouble("x", 0.0).toFloat()).toInt()
        val y = convertDpToPixel(call.getDouble("y", 0.0).toFloat()).toInt()
        val width = convertDpToPixel(call.getDouble("width", convertPixelsToDp(size.x.toFloat()).toDouble()).toFloat()).toInt()
        val height = convertDpToPixel(call.getDouble("height", convertPixelsToDp(calcHeight(size.x).toFloat()).toDouble()).toFloat()).toInt()

        Log.d(TAG, "create")

        val url = call.getString("url") ?: return promise.reject("url is required")
        val prevContentUrl = mMediaInfo?.contentUrl ?: url

        autoPlay = call.getBoolean("autoPlay", false)
        val toBack = call.getBoolean("toBack", false)

        var streamId = call.getString("streamId")
        if (streamId == null) {
            streamId = call.getString("id")
        }

        var thumbnailUrl = call.getString("thumbnailUrl")
        if (thumbnailUrl == null) {
            thumbnailUrl = call.getString("thumbnail")
        }

        createMediaMetaData(
            streamId ?: "",
            call.getString("channelSlug", "") ?: "",
            call.getString("title", "") ?: "",
            call.getString("description", "") ?: "",
            thumbnailUrl ?: ""
        )

        currentActivity?.runOnUiThread {
            cyclePlayer(prevContentUrl, url)
        }
    }

    private fun createMediaMetaData(
        streamId: String,
        channelSlug: String,
        title: String,
        description: String,
        thumbnailUrl: String
    ) {
        mMediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)

        if (streamId.isEmpty()) {
            throw IllegalStateException("streamId is empty")
        }

        mMediaMetadata?.putString(METADATA_KEY_STREAM_ID, streamId)
        Log.d(TAG, "streamId: $streamId")

        mMediaMetadata?.putString(METADATA_KEY_CHANNEL_SLUG, channelSlug)
        Log.d(TAG, "channelSlug: $channelSlug")

        Log.d(TAG, "title: $title")
        mMediaMetadata?.putString(MediaMetadata.KEY_TITLE, title)

        Log.d(TAG, "description: $description")
        mMediaMetadata?.putString(MediaMetadata.KEY_SUBTITLE, description)

        if (thumbnailUrl.isEmpty()) {
            Log.w(TAG, "thumbnailUrl is empty")
        } else {
            val thumbnailUri = Uri.parse(thumbnailUrl)
            val thumbnailImage = WebImage(thumbnailUri)
            mMediaMetadata?.addImage(thumbnailImage)

            Log.w(TAG, "thumbnailUrl: $thumbnailUrl")
        }
    }

    private fun getDisplaySize() {
        val display = currentActivity?.windowManager?.defaultDisplay
        display?.getSize(size)
        Log.d(TAG, "getDisplaySize: ${size.x}x${size.y}")
    }

    private fun prepareButtonInternalPip() {
        expandAnimation = AnimationUtils.loadAnimation(context, R.anim.expand_animation)
        collapseAnimation = AnimationUtils.loadAnimation(context, R.anim.collapse_animation)

        expandButton = ImageView(context)
        shadowView = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.5f
        }
        expandButton?.setImageResource(R.drawable.baseline_zoom_out_map_24)

        closeButton = ImageView(context).apply {
            setImageResource(R.drawable.baseline_close_24)
        }

        playPauseButton = ImageView(context).apply {
            setImageResource(R.drawable.baseline_pause_24)
        }

        val expandButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            topMargin = marginButton
            leftMargin = marginButton
        }
        expandButton?.layoutParams = expandButtonParams

        val closeButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            topMargin = marginButton
            rightMargin = marginButton
        }
        closeButton?.layoutParams = closeButtonParams

        val playPauseButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        playPauseButton?.layoutParams = playPauseButtonParams

        expandButton?.setOnClickListener {
            Log.d(TAG, "expandButton?.setOnClickListener")
            togglePip(false)
            setDisplayPipButton(false)
        }

        closeButton?.setOnClickListener {
            Log.d(TAG, "closeButton?.setOnClickListener")
            setDisplayPipButton(false)
            mPlayerView?.player?.pause()
            mPlayerView?.clipToOutline = false

            val ret: WritableNativeMap = WritableNativeMap()
            sendEvent("closePip", ret)
        }

        playPauseButton?.setOnClickListener {
            val playerState = mPlayerView?.player?.state
            if (playerState == State.PLAYING) {
                playPauseButton?.setImageResource(R.drawable.baseline_play_arrow_24)
                mPlayerView?.player?.pause()
            } else {
                playPauseButton?.setImageResource(R.drawable.baseline_pause_24)
                mPlayerView?.player?.play()
            }
        }

        mPlayerView?.apply {
            addView(shadowView)
            addView(expandButton)
            addView(closeButton)
            addView(playPauseButton)
        }

        setDisplayPipButton(false)
    }

//    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
//    fun setRoundedCorners(view: View, radius: Float) {
//        view.clipToOutline = true
//        view.outlineProvider = object : ViewOutlineProvider() {
//            override fun getOutline(view: View, outline: Outline) {
//                Log.d(TAG, "setRoundedCorners ${view.width} x ${view.height}...")
//                outline.setRoundRect(0, 0, view.width, view.height, radius)
//            }
//        }
//    }

    @SuppressLint("ClickableViewAccessibility")
    @ReactMethod
    public fun setupUI() {
        currentActivity?.runOnUiThread {
            Log.d(TAG, "UI Setup initialised...")
            getDisplaySize()
            setupCastListener()
            setupMediaRouteButton()

            currentActivity?.findViewById<View>(android.R.id.content)?.setBackgroundColor(Color.BLACK)

            mPlayerView = PlayerViewShared.playerView as PlayerView;

            mPlayerView?.parent?.let {
                PlayerViewShared.parentLayout = it as ViewGroup
            }
            mPlayerView?.requestFocus();
            mPlayerView?.setControlsEnabled(false);

//            prepareButtonInternalPip();
            addPipListener();
            addPlayerListener();

            mPlayerView?.setOutlineProvider(object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f)
                }
            })

            gestureDetector =
                GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
//                        toggleFullScreen()
                        return true
                    }
                })

            scaleGestureDetector = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        // Handle scale gestures if needed
                        Log.d(TAG, "XX3...")
                        return true
                    }
                })

//            mPlayerView?.setOnTouchListener { view, event ->
//                var initialX: Int = 0
//                var initialY: Int = 0
//                var initialTouchX: Float = 0f
//                var initialTouchY: Float = 0f
//                var maxMarginX: Int = 0
//                var maxMarginY: Int = 0
//
//                gestureDetector?.onTouchEvent(event)
//                scaleGestureDetector?.onTouchEvent(event)
//                maxMarginX = size.x - (playerViewParams?.width ?: 0)
//                maxMarginY = size.y - (playerViewParams?.height ?: 0)
//                when (event.action) {
//                    MotionEvent.ACTION_DOWN -> {
//                        var initialX = playerViewParams?.leftMargin
//                        var initialY = playerViewParams?.topMargin
//                        var initialTouchX = event.rawX
//                        var initialTouchY = event.rawY
//                        setAutoHideDisplayButton()
//                    }
//
//                    MotionEvent.ACTION_MOVE -> {
//                        val deltaX = (event.rawX - initialTouchX).toInt()
//                        val deltaY = (event.rawY - initialTouchY).toInt()
//                        val newMarginX = (initialX + deltaX).coerceIn(0, maxMarginX)
//                        val newMarginY = (initialY + deltaY).coerceIn(0, maxMarginY)
//                        playerViewParams?.leftMargin = newMarginX
//                        playerViewParams?.topMargin = newMarginY
//                        mPlayerView?.layoutParams = playerViewParams
//                    }
//                }
//                true
//            }

            setupRouteManager()
            setupSessionManager()
        }
    }

    private fun setupSessionManager() {
        val sessionManager = getSessionManager()

        if (sessionManager != null) {
            currentActivity?.runOnUiThread {
                sessionManager.addSessionManagerListener(
                    mSessionManagerListener as SessionManagerListener<CastSession>,
                    CastSession::class.java
                )
            }
        } else {
            Log.e(
                "ReactNativeIVSPlayer",
                "setupSessionManager, sessionManager is null"
            )
        }
    }

    private fun setupMediaRouteButton() {
        currentActivity?.let {
            mediaRouteButton = MediaRouteButton(it).apply {
                visibility = View.GONE
            }

            CastButtonFactory.setUpMediaRouteButton(
                it.applicationContext,
                mediaRouteButton!!
            )

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
            }

            currentActivity?.addContentView(mediaRouteButton, params)

            mediaRouteButton?.setDialogFactory(object : MediaRouteDialogFactory() {
                override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment {
                    return VideoMediaRouteChooserDialogFragment()
                }
            })
        }

    }

    private fun setupRouteManager() {
        mRouteManager = RouteManager(context).apply {
            addRouteChangeListener(object : RouteManager.RouteChangeListener {
                override fun onRouteChanged(routes: List<RouteInfo>) {
                    val routeNames = routes.map { it.name }

                    Log.d(TAG, "addRouteChangeListener number of routes: ${routes.size} routes: $routeNames"
                    )

                    notifyCastStatus()
                }
            })
        }
    }

    @ReactMethod
    fun start(promise: Promise) {
        playVideo()
        promise.resolve(true)
    }

    private fun playVideo() {
        currentActivity?.runOnUiThread {
            playPauseButton?.setImageResource(R.drawable.baseline_pause_24)
        }

        val remoteMediaClient = getRemoteMediaClient()

        if (remoteMediaClient != null) {
            currentActivity?.runOnUiThread {
                remoteMediaClient.play()
            }
            mPlayerView?.player?.pause()
        } else {
            mPlayerView?.player?.play()
        }
    }

    @ReactMethod
    fun pause(promise: Promise) {
        pauseVideo()
        promise.resolve(true)
    }

    private fun pauseVideo() {
        Log.d(TAG, "pauseVideo")
        playPauseButton?.setImageResource(R.drawable.baseline_play_arrow_24)
        val remoteMediaClient = getRemoteMediaClient()

        remoteMediaClient?.let {
            currentActivity?.runOnUiThread {
                it.pause()
            }
        }

        mPlayerView?.player?.pause()
    }

    fun _delete() {
        Log.d(TAG, "_delete")
        currentActivity?.runOnUiThread {
            val mainPiPFrameLayout = currentActivity?.findViewById<FrameLayout>(mainPiPFrameLayoutId)

            mPlayerView?.player?.pause()

            mainPiPFrameLayout?.let {
                currentActivity?.runOnUiThread {
                    it.removeView(mPlayerView)
                }
            }
        }
    }

    @ReactMethod
    fun delete(promise: Promise) {
        Log.d(TAG, "delete")
        _delete()
        promise.resolve(true)
    }

    @ReactMethod
    fun getUrl(promise: Promise) {
        val ret: MutableMap<String, String> = mutableMapOf()

        val contentUrl = mMediaInfo?.contentUrl ?: ""

        ret.put("contentUrl", contentUrl)
        promise.resolve(ret)
    }

    @ReactMethod
    fun getState(promise: Promise) {
        val state = if (getIsCastSessionActive()) {
            getCastPlayerState()
        } else {
            mPlayerView?.player?.state
        }

        Log.d(TAG, "getState, state: $state")
        val ret: MutableMap<String, Any?> = mutableMapOf()
        ret.put("state", state)
        promise.resolve(ret)
    }


    @ReactMethod
    fun setAutoQuality(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val autoQuality = call.getBoolean("autoQuality", false);
        val greeting = "Hello, quality is: $autoQuality!"
        Log.d(TAG, greeting)
        mPlayerView?.player?.setAutoQualityMode(autoQuality)
        promise.resolve(true)
    }

    @ReactMethod
    fun getAutoQuality(promise: Promise) {
        val ret: MutableMap<String, Any?> = mutableMapOf()
        ret.put("autoQuality", mPlayerView?.player?.isAutoQualityMode)
        promise.resolve(ret)
    }

    @ReactMethod
    fun getPip(promise: Promise) {
        val ret: MutableMap<String, Any?> = mutableMapOf()
        ret.put("pip", isAppInPiPMode())
        promise.resolve(ret)
    }


    private fun setDisplayPipButton(displayPipButton: Boolean) {
        Log.d(TAG, "setDisplayPipButton displayPipButton: $displayPipButton, currentStateDisplayButton: $currentStateDisplayButton")
        if (currentStateDisplayButton == displayPipButton) return

        if (displayPipButton) {
            fadeAnimation(shadowView, View.VISIBLE)
            fadeAnimation(expandButton, View.VISIBLE)
            fadeAnimation(closeButton, View.VISIBLE)
            fadeAnimation(playPauseButton, View.VISIBLE)
            currentStateDisplayButton = true
        } else {
            fadeAnimation(shadowView, View.GONE)
            fadeAnimation(expandButton, View.GONE)
            fadeAnimation(closeButton, View.GONE)
            fadeAnimation(playPauseButton, View.GONE)
            currentStateDisplayButton = false
        }
    }

    private fun fadeAnimation(view: View?, visibility: Int) {
        if (view == null) return;
        val animation = if (visibility == View.VISIBLE) {
            AlphaAnimation(0f, 1f)
        } else {
            AlphaAnimation(1f, 0f)
        }

        animation.duration = 500
        view.visibility = visibility
        view.startAnimation(animation)
    }

    private fun animateResize(
        startWidth: Int, startHeight: Int,
        endWidth: Int, endHeight: Int,
        startX: Int, startY: Int, endX: Int, endY: Int
    ) {
        val widthAnimator = ValueAnimator.ofFloat(startWidth.toFloat(), endWidth.toFloat())
        widthAnimator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            mPlayerView?.layoutParams?.width = animatedValue.toInt()
            val maxMarginX = size.x - animatedValue.toInt()
            val newMarginX = maxOf(0, minOf(mPlayerView?.left ?: 0, maxMarginX))
            val layoutParams = mPlayerView?.layoutParams as FrameLayout.LayoutParams
            layoutParams.leftMargin = newMarginX
            mPlayerView?.layoutParams = layoutParams
            mPlayerView?.requestLayout()
        }

        val heightAnimator = ValueAnimator.ofFloat(startHeight.toFloat(), endHeight.toFloat())
        heightAnimator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            mPlayerView?.layoutParams?.height = animatedValue.toInt()
            val maxMarginY = size.y - animatedValue.toInt()
            val newMarginY = maxOf(0, minOf(mPlayerView?.top ?: 0, maxMarginY))
            val layoutParams = mPlayerView?.layoutParams as FrameLayout.LayoutParams
            layoutParams.topMargin = newMarginY
            mPlayerView?.layoutParams = layoutParams
            mPlayerView?.requestLayout()
        }

        val animatorSet = AnimatorSet()
        animatorSet.duration = 300
        animatorSet.playTogether(widthAnimator, heightAnimator)
        animatorSet.start()
    }

    @ReactMethod
    fun setPip(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val pip = call.getBoolean("pip", false)
        Log.d(TAG, "setPip pip: $pip")

        if (pip && !getIsCastSessionActive()) {
            enterPipMode();
        }

        promise.resolve(true)
    }

    fun convertDpToPixel(dp: Float): Float {
        return dp * (getContext().resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    fun convertPixelsToDp(px: Float): Float {
        return px / (getContext().resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    @ReactMethod
    fun setMute(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val muted = call.getBoolean("muted", false)
        mPlayerView?.player?.isMuted = muted

        val remoteMediaClient = getRemoteMediaClient()

        remoteMediaClient?.let {
            currentActivity?.runOnUiThread {
                it.setStreamMute(muted)
            }
        }

        promise.resolve(true)
    }

    @ReactMethod
    fun getMute(promise: Promise) {
        val ret = WritableNativeMap().apply {
            putBoolean("muted", getMuted())
        }
        promise.resolve(ret)
    }

    private fun getMuted(): Boolean {
        return if (getIsCastSessionActive()) {
            try {
                getResultFromUiThread {
                    var muted = false

                    val mediaStatus = getMediaStatus()

                    if (mediaStatus != null) {
                        muted = mediaStatus.isMute
                    } else {
                        Log.e(TAG, "getMuted, mediaStatus is null")
                    }

                    muted
                }
            } catch (e: ExecutionException) {
                Log.e(TAG, "getMuted exception: ${e.message}")
                false
            } catch (e: InterruptedException) {
                Log.e(TAG, "getMuted exception: ${e.message}")
                false
            }
        } else {
            mPlayerView?.player?.isMuted ?: false
        }
    }


    private fun getMediaStatus(): MediaStatus? {
        return try {
            getResultFromUiThread {
                val remoteMediaClient = getRemoteMediaClient()
                remoteMediaClient?.mediaStatus
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "getMediaStatus exception: ${e.message}")
            null
        } catch (e: InterruptedException) {
            Log.e(TAG, "getMediaStatus exception: ${e.message}")
            null
        }
    }

    @ReactMethod
    fun setQuality(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val qualityName = call.getString("quality")
        // loop through qualities and find the one with the name

        val player = mPlayerView?.player

        if (player != null) {
            var quality: Quality? = null
            for (q in player.qualities) {
                if (q.name == qualityName) {
                    quality = q
                    break
                }
            }

            player.setQuality(quality as Quality)
        }

        promise.resolve(true)
    }

    @ReactMethod
    fun getQuality(promise: Promise) {
        val ret = WritableNativeMap().apply {
            putString("quality", mPlayerView?.player?.quality?.name)
        }
        promise.resolve(ret)
    }

    @ReactMethod
    fun getQualities(promise: Promise) {
        val ret = WritableNativeMap()
        val qualities = mPlayerView?.player?.qualities
        val qualitiesArray = qualities?.map { it.toString() }
        val writableArray: WritableArray = Arguments.createArray()
        qualitiesArray?.forEach { x ->
            writableArray.pushString(x)
        }

        ret.putArray("qualities", writableArray)
        promise.resolve(ret)
    }

    @ReactMethod
    fun getSeekPosition(promise: Promise) {
        val ret = WritableNativeMap()

        if (getIsCastSessionActive()) {
            val mediaStatus = getMediaStatus()

            if (mediaStatus != null) {
                val pos =
                    (mediaStatus.streamPosition / 1000).toDouble() // position is in `ms`, we need `seconds`
                ret.putDouble("position", pos)
            }
        } else {
            val pos = mPlayerView?.player?.position?.div(1000)?.toDouble()
                ?: 0.0 // position is in `ms`, we need `seconds`
            ret.putDouble("position", pos)
        }

        promise.resolve(ret)
    }

    @ReactMethod
    fun seekTo(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val position = call.getFloat("position")

        if (position != null && position >= 0) {
            val longPos = (position * 1000).toLong() // position is in `seconds`, we need `ms`

            if (getIsCastSessionActive()) {
                val remoteMediaClient = getCastSession()?.remoteMediaClient

                remoteMediaClient?.seek(longPos)
            } else {
                mPlayerView?.player?.seekTo(longPos)
                promise.resolve(true)
            }
        } else {
            promise.reject(
                "Error", "Invalid seek position"
            )
        }
    }

    @ReactMethod
    fun setPlaybackRate(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val rate = call.getFloat("playbackRate")

        if (rate != null && rate <= 2 && rate >= 0.5) {
            if (getIsCastSessionActive()) {
                promise.reject(
                    "Error", "Playback rate can not be adjusted while casting!"
                )
            } else {
                mPlayerView?.player?.setPlaybackRate(rate)
                promise.resolve(true)
            }
        } else {
            promise.reject(
                "Error",
                "Playback rate should be a number between 0.5 and 2.0 (both inclusive), where 1.0 is the default rate."
            )
        }
    }

    @ReactMethod
    fun getPlaybackRate(promise: Promise) {
        if (getIsCastSessionActive()) {
            promise.reject(
                "Error", "Playback rate can not be queried nor adjusted while casting!"
            )
        } else {
            val rate = mPlayerView?.player?.playbackRate

            val ret = WritableNativeMap()
            ret.putDouble("playbackRate", rate?.toDouble() ?: 0.0)
            promise.resolve(ret)
        }
    }

    fun applyLastSeekPosition() {
        lastSeekPosBeforeSrcChange?.let {
            if (getIsCastSessionActive()) {
                val remoteMediaClient = getRemoteMediaClient()

                remoteMediaClient?.seek(it)
            } else {
                mPlayerView?.player?.seekTo(it)
            }

            lastSeekPosBeforeSrcChange = null
        }
    }

    @ReactMethod
    fun updatePlayerSrcUrl(options: ReadableMap, promise: Promise) {
        val call = DefaultReadableMap(options)
        val srcUrl = call.getString("url")

        if (srcUrl == null) {
            promise.reject("Error", "Url is required")
            return
        }

        if (getIsCastSessionActive()) {
            val mediaStatus = getMediaStatus()

            if (mediaStatus != null) {
                lastSeekPosBeforeSrcChange = mediaStatus.streamPosition
            }
        } else {
            lastSeekPosBeforeSrcChange = mPlayerView?.player?.position
        }

        loadUrl(srcUrl)

        promise.resolve(true)
    }


}
