package ac.mdiq.podcini.menuhandler

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.util.Log
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.dialog.ConfirmationDialog
import ac.mdiq.podcini.core.storage.DBWriter
import ac.mdiq.podcini.dialog.RemoveFeedDialog
import ac.mdiq.podcini.dialog.RenameItemDialog
import ac.mdiq.podcini.dialog.TagSettingsDialog
import ac.mdiq.podcini.model.feed.Feed
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Callable
import java.util.concurrent.Future

/**
 * Handles interactions with the FeedItemMenu.
 */
object FeedMenuHandler {
    private const val TAG = "FeedMenuHandler"

    @JvmStatic
    fun onMenuItemClicked(fragment: Fragment, menuItemId: Int,
                          selectedFeed: Feed, callback: Runnable
    ): Boolean {
        val context = fragment.requireContext()
        if (menuItemId == R.id.rename_folder_item) {
            RenameItemDialog(fragment.activity as Activity, selectedFeed).show()
        } else if (menuItemId == R.id.remove_all_inbox_item) {
            val dialog: ConfirmationDialog = object : ConfirmationDialog(fragment.activity as Activity,
                R.string.remove_all_inbox_label, R.string.remove_all_inbox_confirmation_msg) {
                @SuppressLint("CheckResult")
                override fun onConfirmButtonPressed(clickedDialog: DialogInterface) {
                    clickedDialog.dismiss()
                    Observable.fromCallable(Callable { DBWriter.removeFeedNewFlag(selectedFeed.id) } as Callable<Future<*>>)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ result: Future<*>? -> callback.run() },
                            { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
                }
            }
            dialog.createNewDialog().show()
        } else if (menuItemId == R.id.edit_tags) {
            if (selectedFeed.preferences != null) TagSettingsDialog.newInstance(listOf(selectedFeed.preferences!!))
                .show(fragment.childFragmentManager, TagSettingsDialog.TAG)
        } else if (menuItemId == R.id.rename_item) {
            RenameItemDialog(fragment.activity as Activity, selectedFeed).show()
        } else if (menuItemId == R.id.remove_feed) {
            RemoveFeedDialog.show(context, selectedFeed, null)
        } else {
            return false
        }
        return true
    }
}
