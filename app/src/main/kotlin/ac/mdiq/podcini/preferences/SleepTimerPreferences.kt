package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

object SleepTimerPreferences {
    private val TAG: String = SleepTimerPreferences::class.simpleName ?: "Anonymous"

    private enum class Prefs {
        SleepTimerDialog,
        LastValue,
        Vibrate,
        ShakeToReset,
        AutoEnable,
        AutoEnableFrom,
        AutoEnableTo
    }

    private const val DEFAULT_LAST_TIMER = "15"
    private const val DEFAULT_AUTO_ENABLE_FROM = 22
    private const val DEFAULT_AUTO_ENABLE_TO = 6

    private var prefs: SharedPreferences? = null

    /**
     * Sets up the UserPreferences class.
     *
     * @throws IllegalArgumentException if context is null
     */
    @JvmStatic
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of SleepTimerPreferences")
        prefs = context.getSharedPreferences(Prefs.SleepTimerDialog.name, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun setLastTimer(value: String?) {
        prefs!!.edit().putString(Prefs.LastValue.name, value).apply()
    }

    @JvmStatic
    fun lastTimerValue(): String? {
        return prefs!!.getString(Prefs.LastValue.name, DEFAULT_LAST_TIMER)
    }

    @JvmStatic
    fun timerMillis(): Long {
        val value = lastTimerValue()!!.toLong()
        return TimeUnit.MINUTES.toMillis(value)
    }

    @JvmStatic
    fun setVibrate(vibrate: Boolean) {
        prefs!!.edit().putBoolean(Prefs.Vibrate.name, vibrate).apply()
    }

    @JvmStatic
    fun vibrate(): Boolean {
        return prefs!!.getBoolean(Prefs.Vibrate.name, false)
    }

    @JvmStatic
    fun setShakeToReset(shakeToReset: Boolean) {
        prefs!!.edit().putBoolean(Prefs.ShakeToReset.name, shakeToReset).apply()
    }

    @JvmStatic
    fun shakeToReset(): Boolean {
        return prefs!!.getBoolean(Prefs.ShakeToReset.name, true)
    }

    @JvmStatic
    fun setAutoEnable(autoEnable: Boolean) {
        prefs!!.edit().putBoolean(Prefs.AutoEnable.name, autoEnable).apply()
    }

    @JvmStatic
    fun autoEnable(): Boolean {
        return prefs!!.getBoolean(Prefs.AutoEnable.name, false)
    }

    @JvmStatic
    fun setAutoEnableFrom(hourOfDay: Int) {
        prefs!!.edit().putInt(Prefs.AutoEnableFrom.name, hourOfDay).apply()
    }

    @JvmStatic
    fun autoEnableFrom(): Int {
        return prefs!!.getInt(Prefs.AutoEnableFrom.name, DEFAULT_AUTO_ENABLE_FROM)
    }

    @JvmStatic
    fun setAutoEnableTo(hourOfDay: Int) {
        prefs!!.edit().putInt(Prefs.AutoEnableTo.name, hourOfDay).apply()
    }

    @JvmStatic
    fun autoEnableTo(): Int {
        return prefs!!.getInt(Prefs.AutoEnableTo.name, DEFAULT_AUTO_ENABLE_TO)
    }

    @JvmStatic
    fun isInTimeRange(from: Int, to: Int, current: Int): Boolean {
        // Range covers one day
        if (from < to) return current in from..<to

        // Range covers two days
        if (from <= current) return true

        return current < to
    }
}
