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
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.TAG
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MotionEvent
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SleepTimerDialog : DialogFragment() {
    private var _binding: TimeDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var etxtTime: EditText
    private lateinit var timeSetup: LinearLayout
    private lateinit var timeDisplay: LinearLayout
    private lateinit var time: TextView
    private lateinit var chAutoEnable: CheckBox

    @UnstableApi override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    @UnstableApi override fun onStop() {
        super.onStop()
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
            showTimeRangeDialog(from, to)
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
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    fun extendSleepTimer(extendTime: Long) {
        val timeLeft = playbackService?.taskManager?.sleepTimerTimeLeft ?: Playable.INVALID_TIME.toLong()
        if (timeLeft != Playable.INVALID_TIME.toLong()) setSleepTimer(timeLeft + extendTime)
    }

    fun setSleepTimer(time: Long) {
        playbackService?.taskManager?.setSleepTimer(time)
    }

    private fun showTimeRangeDialog(from: Int, to: Int) {
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

    class TimeRangeDialog(context: Context, from: Int, to: Int) : MaterialAlertDialogBuilder(context) {
        private val view = TimeRangeView(context, from, to)

        init {
            setView(view)
            setPositiveButton(android.R.string.ok, null)
        }

        val from: Int
            get() = view.from

        val to: Int
            get() = view.to

        internal class TimeRangeView @JvmOverloads constructor(context: Context, internal var from: Int = 0, var to: Int = 0) : View(context) {
            private val paintDial = Paint()
            private val paintSelected = Paint()
            private val paintText = Paint()
            private val bounds = RectF()
            var touching: Int = 0

            init {
                setup()
            }

            private fun setup() {
                paintDial.isAntiAlias = true
                paintDial.style = Paint.Style.STROKE
                paintDial.strokeCap = Paint.Cap.ROUND
                paintDial.color = getColorFromAttr(context, android.R.attr.textColorPrimary)
                paintDial.alpha = DIAL_ALPHA

                paintSelected.isAntiAlias = true
                paintSelected.style = Paint.Style.STROKE
                paintSelected.strokeCap = Paint.Cap.ROUND
                paintSelected.color = getColorFromAttr(context, androidx.appcompat.R.attr.colorAccent)

                paintText.isAntiAlias = true
                paintText.style = Paint.Style.FILL
                paintText.color = getColorFromAttr(context, android.R.attr.textColorPrimary)
                paintText.textAlign = Paint.Align.CENTER
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                when {
                    MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY ->
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY -> super.onMeasure(widthMeasureSpec, widthMeasureSpec)
                    MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY -> super.onMeasure(heightMeasureSpec, heightMeasureSpec)
                    MeasureSpec.getSize(widthMeasureSpec) < MeasureSpec.getSize(heightMeasureSpec) -> super.onMeasure(widthMeasureSpec, widthMeasureSpec)
                    else -> super.onMeasure(heightMeasureSpec, heightMeasureSpec)
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val size = height.toFloat() // square
                val padding = size * 0.1f
                paintDial.strokeWidth = size * 0.005f
                bounds[padding, padding, size - padding] = size - padding

                paintText.alpha = DIAL_ALPHA
                canvas.drawArc(bounds, 0f, 360f, false, paintDial)
                for (i in 0..23) {
                    paintDial.strokeWidth = size * 0.005f
                    if (i % 6 == 0) {
                        paintDial.strokeWidth = size * 0.01f
                        val textPos = radToPoint(i / 24.0f * 360f, size / 2 - 2.5f * padding)
                        paintText.textSize = 0.4f * padding
                        canvas.drawText(i.toString(), textPos.x.toFloat(),
                            textPos.y + (-paintText.descent() - paintText.ascent()) / 2, paintText)
                    }
                    val outer = radToPoint(i / 24.0f * 360f, size / 2 - 1.7f * padding)
                    val inner = radToPoint(i / 24.0f * 360f, size / 2 - 1.9f * padding)
                    canvas.drawLine(outer.x.toFloat(), outer.y.toFloat(), inner.x.toFloat(), inner.y.toFloat(), paintDial)
                }
                paintText.alpha = 255

                val angleFrom = from.toFloat() / 24 * 360 - 90
                val angleDistance = ((to - from + 24) % 24).toFloat() / 24 * 360
                paintSelected.strokeWidth = padding / 6
                paintSelected.style = Paint.Style.STROKE
                canvas.drawArc(bounds, angleFrom, angleDistance, false, paintSelected)
                paintSelected.style = Paint.Style.FILL
                val p1 = radToPoint(angleFrom + 90, size / 2 - padding)
                canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), padding / 2, paintSelected)
                val p2 = radToPoint(angleFrom + angleDistance + 90, size / 2 - padding)
                canvas.drawCircle(p2.x.toFloat(), p2.y.toFloat(), padding / 2, paintSelected)

                paintText.textSize = 0.6f * padding
                val timeRange = when {
                    from == to -> context.getString(R.string.sleep_timer_always)
                    DateFormat.is24HourFormat(context) -> String.format(Locale.getDefault(), "%02d:00 - %02d:00", from, to)
                    else -> {
                        String.format(Locale.getDefault(), "%02d:00 %s - %02d:00 %s", from % 12,
                            if (from >= 12) "PM" else "AM", to % 12, if (to >= 12) "PM" else "AM")
                    }
                }
                canvas.drawText(timeRange, size / 2, (size - paintText.descent() - paintText.ascent()) / 2, paintText)
            }

            private fun radToPoint(angle: Float, radius: Float): Point {
                return Point((width / 2 + radius * sin(-angle * Math.PI / 180 + Math.PI)).toInt(),
                    (height / 2 + radius * cos(-angle * Math.PI / 180 + Math.PI)).toInt())
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                parent.requestDisallowInterceptTouchEvent(true)
                val center = Point(width / 2, height / 2)
                val angleRad = atan2((center.y - event.y).toDouble(), (center.x - event.x).toDouble())
                var angle = (angleRad * (180 / Math.PI)).toFloat()
                angle += (360 + 360 - 90).toFloat()
                angle %= 360f

                when {
                    event.action == MotionEvent.ACTION_DOWN -> {
                        val fromDistance = abs((angle - from.toFloat() / 24 * 360).toDouble()).toFloat()
                        val toDistance = abs((angle - to.toFloat() / 24 * 360).toDouble()).toFloat()
                        when {
                            fromDistance < 15 || fromDistance > (360 - 15) -> {
                                touching = 1
                                return true
                            }
                            toDistance < 15 || toDistance > (360 - 15) -> {
                                touching = 2
                                return true
                            }
                        }
                    }
                    event.action == MotionEvent.ACTION_MOVE -> {
                        val newTime = (24 * (angle / 360.0)).toInt()
                        // Switch which handle is focussed such that selection is the smaller arc
                        if (from == to && touching != 0) touching = if ((((newTime - to + 24) % 24) < 12)) 2 else 1

                        when (touching) {
                            1 -> {
                                from = newTime
                                invalidate()
                                return true
                            }
                            2 -> {
                                to = newTime
                                invalidate()
                                return true
                            }
                        }
                    }
                    touching != 0 -> {
                        touching = 0
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }

            companion object {
                private const val DIAL_ALPHA = 120
            }
        }
    }
}
