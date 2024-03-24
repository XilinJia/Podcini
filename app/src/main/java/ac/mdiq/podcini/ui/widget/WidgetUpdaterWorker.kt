package ac.mdiq.podcini.ui.widget

import android.content.Context
import android.util.Log
import androidx.work.*
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils.getCurrentPlaybackSpeed
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.createInstanceFromPreferences
import ac.mdiq.podcini.ui.widget.WidgetUpdater.WidgetState
import ac.mdiq.podcini.playback.base.PlayerStatus

class WidgetUpdaterWorker(context: Context,
                          workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        try {
            updateWidget()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to update Podcini widget: ", e)
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

        fun enqueueWork(context: Context) {
            val workRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(WidgetUpdaterWorker::class.java).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
