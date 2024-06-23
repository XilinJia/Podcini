package ac.mdiq.podcini.ui.widget

import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.ui.widget.WidgetUpdater.WidgetState
import ac.mdiq.podcini.util.Logd
import android.content.Context
import androidx.work.*

class WidgetUpdaterWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        try {
            updateWidget()
        } catch (e: Exception) {
            Logd(TAG, "Failed to update Podcini widget: $e")
            return Result.failure()
        }
        return Result.success()
    }

    /**
     * Loads the current media from the database and updates the widget in a background job.
     */
    private fun updateWidget() {
        val media = curMedia
        if (media != null) WidgetUpdater.updateWidget(applicationContext, WidgetState(media, PlayerStatus.STOPPED, media.getPosition(), media.getDuration(), getCurrentPlaybackSpeed(media)))
        else WidgetUpdater.updateWidget(applicationContext, WidgetState(PlayerStatus.STOPPED))
    }

    companion object {
        private val TAG: String = WidgetUpdaterWorker::class.simpleName ?: "Anonymous"

        fun enqueueWork(context: Context) {
            val workRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(WidgetUpdaterWorker::class.java).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
