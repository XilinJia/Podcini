package de.danoeh.antennapod.core.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Created by Tom on 1/5/15.
 */
object PowerUtils {
    /**
     * @return true if the device is charging
     */
    @JvmStatic
    fun deviceCharging(context: Context): Boolean {
        // from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, iFilter)

        val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL)
    }
}
