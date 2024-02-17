package ac.mdiq.podvinci.fragment.actions

import ac.mdiq.podvinci.activity.MainActivity
import android.content.DialogInterface
import android.util.Log
import android.widget.CompoundButton
import androidx.annotation.PluralsRes
import androidx.core.util.Consumer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.storage.DBWriter
import ac.mdiq.podvinci.databinding.PlaybackSpeedFeedSettingDialogBinding
import ac.mdiq.podvinci.dialog.RemoveFeedDialog
import ac.mdiq.podvinci.dialog.TagSettingsDialog
import ac.mdiq.podvinci.fragment.preferences.dialog.PreferenceListDialog
import ac.mdiq.podvinci.fragment.preferences.dialog.PreferenceSwitchDialog
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedPreferences
import androidx.media3.common.util.UnstableApi
import java.util.*

@UnstableApi
class FeedMultiSelectActionHandler(private val activity: MainActivity, private val selectedItems: List<Feed>) {

    fun handleAction(id: Int) {
        when (id) {
            R.id.remove_feed -> {
                RemoveFeedDialog.show(activity, selectedItems)
            }
            R.id.notify_new_episodes -> {
                notifyNewEpisodesPrefHandler()
            }
            R.id.keep_updated -> {
                keepUpdatedPrefHandler()
            }
            R.id.autodownload -> {
                autoDownloadPrefHandler()
            }
            R.id.autoDeleteDownload -> {
                autoDeleteEpisodesPrefHandler()
            }
            R.id.playback_speed -> {
                playbackSpeedPrefHandler()
            }
            R.id.edit_tags -> {
                editFeedPrefTags()
            }
            else -> {
                Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=$id")
            }
        }
    }

    private fun notifyNewEpisodesPrefHandler() {
        val preferenceSwitchDialog = PreferenceSwitchDialog(activity,
            activity.getString(R.string.episode_notification),
            activity.getString(R.string.episode_notification_summary))

        preferenceSwitchDialog.setOnPreferenceChangedListener(object: PreferenceSwitchDialog.OnPreferenceChangedListener {
            @UnstableApi override fun preferenceChanged(enabled: Boolean) {
                saveFeedPreferences { feedPreferences: FeedPreferences ->
                    feedPreferences.showEpisodeNotification = enabled
                }
            }
        })
        preferenceSwitchDialog.openDialog()
    }

    private fun autoDownloadPrefHandler() {
        val preferenceSwitchDialog = PreferenceSwitchDialog(activity,
            activity.getString(R.string.auto_download_settings_label),
            activity.getString(R.string.auto_download_label))
        preferenceSwitchDialog.setOnPreferenceChangedListener(@UnstableApi object: PreferenceSwitchDialog.OnPreferenceChangedListener {
            override fun preferenceChanged(enabled: Boolean) {
                saveFeedPreferences { feedPreferences: FeedPreferences -> feedPreferences.autoDownload = enabled }
            }
        })
        preferenceSwitchDialog.openDialog()
    }

    @UnstableApi private fun playbackSpeedPrefHandler() {
        val viewBinding =
            PlaybackSpeedFeedSettingDialogBinding.inflate(activity.layoutInflater)
        viewBinding.seekBar.setProgressChangedListener { speed: Float? ->
            viewBinding.currentSpeedLabel.text = String.format(
                Locale.getDefault(), "%.2fx", speed)
        }
        viewBinding.useGlobalCheckbox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            viewBinding.seekBar.isEnabled = !isChecked
            viewBinding.seekBar.alpha = if (isChecked) 0.4f else 1f
            viewBinding.currentSpeedLabel.alpha = if (isChecked) 0.4f else 1f
        }
        viewBinding.seekBar.updateSpeed(1.0f)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.playback_speed)
            .setView(viewBinding.root)
            .setPositiveButton("OK") { dialog: DialogInterface?, which: Int ->
                val newSpeed = if (viewBinding.useGlobalCheckbox.isChecked
                ) FeedPreferences.SPEED_USE_GLOBAL else viewBinding.seekBar.currentSpeed
                saveFeedPreferences { feedPreferences: FeedPreferences ->
                    feedPreferences.feedPlaybackSpeed = newSpeed
                }
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun autoDeleteEpisodesPrefHandler() {
        val preferenceListDialog = PreferenceListDialog(activity,
            activity.getString(R.string.auto_delete_label))
        val items: Array<String> = activity.resources.getStringArray(R.array.spnAutoDeleteItems)
        preferenceListDialog.openDialog(items)
        preferenceListDialog.setOnPreferenceChangedListener(object: PreferenceListDialog.OnPreferenceChangedListener {
            @UnstableApi override fun preferenceChanged(which: Int) {
                val autoDeleteAction: FeedPreferences.AutoDeleteAction = FeedPreferences.AutoDeleteAction.fromCode(which)
                saveFeedPreferences { feedPreferences: FeedPreferences ->
                    feedPreferences.currentAutoDelete = autoDeleteAction
                }
            }
        })
    }

    private fun keepUpdatedPrefHandler() {
        val preferenceSwitchDialog = PreferenceSwitchDialog(activity,
            activity.getString(R.string.kept_updated),
            activity.getString(R.string.keep_updated_summary))
        preferenceSwitchDialog.setOnPreferenceChangedListener(object: PreferenceSwitchDialog.OnPreferenceChangedListener {
            @UnstableApi override fun preferenceChanged(keepUpdated: Boolean) {
                saveFeedPreferences { feedPreferences: FeedPreferences ->
                    feedPreferences.keepUpdated = keepUpdated
                }
            }
        })
        preferenceSwitchDialog.openDialog()
    }

    @UnstableApi private fun showMessage(@PluralsRes msgId: Int, numItems: Int) {
        activity.showSnackbarAbovePlayer(activity.resources
            .getQuantityString(msgId, numItems, numItems), Snackbar.LENGTH_LONG)
    }

    @UnstableApi private fun saveFeedPreferences(preferencesConsumer: Consumer<FeedPreferences>) {
        for (feed in selectedItems) {
            if (feed.preferences == null) continue
            preferencesConsumer.accept(feed.preferences)
            DBWriter.setFeedPreferences(feed.preferences!!)
        }
        showMessage(R.plurals.updated_feeds_batch_label, selectedItems.size)
    }

    private fun editFeedPrefTags() {
        val preferencesList: ArrayList<FeedPreferences> = ArrayList<FeedPreferences>()
        for (feed in selectedItems) {
            if (feed.preferences == null) continue
            preferencesList.add(feed.preferences!!)
        }
        TagSettingsDialog.newInstance(preferencesList).show(activity.supportFragmentManager,
            TagSettingsDialog.TAG)
    }

    companion object {
        private const val TAG = "FeedSelectHandler"
    }
}
