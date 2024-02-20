package ac.mdiq.podcini.core.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs

/**
 * Collects statistics about the app usage. The statistics are used to allow on-demand configuration:
 * "Looks like you stream a lot. Do you want to toggle the 'Prefer streaming' setting?".
 * The data is only stored locally on the device. It is NOT used for analytics/tracking.
 * A private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
object UsageStatistics {
    private const val PREF_DB_NAME = "UsageStatistics"
    private const val MOVING_AVERAGE_WEIGHT = 0.8f
    private const val MOVING_AVERAGE_BIAS_THRESHOLD = 0.1f
    private const val SUFFIX_HIDDEN = "_hidden"
    private var prefs: SharedPreferences? = null

    @JvmField
    val ACTION_STREAM: StatsAction = StatsAction("downloadVsStream", 0)
    @JvmField
    val ACTION_DOWNLOAD: StatsAction = StatsAction("downloadVsStream", 1)

    /**
     * Sets up the UsageStatistics class.
     *
     * @throws IllegalArgumentException if context is null
     */
    @JvmStatic
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_DB_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun logAction(action: StatsAction) {
        val numExecutions = prefs!!.getInt(action.type + action.value, 0)
        val movingAverage = prefs!!.getFloat(action.type, 0.5f)
        prefs!!.edit()
            .putInt(action.type + action.value, numExecutions + 1)
            .putFloat(action.type, MOVING_AVERAGE_WEIGHT * movingAverage
                    + (1 - MOVING_AVERAGE_WEIGHT) * action.value)
            .apply()
    }

    @JvmStatic
    fun hasSignificantBiasTo(action: StatsAction): Boolean {
        if (prefs!!.getBoolean(action.type + SUFFIX_HIDDEN, false)) {
            return false
        } else {
            val movingAverage = prefs!!.getFloat(action.type, 0.5f)
            return abs((action.value - movingAverage).toDouble()) < MOVING_AVERAGE_BIAS_THRESHOLD
        }
    }

    @JvmStatic
    fun doNotAskAgain(action: StatsAction) {
        prefs!!.edit().putBoolean(action.type + SUFFIX_HIDDEN, true).apply()
    }

    class StatsAction(val type: String, val value: Int)
}
