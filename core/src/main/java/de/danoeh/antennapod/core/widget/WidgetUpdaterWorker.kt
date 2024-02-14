package de.danoeh.antennapod.core.widget

import android.content.Context
import android.util.Log
import androidx.work.*
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils.getCurrentPlaybackSpeed
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.createInstanceFromPreferences
import de.danoeh.antennapod.core.widget.WidgetUpdater.WidgetState
import de.danoeh.antennapod.playback.base.PlayerStatus

class WidgetUpdaterWorker(context: Context,
                          workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        try {
            updateWidget()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to update AntennaPod widget: ", e)
            return Result.failure()
        }
        return Result.success()
    }

    /**
     * Loads the current media from the database and updates the widget in a background job.
     */
    private fun updateWidget() {
        val media = createInstanceFromPreferences(applicationContext)
        if (media != null) {
            WidgetUpdater.updateWidget(applicationContext,
                WidgetState(media, PlayerStatus.STOPPED,
                    media.getPosition(), media.getDuration(),
                    getCurrentPlaybackSpeed(media)))
        } else {
            WidgetUpdater.updateWidget(applicationContext,
                WidgetState(PlayerStatus.STOPPED))
        }
    }

    companion object {
        private const val TAG = "WidgetUpdaterWorker"

        fun enqueueWork(context: Context?) {
            val workRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(WidgetUpdaterWorker::class.java).build()
            WorkManager.getInstance(context!!).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
