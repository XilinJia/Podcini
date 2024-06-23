package ac.mdiq.podcini.ui.utils

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

object PictureInPictureUtil {
    fun supportsPictureInPicture(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val packageManager = activity.packageManager
            return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else return false
    }

    fun isInPictureInPictureMode(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && supportsPictureInPicture(activity)) activity.isInPictureInPictureMode
        else false
    }
}
