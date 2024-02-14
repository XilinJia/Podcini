package de.danoeh.antennapod.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.autoEnable
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.autoEnableFrom
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.autoEnableTo
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.lastTimerValue
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.setAutoEnable
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.setAutoEnableFrom
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.setAutoEnableTo
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.setLastTimer
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.setShakeToReset
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.setVibrate
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.shakeToReset
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.timerMillis
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.vibrate
import de.danoeh.antennapod.core.service.playback.PlaybackService
import de.danoeh.antennapod.core.util.Converter.getDurationStringLong
import de.danoeh.antennapod.core.util.playback.PlaybackController
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class SleepTimerDialog : DialogFragment() {
    private var controller: PlaybackController? = null
    private var etxtTime: EditText? = null
    private var timeSetup: LinearLayout? = null
    private var timeDisplay: LinearLayout? = null
    private var time: TextView? = null
    private var chAutoEnable: CheckBox? = null

    @UnstableApi override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(activity) {
            override fun loadMediaInfo() {
            }
        }
        controller?.init()
        EventBus.getDefault().register(this)
    }

    @UnstableApi override fun onStop() {
        super.onStop()
        controller?.release()
        EventBus.getDefault().unregister(this)
    }

    @UnstableApi override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = View.inflate(context, R.layout.time_dialog, null)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.sleep_timer_label)
        builder.setView(content)
        builder.setPositiveButton(R.string.close_label, null)

        etxtTime = content.findViewById(R.id.etxtTime)
        timeSetup = content.findViewById(R.id.timeSetup)
        timeDisplay = content.findViewById(R.id.timeDisplay)
        timeDisplay?.visibility = View.GONE
        time = content.findViewById(R.id.time)
        val extendSleepFiveMinutesButton = content.findViewById<Button>(R.id.extendSleepFiveMinutesButton)
        extendSleepFiveMinutesButton.text = getString(R.string.extend_sleep_timer_label, 5)
        val extendSleepTenMinutesButton = content.findViewById<Button>(R.id.extendSleepTenMinutesButton)
        extendSleepTenMinutesButton.text = getString(R.string.extend_sleep_timer_label, 10)
        val extendSleepTwentyMinutesButton = content.findViewById<Button>(R.id.extendSleepTwentyMinutesButton)
        extendSleepTwentyMinutesButton.text = getString(R.string.extend_sleep_timer_label, 20)
        extendSleepFiveMinutesButton.setOnClickListener { v: View? ->
            if (controller != null) {
                controller!!.extendSleepTimer((5 * 1000 * 60).toLong())
            }
        }
        extendSleepTenMinutesButton.setOnClickListener { v: View? ->
            if (controller != null) {
                controller!!.extendSleepTimer((10 * 1000 * 60).toLong())
            }
        }
        extendSleepTwentyMinutesButton.setOnClickListener { v: View? ->
            if (controller != null) {
                controller!!.extendSleepTimer((20 * 1000 * 60).toLong())
            }
        }

        etxtTime?.setText(lastTimerValue())
        etxtTime?.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etxtTime, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        val cbShakeToReset = content.findViewById<CheckBox>(R.id.cbShakeToReset)
        val cbVibrate = content.findViewById<CheckBox>(R.id.cbVibrate)
        chAutoEnable = content.findViewById(R.id.chAutoEnable)
        val changeTimesButton = content.findViewById<ImageView>(R.id.changeTimesButton)

        cbShakeToReset.isChecked = shakeToReset()
        cbVibrate.isChecked = vibrate()
        chAutoEnable?.setChecked(autoEnable())
        if (chAutoEnable != null) {
            changeTimesButton.isEnabled = chAutoEnable!!.isChecked
            changeTimesButton.alpha = if (chAutoEnable!!.isChecked) 1.0f else 0.5f
        }
        cbShakeToReset.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            setShakeToReset(isChecked)
        }
        cbVibrate.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> setVibrate(isChecked) }
        chAutoEnable?.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            setAutoEnable(isChecked)
            changeTimesButton.isEnabled = isChecked
            changeTimesButton.alpha = if (isChecked) 1.0f else 0.5f
        }
        updateAutoEnableText()

        changeTimesButton.setOnClickListener { changeTimesBtn: View? ->
            val from = autoEnableFrom()
            val to = autoEnableTo()
            showTimeRangeDialog(context, from, to)
        }

        val disableButton = content.findViewById<Button>(R.id.disableSleeptimerButton)
        disableButton.setOnClickListener { v: View? ->
            if (controller != null) {
                controller!!.disableSleepTimer()
            }
        }
        val setButton = content.findViewById<Button>(R.id.setSleeptimerButton)
        setButton.setOnClickListener { v: View? ->
            if (!PlaybackService.isRunning) {
                Snackbar.make(content, R.string.no_media_playing_label, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                if (etxtTime != null) {
                    val time = etxtTime!!.getText().toString().toLong()
                    if (time == 0L) {
                        throw NumberFormatException("Timer must not be zero")
                    }
                    setLastTimer(etxtTime!!.getText().toString())
                }
                if (controller != null) {
                    controller!!.setSleepTimer(timerMillis())
                }
                closeKeyboard(content)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                Snackbar.make(content, R.string.time_dialog_invalid_input, Snackbar.LENGTH_LONG).show()
            }
        }
        return builder.create()
    }

    private fun showTimeRangeDialog(context: Context?, from: Int, to: Int) {
        val dialog = TimeRangeDialog(requireContext(), from, to)
        dialog.setOnDismissListener { v: DialogInterface? ->
            setAutoEnableFrom(dialog.from)
            setAutoEnableTo(dialog.to)
            updateAutoEnableText()
        }
        dialog.show()
    }

    private fun updateAutoEnableText() {
        val text: String
        val from = autoEnableFrom()
        val to = autoEnableTo()

        if (from == to) {
            text = getString(R.string.auto_enable_label)
        } else if (DateFormat.is24HourFormat(context)) {
            val formattedFrom = String.format(Locale.getDefault(), "%02d:00", from)
            val formattedTo = String.format(Locale.getDefault(), "%02d:00", to)
            text = getString(R.string.auto_enable_label_with_times, formattedFrom, formattedTo)
        } else {
            val formattedFrom = String.format(Locale.getDefault(), "%02d:00 %s",
                from % 12, if (from >= 12) "PM" else "AM")
            val formattedTo = String.format(Locale.getDefault(), "%02d:00 %s",
                to % 12, if (to >= 12) "PM" else "AM")
            text = getString(R.string.auto_enable_label_with_times, formattedFrom, formattedTo)
        }
        chAutoEnable!!.text = text
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun timerUpdated(event: SleepTimerUpdatedEvent) {
        timeDisplay!!.visibility = if (event.isOver || event.isCancelled) View.GONE else View.VISIBLE
        timeSetup!!.visibility =
            if (event.isOver || event.isCancelled) View.VISIBLE else View.GONE
        time!!.text = getDurationStringLong(event.getTimeLeft().toInt())
    }

    private fun closeKeyboard(content: View) {
        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(content.windowToken, 0)
    }
}
