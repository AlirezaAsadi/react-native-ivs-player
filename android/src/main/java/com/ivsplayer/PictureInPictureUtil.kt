package com.ivsplayer
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.app.AppOpsManagerCompat
import com.facebook.react.bridge.ReactApplicationContext

object PictureInPictureUtil {
    private const val FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x400000
    private const val TAG = "ReactNativeIVSPlayer"

    public fun isSupportPictureInPicture(context: ReactApplicationContext): Boolean =
        checkIsApiSupport() && checkIsSystemSupportPIP(context) && checkIsUserAllowPIP(context)

    private fun isSupportPictureInPictureAction(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    private fun checkIsApiSupport(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkIsSystemSupportPIP(context: ReactApplicationContext): Boolean {
        val activity = context.currentActivity ?: return false

        val activityInfo = activity.packageManager.getActivityInfo(activity.componentName, PackageManager.GET_META_DATA)
        // detect current activity's android:supportsPictureInPicture value defined within AndroidManifest.xml
        // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/content/pm/ActivityInfo.java;l=1090-1093;drc=7651f0a4c059a98f32b0ba30cd64500bf135385f
        val isActivitySupportPip = activityInfo.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE != 0

        // PIP might be disabled on devices that have low RAM.
        val isPipAvailable = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        Log.i("ReactNativeIVSPlayer", "checkIsSystemSupportPIP -> isPipAvailable: $isPipAvailable, isActivitySupportPip: $isActivitySupportPip")

        return isActivitySupportPip && isPipAvailable
    }

    private fun checkIsUserAllowPIP(context: ReactApplicationContext): Boolean {
        val activity = context.currentActivity ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressLint("InlinedApi")
            val result = AppOpsManagerCompat.noteOpNoThrow(
                activity,
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(),
                activity.packageName
            )
            AppOpsManager.MODE_ALLOWED == result
        } else {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        }
    }
}
