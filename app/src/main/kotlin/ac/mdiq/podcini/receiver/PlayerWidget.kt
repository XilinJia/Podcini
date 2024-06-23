package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions.Companion.SWIPE_ACTIONS_PREF_NAME
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import ac.mdiq.podcini.ui.widget.WidgetUpdaterWorker
import ac.mdiq.podcini.util.Logd
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

class PlayerWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Logd(TAG, "Widget enabled")
        setEnabled(context, true)
        WidgetUpdaterWorker.enqueueWork(context)
        scheduleWorkaround(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Logd(TAG, "onUpdate() called with: context = [$context], appWidgetManager = [$appWidgetManager], appWidgetIds = [${appWidgetIds.contentToString()}]")
        WidgetUpdaterWorker.enqueueWork(context)

        if (!prefs!!.getBoolean(KEY_WORKAROUND_ENABLED, false)) {
            scheduleWorkaround(context)
            prefs!!.edit().putBoolean(KEY_WORKAROUND_ENABLED, true).apply()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Logd(TAG, "Widget disabled")
        setEnabled(context, false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Logd(TAG, "OnDeleted")
        for (appWidgetId in appWidgetIds) {
            prefs!!.edit().remove(KEY_WIDGET_COLOR + appWidgetId).apply()
            prefs!!.edit().remove(KEY_WIDGET_PLAYBACK_SPEED + appWidgetId).apply()
            prefs!!.edit().remove(KEY_WIDGET_REWIND + appWidgetId).apply()
            prefs!!.edit().remove(KEY_WIDGET_FAST_FORWARD + appWidgetId).apply()
            prefs!!.edit().remove(KEY_WIDGET_SKIP + appWidgetId).apply()
        }
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (widgetIds.isEmpty()) {
            prefs!!.edit().putBoolean(KEY_WORKAROUND_ENABLED, false).apply()
            WorkManager.getInstance(context).cancelUniqueWork(WORKAROUND_WORK_NAME)
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun setEnabled(context: Context, enabled: Boolean) {
        prefs!!.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private val TAG: String = PlayerWidget::class.simpleName ?: "Anonymous"
        const val PREFS_NAME: String = "PlayerWidgetPrefs"
        private const val KEY_WORKAROUND_ENABLED = "WorkaroundEnabled"
        private const val KEY_ENABLED = "WidgetEnabled"
        const val KEY_WIDGET_COLOR: String = "widget_color"
        const val KEY_WIDGET_PLAYBACK_SPEED: String = "widget_playback_speed"
        const val KEY_WIDGET_SKIP: String = "widget_skip"
        const val KEY_WIDGET_FAST_FORWARD: String = "widget_fast_forward"
        const val KEY_WIDGET_REWIND: String = "widget_rewind"
        const val DEFAULT_COLOR: Int = -0xd9d3cf
        private const val WORKAROUND_WORK_NAME = "WidgetUpdaterWorkaround"

        var prefs: SharedPreferences? = null

        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private fun scheduleWorkaround(context: Context) {
            // Enqueueing work enables a BOOT_COMPLETED receiver, which in turn makes Android refresh widgets.
            // This creates an endless loop with a flickering widget.
            // Workaround: When there is a widget, schedule a dummy task in the far future, so that the receiver stays.
            val workRequest: OneTimeWorkRequest = OneTimeWorkRequest.Builder(WidgetUpdaterWorker::class.java)
                .setInitialDelay((100 * 356).toLong(), TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORKAROUND_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }

        @JvmStatic
        fun isEnabled(context: Context): Boolean {
//            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs!!.getBoolean(KEY_ENABLED, false)
        }
    }
}
