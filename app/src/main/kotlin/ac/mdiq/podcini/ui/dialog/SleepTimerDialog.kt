package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.TimeDialogBinding
import ac.mdiq.podcini.playback.PlaybackController.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnable
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableFrom
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableTo
import ac.mdiq.podcini.preferences.SleepTimerPreferences.lastTimerValue
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setAutoEnable
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setAutoEnableFrom
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setAutoEnableTo
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setLastTimer
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setShakeToReset
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setVibrate
import ac.mdiq.podcini.preferences.SleepTimerPreferences.shakeToReset
import ac.mdiq.podcini.preferences.SleepTimerPreferences.timerMillis
import ac.mdiq.podcini.preferences.SleepTimerPreferences.vibrate
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class SleepTimerDialog : DialogFragment() {
    private var _binding: TimeDialogBinding? = null
    private val binding get() = _binding!!

//    private lateinit var controller: PlaybackController
    private lateinit var etxtTime: EditText
    private lateinit var timeSetup: LinearLayout
    private lateinit var timeDisplay: LinearLayout
    private lateinit var time: TextView
    private lateinit var chAutoEnable: CheckBox

    @UnstableApi override fun onStart() {
        super.onStart()
//        controller = object : PlaybackController(requireActivity()) {
//            override fun loadMediaInfo() {}
//        }
//        controller.init()
        procFlowEvents()
    }

    @UnstableApi override fun onStop() {
        super.onStop()
//        controller.release()
        cancelFlowEvents()
    }

    @UnstableApi override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = TimeDialogBinding.inflate(layoutInflater)
        val content = binding.root
//        val content = View.inflate(context, R.layout.time_dialog, null)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.sleep_timer_label)
        builder.setView(binding.root)
        builder.setPositiveButton(R.string.close_label, null)

        etxtTime = binding.etxtTime
        timeSetup = binding.timeSetup
        timeDisplay = binding.timeDisplay
        timeDisplay.visibility = View.GONE
        time = binding.time
        val extendSleepFiveMinutesButton = binding.extendSleepFiveMinutesButton
        extendSleepFiveMinutesButton.text = getString(R.string.extend_sleep_timer_label, 5)
        val extendSleepTenMinutesButton = binding.extendSleepTenMinutesButton
        extendSleepTenMinutesButton.text = getString(R.string.extend_sleep_timer_label, 10)
        val extendSleepTwentyMinutesButton = binding.extendSleepTwentyMinutesButton
        extendSleepTwentyMinutesButton.text = getString(R.string.extend_sleep_timer_label, 20)
        extendSleepFiveMinutesButton.setOnClickListener {
            extendSleepTimer((5 * 1000 * 60).toLong())
        }
        extendSleepTenMinutesButton.setOnClickListener {
            extendSleepTimer((10 * 1000 * 60).toLong())
        }
        extendSleepTwentyMinutesButton.setOnClickListener {
            extendSleepTimer((20 * 1000 * 60).toLong())
        }

        etxtTime.setText(lastTimerValue())
        etxtTime.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etxtTime, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        val cbShakeToReset = binding.cbShakeToReset
        val cbVibrate = binding.cbVibrate
        chAutoEnable = binding.chAutoEnable
        val changeTimesButton = binding.changeTimesButton

        cbShakeToReset.isChecked = shakeToReset()
        cbVibrate.isChecked = vibrate()
        chAutoEnable.setChecked(autoEnable())
        changeTimesButton.isEnabled = chAutoEnable.isChecked
        changeTimesButton.alpha = if (chAutoEnable.isChecked) 1.0f else 0.5f

        cbShakeToReset.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setShakeToReset(isChecked)
        }
        cbVibrate.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> setVibrate(isChecked) }
        chAutoEnable.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setAutoEnable(isChecked)
            changeTimesButton.isEnabled = isChecked
            changeTimesButton.alpha = if (isChecked) 1.0f else 0.5f
        }
        updateAutoEnableText()

        changeTimesButton.setOnClickListener {
            val from = autoEnableFrom()
            val to = autoEnableTo()
            showTimeRangeDialog(requireContext(), from, to)
        }

        val disableButton = binding.disableSleeptimerButton
        disableButton.setOnClickListener {
            playbackService?.taskManager?.disableSleepTimer()
        }
        val setButton = binding.setSleeptimerButton
        setButton.setOnClickListener {
            if (!PlaybackService.isRunning) {
                Snackbar.make(content, R.string.no_media_playing_label, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                val time = etxtTime.getText().toString().toLong()
                if (time == 0L) throw NumberFormatException("Timer must not be zero")

                setLastTimer(etxtTime.getText().toString())
                setSleepTimer(timerMillis())
                
                closeKeyboard(content)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                Snackbar.make(content, R.string.time_dialog_invalid_input, Snackbar.LENGTH_LONG).show()
            }
        }
        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun extendSleepTimer(extendTime: Long) {
        val timeLeft = playbackService?.taskManager?.sleepTimerTimeLeft ?: Playable.INVALID_TIME.toLong()
        if (timeLeft != Playable.INVALID_TIME.toLong()) setSleepTimer(timeLeft + extendTime)
    }

    fun setSleepTimer(time: Long) {
        playbackService?.taskManager?.setSleepTimer(time)
    }

    private fun showTimeRangeDialog(context: Context, from: Int, to: Int) {
        val dialog = TimeRangeDialog(requireContext(), from, to)
        dialog.setOnDismissListener {
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

        when {
            from == to -> text = getString(R.string.auto_enable_label)
            DateFormat.is24HourFormat(context) -> {
                val formattedFrom = String.format(Locale.getDefault(), "%02d:00", from)
                val formattedTo = String.format(Locale.getDefault(), "%02d:00", to)
                text = getString(R.string.auto_enable_label_with_times, formattedFrom, formattedTo)
            }
            else -> {
                val formattedFrom = String.format(Locale.getDefault(), "%02d:00 %s", from % 12, if (from >= 12) "PM" else "AM")
                val formattedTo = String.format(Locale.getDefault(), "%02d:00 %s", to % 12, if (to >= 12) "PM" else "AM")
                text = getString(R.string.auto_enable_label_with_times, formattedFrom, formattedTo)
            }
        }
        chAutoEnable.text = text
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.SleepTimerUpdatedEvent -> timerUpdated(event)
                    else -> {}
                }
            }
        }
    }

    fun timerUpdated(event: FlowEvent.SleepTimerUpdatedEvent) {
        timeDisplay.visibility = if (event.isOver || event.isCancelled) View.GONE else View.VISIBLE
        timeSetup.visibility = if (event.isOver || event.isCancelled) View.VISIBLE else View.GONE
        time.text = getDurationStringLong(event.getTimeLeft().toInt())
    }

    private fun closeKeyboard(content: View) {
        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(content.windowToken, 0)
    }
}
