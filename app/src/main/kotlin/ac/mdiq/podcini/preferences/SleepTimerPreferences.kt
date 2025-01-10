package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.DurationConverter.convertOnSpeed
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

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
     * @throws IllegalArgumentException if context is null
     */
    @JvmStatic
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of SleepTimerPreferences")
        prefs = context.getSharedPreferences(Prefs.SleepTimerDialog.name, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun setLastTimer(value: String?) {  // in minutes
        prefs!!.edit().putString(Prefs.LastValue.name, value).apply()
    }

    @JvmStatic
    fun lastTimerValue(): String? { // in minutes
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

    @Composable
    fun SleepTimerDialog(onDismiss: () -> Unit) {
        val lcScope = rememberCoroutineScope()
        val timeLeft = remember { (playbackService?.taskManager?.sleepTimerTimeLeft?:0) }
        var showTimeDisplay by remember { mutableStateOf(false) }
        var showTimeSetup by remember { mutableStateOf(true) }
        var timerText by remember { mutableStateOf(getDurationStringLong(timeLeft.toInt())) }

        fun timerUpdated(event: FlowEvent.SleepTimerUpdatedEvent) {
            showTimeDisplay = !event.isOver && !event.isCancelled
            showTimeSetup = event.isOver || event.isCancelled
            timerText = getDurationStringLong(event.getTimeLeft().toInt())
        }

        var eventSink: Job? = remember { null }
        fun cancelFlowEvents() {
            eventSink?.cancel()
            eventSink = null
        }
        fun procFlowEvents() {
            if (eventSink != null) return
            eventSink = lcScope.launch {
                EventFlow.events.collectLatest { event ->
                    when (event) {
                        is FlowEvent.SleepTimerUpdatedEvent -> timerUpdated(event)
                        else -> {}
                    }
                }
            }
        }

        LaunchedEffect(Unit) { procFlowEvents() }
        DisposableEffect(Unit) { onDispose { cancelFlowEvents() } }

        var toEnd by remember { mutableStateOf(false) }
        var etxtTime by remember { mutableStateOf(lastTimerValue()?:"") }
        fun setSleepTimer(time: Long) {
            playbackService?.taskManager?.setSleepTimer(time)
        }
        fun extendSleepTimer(extendTime: Long) {
            val timeLeft = playbackService?.taskManager?.sleepTimerTimeLeft ?: Episode.INVALID_TIME.toLong()
            if (timeLeft != Episode.INVALID_TIME.toLong()) setSleepTimer(timeLeft + extendTime)
        }

        val scrollState = rememberScrollState()
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sleep_timer_label)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                    if (showTimeSetup) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                            Checkbox(checked = toEnd, onCheckedChange = { toEnd = it })
                            Text(stringResource(R.string.end_episode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                        }
                        if (!toEnd) TextField(value = etxtTime, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number), label = { Text(stringResource(R.string.time_minutes)) }, singleLine = true,
                            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) etxtTime = it })
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            if (!PlaybackService.isRunning) {
//                        Snackbar.make(content, R.string.no_media_playing_label, Snackbar.LENGTH_LONG).show()
                                return@Button
                            }
                            try {
                                val time = if (toEnd) {
                                    val curPosition = curEpisode?.position ?: 0
                                    val duration = curEpisode?.duration ?: 0
                                    TimeUnit.MILLISECONDS.toMinutes(convertOnSpeed(max((duration - curPosition).toDouble(), 0.0).toInt(), curSpeedFB).toLong()) // ms to minutes
                                } else etxtTime.toLong()
                                Logd("SleepTimerDialog", "Sleep timer set: $time")
                                if (time == 0L) throw NumberFormatException("Timer must not be zero")
                                setLastTimer(time.toString())
                                setSleepTimer(timerMillis())
                                showTimeSetup = false
                                showTimeDisplay = true
//                        closeKeyboard(content)
                            } catch (e: NumberFormatException) {
                                e.printStackTrace()
//                        Snackbar.make(content, R.string.time_dialog_invalid_input, Snackbar.LENGTH_LONG).show()
                            }
                        }) { Text(stringResource(R.string.set_sleeptimer_label)) }
                    }
                    if (showTimeDisplay || timeLeft > 0) {
                        Text(timerText, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Button(modifier = Modifier.fillMaxWidth(), onClick = { playbackService?.taskManager?.disableSleepTimer()
                        }) { Text(stringResource(R.string.disable_sleeptimer_label)) }
                        Row {
                            Button(onClick = { extendSleepTimer((10 * 1000 * 60).toLong())
                            }) { Text(stringResource(R.string.extend_sleep_timer_label, 10)) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { extendSleepTimer((30 * 1000 * 60).toLong())
                            }) { Text(stringResource(R.string.extend_sleep_timer_label, 30)) }
                        }
                    }
                    var cbShakeToReset by remember { mutableStateOf(shakeToReset()) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                        Checkbox(checked = cbShakeToReset, onCheckedChange = {
                            cbShakeToReset = it
                            setShakeToReset(it)
                        })
                        Text(stringResource(R.string.shake_to_reset_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                    var cbVibrate by remember { mutableStateOf(vibrate()) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                        Checkbox(checked = cbVibrate, onCheckedChange = {
                            cbVibrate = it
                            setVibrate(it)
                        })
                        Text(stringResource(R.string.timer_vibration_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                    var chAutoEnable by remember { mutableStateOf(autoEnable()) }
                    var enableChangeTime by remember { mutableStateOf(chAutoEnable) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                        Checkbox(checked = chAutoEnable, onCheckedChange = {
                            chAutoEnable = it
                            setAutoEnable(it)
                            enableChangeTime = it
                        })
                        Text(stringResource(R.string.auto_enable_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                    if (enableChangeTime) {
                        var from by remember { mutableStateOf(autoEnableFrom().toString()) }
                        var to by remember { mutableStateOf(autoEnableTo().toString()) }
                        Text(stringResource(R.string.auto_enable_sum), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
                            TextField(value = from, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number),
                                label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 8.dp),
                                onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) from = it })
                            TextField(value = to, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number),
                                label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 8.dp),
                                onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) to = it })
                            IconButton(onClick = {
                                setAutoEnableFrom(from.toInt())
                                setAutoEnableTo(to.toInt())
                            }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), contentDescription = "setting") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.close_label)) } }
        )
    }
}
