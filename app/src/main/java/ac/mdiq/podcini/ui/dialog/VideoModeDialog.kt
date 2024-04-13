package ac.mdiq.podcini.ui.dialog

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.event.UnreadItemsUpdateEvent
import ac.mdiq.podcini.preferences.UserPreferences.feedOrder
import ac.mdiq.podcini.preferences.UserPreferences.setFeedOrder
import ac.mdiq.podcini.preferences.UserPreferences.setVideoMode
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import org.greenrobot.eventbus.EventBus

object VideoModeDialog {
    fun showDialog(context: Context) {
        val dialog = MaterialAlertDialogBuilder(context)
        dialog.setTitle(context.getString(R.string.pref_playback_video_mode))
        dialog.setNegativeButton(android.R.string.cancel) { d: DialogInterface, _: Int -> d.dismiss() }

        val selected = videoPlayMode
        val entryValues = listOf(*context.resources.getStringArray(R.array.video_mode_options_values))
        val selectedIndex =  entryValues.indexOf("" + selected)

        val items = context.resources.getStringArray(R.array.video_mode_options)
        dialog.setSingleChoiceItems(items, selectedIndex) { d: DialogInterface, which: Int ->
            if (selectedIndex != which) {
                setVideoMode(entryValues[which].toInt())
            }
            d.dismiss()
        }
        dialog.show()
    }
}
