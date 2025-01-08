package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
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
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.DurationConverter.convertOnSpeed
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.lcScope
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedItem: String, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var currentSelectedItem by remember { mutableStateOf(selectedItem) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = currentSelectedItem, onValueChange = { currentSelectedItem = it},
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
            modifier = modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
            decorationBox = { innerTextField ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                    innerTextField()
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (i in items.indices) {
                DropdownMenuItem(text = { Text(items[i]) },
                    onClick = {
                        currentSelectedItem = items[i]
                        onItemSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedIndex: Int, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var curIndex by remember { mutableIntStateOf(selectedIndex) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = items.getOrNull(curIndex) ?: "Select Item", onValueChange = { },
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
            modifier = modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
            decorationBox = { innerTextField ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                    innerTextField()
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (i in items.indices) {
                DropdownMenuItem(text = { Text(items[i]) },
                    onClick = {
                        curIndex = i
                        onItemSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerExternalSet(items: List<String>, selectedIndex: Int, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = items.getOrNull(selectedIndex) ?: "Select Item", onValueChange = { },
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
            modifier = modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
            decorationBox = { innerTextField ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                    innerTextField()
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (i in items.indices) {
                DropdownMenuItem(text = { Text(items[i]) },
                    onClick = {
                        onItemSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CustomToast(message: String, durationMillis: Long = 2000L, onDismiss: () -> Unit) {
    // Launch a coroutine to auto-dismiss the toast after a certain time
    LaunchedEffect(message) {
        delay(durationMillis)
        onDismiss()
    }
    Popup(onDismissRequest = { onDismiss() }) {
        Box(modifier = Modifier.background(Color.Black, RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun LargeTextEditingDialog(textState: TextFieldValue, onTextChange: (TextFieldValue) -> Unit, onDismissRequest: () -> Unit, onSave: (String) -> Unit) {
    Dialog(onDismissRequest = { onDismissRequest() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Add comment", color = textColor, style = CustomTextStyles.titleCustom)
                Spacer(modifier = Modifier.height(16.dp))
                BasicTextField(value = textState, onValueChange = { onTextChange(it) }, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                    TextButton(onClick = {
                        onSave(textState.text)
                        onDismissRequest()
                    }) { Text("Save") }
                }
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(10000)
                onSave(textState.text)
            }
        }
    }
}

@Composable
fun NonlazyGrid(columns: Int, itemCount: Int, modifier: Modifier = Modifier, content: @Composable() (Int) -> Unit) {
    Column(modifier = modifier) {
        var rows = (itemCount / columns)
        if (itemCount.mod(columns) > 0) rows += 1
        for (rowId in 0 until rows) {
            val firstIndex = rowId * columns
            Row {
                for (columnId in 0 until columns) {
                    val index = firstIndex + columnId
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { if (index < itemCount) content(index) }
                }
            }
        }
    }
}

@Composable
fun SimpleSwitchDialog(title: String, text: String, onDismissRequest: ()->Unit, callback: (Boolean)-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    var isChecked by remember { mutableStateOf(false) }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        title = { Text(title, style = CustomTextStyles.titleCustom) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Text(text, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = isChecked, onCheckedChange = { isChecked = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                callback(isChecked)
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

@Composable
fun IconTitleSummaryActionRow(vecRes: Int, titleRes: Int, summaryRes: Int, callback: ()-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
        Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
        Column(modifier = Modifier.weight(1f).clickable(onClick = { callback() })) {
            Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
            Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TitleSummaryActionColumn(titleRes: Int, summaryRes: Int, callback: ()-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = { callback() })) {
        if (titleRes != 0) Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
        if (summaryRes != 0) Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TitleSummarySwitchPrefRow(titleRes: Int, summaryRes: Int, prefName: String, default: Boolean = false) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
            Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        var isChecked by remember { mutableStateOf(getPref(prefName, default)) }
        Switch(checked = isChecked, onCheckedChange = {
            isChecked = it
            putPref(prefName, it) })
    }
}

@Composable
fun ComfirmDialog(titleRes: Int, message: String, showDialog: MutableState<Boolean>, cancellable: Boolean = true, onConfirm: () -> Unit) {
    if (showDialog.value) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showDialog.value = false },
            title = { if (titleRes != 0) Text(stringResource(titleRes)) },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) { Text(message) }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    showDialog.value = false
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { if (cancellable) TextButton(onClick = { showDialog.value = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
}

@Composable
fun MediaPlayerErrorDialog(activity: Context, message: String, showDialog: MutableState<Boolean>) {
    if (showDialog.value) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showDialog.value = false },
            title = { Text(stringResource(R.string.error_label)) },
            text = {
                val genericMessage: String = activity.getString(R.string.playback_error_generic)
                val errorMessage = SpannableString("""
                                    $genericMessage
                                    
                                    $message
                                    """.trimIndent())
                errorMessage.setSpan(ForegroundColorSpan(-0x77777778), genericMessage.length, errorMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                Text(errorMessage.toString())
            },
            confirmButton = {
                TextButton(onClick = {
//                    if (activity is MainActivity) activity.bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                    showDialog.value = false
                }) { Text("OK") }
            },
        )
    }
}

@Composable
fun SearchBarRow(hintTextRes: Int, defaultText: String = "", performSearch: (String) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        var queryText by remember { mutableStateOf(defaultText) }
        TextField(value = queryText, onValueChange = { queryText = it }, label = { Text(stringResource(hintTextRes)) },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { performSearch(queryText) }), modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), tint = textColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(40.dp).height(40.dp).padding(start = 5.dp).clickable(onClick = { performSearch(queryText) }))
    }
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