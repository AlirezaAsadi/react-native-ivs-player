package com.ivsplayer.mediaroute
import android.content.Context
import android.os.Bundle
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteChooserDialogFragment

class VideoMediaRouteChooserDialogFragment : MediaRouteChooserDialogFragment() {

    override fun onCreateChooserDialog(context: Context, savedInstanceState: Bundle?): MediaRouteChooserDialog {
        return VideoMediaRouteChooserDialog(context, theme)
    }
}
