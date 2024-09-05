package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.ui.widget.WidgetUpdaterWorker
import ac.mdiq.podcini.util.Logd
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PlayerWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        getSharedPrefs(context)
        Logd(TAG, "Widget enabled")
        setEnabled(true)
        WidgetUpdaterWorker.enqueueWork(context)
        scheduleWorkaround(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Logd(TAG, "onUpdate() called with: context = [$context], appWidgetManager = [$appWidgetManager], appWidgetIds = [${appWidgetIds.contentToString()}]")
        getSharedPrefs(context)
        WidgetUpdaterWorker.enqueueWork(context)
        if (!prefs!!.getBoolean(Prefs.WorkaroundEnabled.name, false)) {
            scheduleWorkaround(context)
            prefs!!.edit().putBoolean(Prefs.WorkaroundEnabled.name, true).apply()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Logd(TAG, "Widget disabled")
        setEnabled(false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Logd(TAG, "OnDeleted")
        for (appWidgetId in appWidgetIds) {
            prefs!!.edit().remove(Prefs.widget_color.name + appWidgetId).apply()
            prefs!!.edit().remove(Prefs.widget_playback_speed.name + appWidgetId).apply()
            prefs!!.edit().remove(Prefs.widget_rewind.name + appWidgetId).apply()
            prefs!!.edit().remove(Prefs.widget_fast_forward.name + appWidgetId).apply()
            prefs!!.edit().remove(Prefs.widget_skip.name + appWidgetId).apply()
        }
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (widgetIds.isEmpty()) {
            prefs!!.edit().putBoolean(Prefs.WorkaroundEnabled.name, false).apply()
            WorkManager.getInstance(context).cancelUniqueWork(Prefs.WidgetUpdaterWorkaround.name)
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun setEnabled(enabled: Boolean) {
        prefs!!.edit().putBoolean(Prefs.WidgetEnabled.name, enabled).apply()
    }

    enum class Prefs {
        widget_color,
        widget_playback_speed,
        widget_skip,
        widget_fast_forward,
        widget_rewind,
        WidgetUpdaterWorkaround,
        WorkaroundEnabled,
        WidgetEnabled
    }

    companion object {
        private val TAG: String = PlayerWidget::class.simpleName ?: "Anonymous"
        private const val PREFS_NAME: String = "PlayerWidgetPrefs"
        const val DEFAULT_COLOR: Int = -0xd9d3cf
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
            WorkManager.getInstance(context).enqueueUniqueWork(Prefs.WidgetUpdaterWorkaround.name, ExistingWorkPolicy.REPLACE, workRequest)
        }

        @JvmStatic
        fun isEnabled(): Boolean {
            return prefs!!.getBoolean(Prefs.WidgetEnabled.name, false)
        }
    }
}
