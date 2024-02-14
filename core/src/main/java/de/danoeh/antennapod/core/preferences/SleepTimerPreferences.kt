package de.danoeh.antennapod.core.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit

object SleepTimerPreferences {
    private const val TAG = "SleepTimerPreferences"

    const val PREF_NAME: String = "SleepTimerDialog"
    private const val PREF_VALUE = "LastValue"

    private const val PREF_VIBRATE = "Vibrate"
    private const val PREF_SHAKE_TO_RESET = "ShakeToReset"
    private const val PREF_AUTO_ENABLE = "AutoEnable"
    private const val PREF_AUTO_ENABLE_FROM = "AutoEnableFrom"
    private const val PREF_AUTO_ENABLE_TO = "AutoEnableTo"

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
        Log.d(TAG, "Creating new instance of SleepTimerPreferences")
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun setLastTimer(value: String?) {
        prefs!!.edit().putString(PREF_VALUE, value).apply()
    }

    @JvmStatic
    fun lastTimerValue(): String? {
        return prefs!!.getString(PREF_VALUE, DEFAULT_LAST_TIMER)
    }

    @JvmStatic
    fun timerMillis(): Long {
        val value = lastTimerValue()!!.toLong()
        return TimeUnit.MINUTES.toMillis(value)
    }

    @JvmStatic
    fun setVibrate(vibrate: Boolean) {
        prefs!!.edit().putBoolean(PREF_VIBRATE, vibrate).apply()
    }

    @JvmStatic
    fun vibrate(): Boolean {
        return prefs!!.getBoolean(PREF_VIBRATE, false)
    }

    @JvmStatic
    fun setShakeToReset(shakeToReset: Boolean) {
        prefs!!.edit().putBoolean(PREF_SHAKE_TO_RESET, shakeToReset).apply()
    }

    @JvmStatic
    fun shakeToReset(): Boolean {
        return prefs!!.getBoolean(PREF_SHAKE_TO_RESET, true)
    }

    @JvmStatic
    fun setAutoEnable(autoEnable: Boolean) {
        prefs!!.edit().putBoolean(PREF_AUTO_ENABLE, autoEnable).apply()
    }

    @JvmStatic
    fun autoEnable(): Boolean {
        return prefs!!.getBoolean(PREF_AUTO_ENABLE, false)
    }

    @JvmStatic
    fun setAutoEnableFrom(hourOfDay: Int) {
        prefs!!.edit().putInt(PREF_AUTO_ENABLE_FROM, hourOfDay).apply()
    }

    @JvmStatic
    fun autoEnableFrom(): Int {
        return prefs!!.getInt(PREF_AUTO_ENABLE_FROM, DEFAULT_AUTO_ENABLE_FROM)
    }

    @JvmStatic
    fun setAutoEnableTo(hourOfDay: Int) {
        prefs!!.edit().putInt(PREF_AUTO_ENABLE_TO, hourOfDay).apply()
    }

    @JvmStatic
    fun autoEnableTo(): Int {
        return prefs!!.getInt(PREF_AUTO_ENABLE_TO, DEFAULT_AUTO_ENABLE_TO)
    }

    @JvmStatic
    fun isInTimeRange(from: Int, to: Int, current: Int): Boolean {
        // Range covers one day
        if (from < to) {
            return from <= current && current < to
        }

        // Range covers two days
        if (from <= current) {
            return true
        }

        return current < to
    }
}
