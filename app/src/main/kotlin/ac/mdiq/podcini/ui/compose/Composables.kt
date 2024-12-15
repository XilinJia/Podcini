package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.util.ShareUtils.shareFeedItemFile
import ac.mdiq.podcini.util.ShareUtils.shareFeedItemLinkWithDownloadLink
import ac.mdiq.podcini.util.ShareUtils.shareMediaDownloadLink
import android.app.Activity
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedItem: String, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var currentSelectedItem by remember { mutableStateOf(selectedItem) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
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
                    TextButton(onClick = { onDismissRequest() }) { Text("Cancel") }
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
    AlertDialog(onDismissRequest = { onDismissRequest() },
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
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
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
        Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
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
        var isChecked by remember { mutableStateOf(appPrefs.getBoolean(prefName, default)) }
        Switch(checked = isChecked, onCheckedChange = {
            isChecked = it
            appPrefs.edit().putBoolean(prefName, it).apply() })
    }
}

@Composable
fun ComfirmDialog(titleRes: Int, message: String, showDialog: MutableState<Boolean>, cancellable: Boolean = true, onConfirm: () -> Unit) {
    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { if (titleRes != 0) Text(stringResource(titleRes)) },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) { Text(message) }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    showDialog.value = false
                }) { Text("Confirm") }
            },
            dismissButton = { if (cancellable) TextButton(onClick = { showDialog.value = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun MediaPlayerErrorDialog(activity: Activity, message: String, showDialog: MutableState<Boolean>) {
    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
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
                    if (activity is MainActivity) activity.bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                    showDialog.value = false
                }) { Text("Confirm") }
            },
        )
    }
}

@Composable
fun ComposableLifecycle(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, onEvent: (LifecycleOwner, Lifecycle.Event) -> Unit) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, event -> onEvent(source, event) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
fun ShareDialog(item: Episode, act: Activity, onDismissRequest: ()->Unit) {
    val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"
    val PREF_SHARE_EPISODE_TYPE = "prefShareEpisodeType"

    val prefs = remember { act.getSharedPreferences("ShareDialog", Context.MODE_PRIVATE) }
    val hasMedia = remember { item.media != null }
    val downloaded = remember { hasMedia && item.media!!.downloaded }
    val hasDownloadUrl = remember { hasMedia && item.media!!.downloadUrl != null }

    var type = remember { prefs.getInt(PREF_SHARE_EPISODE_TYPE, 1) }
    if ((type == 2 && !hasDownloadUrl) || (type == 3 && !downloaded)) type = 1

    var position by remember { mutableIntStateOf(type) }
    var isChecked by remember { mutableStateOf(false) }
    var ctx = LocalContext.current

    AlertDialog(onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(R.string.share_label), style = CustomTextStyles.titleCustom) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = position == 1, onClick = { position = 1 })
                    Text(stringResource(R.string.share_dialog_for_social))
                }
                if (hasDownloadUrl) Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = position == 2, onClick = { position = 2 })
                    Text(stringResource(R.string.share_dialog_media_address))
                }
                if (downloaded) Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = position == 3, onClick = { position = 3 })
                    Text(stringResource(R.string.share_dialog_media_file_label))
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth().height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                    Text(stringResource(R.string.share_playback_position_dialog_label))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when(position) {
                    1 -> shareFeedItemLinkWithDownloadLink(ctx, item, isChecked)
                    2 -> shareMediaDownloadLink(ctx, item.media!!)
                    3 -> shareFeedItemFile(ctx, item.media!!)
                }
                prefs.edit().putBoolean(PREF_SHARE_EPISODE_START_AT, isChecked).putInt(PREF_SHARE_EPISODE_TYPE, position).apply()
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
    )
}

@Composable
fun DatesFilterDialogCompose(inclPlayed: Boolean = false, from: Long? = null, to: Long? = null, oldestDate: Long, onDismissRequest: ()->Unit, callback: (Long, Long, Boolean) -> Unit) {
    @Composable
    fun MonthYearInput(default: String, onMonthYearChange: (String) -> Unit) {
        fun formatMonthYear(input: String): String {
            val sanitized = input.replace(Regex("[^0-9/]"), "")
            return when {
                sanitized.length > 7 -> sanitized.substring(0, 7)
                else -> sanitized
            }
        }
        fun isValidMonthYear(input: String): Boolean {
            val regex = Regex("^(0[1-9]|1[0-2])/\\d{4}$")
            return regex.matches(input)
        }
        var monthYear by remember { mutableStateOf(TextFieldValue(default)) }
        var isValid by remember { mutableStateOf(isValidMonthYear(monthYear.text)) }
        Column(modifier = Modifier.padding(16.dp)) {
            TextField(value = monthYear, label = { Text(stringResource(R.string.statistics_month_year)) },
                onValueChange = { input ->
                    monthYear = input
                    val formattedInput = formatMonthYear(monthYear.text)
                    isValid = isValidMonthYear(formattedInput)
                    if (isValid) onMonthYearChange(formattedInput)
                },
                isError = !isValidMonthYear(monthYear.text),
                modifier = Modifier.fillMaxWidth()
            )
            if (!isValid) Text(text = "Invalid format. Please use MM/YYYY.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
    fun convertMonthYearToUnixTime(monthYear: String, start: Boolean = true): Long? {
        val regex = Regex("^(0[1-9]|1[0-2])/\\d{4}$")
        if (!regex.matches(monthYear)) return null
        val (month, year) = monthYear.split("/").map { it.toInt() }
        val localDate = if (start) LocalDate.of(year, month, 1) else LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    fun convertUnixTimeToMonthYear(unixTime: Long): String {
        return Instant.ofEpochMilli(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MM/yyyy"))
    }
    var includeMarkedAsPlayed by remember { mutableStateOf(inclPlayed) }
    var timeFilterFrom by remember { mutableLongStateOf(from ?: oldestDate) }
    var timeFilterTo by remember { mutableLongStateOf(to ?: Date().time) }
    var useAllTime by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(R.string.share_label), style = CustomTextStyles.titleCustom) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeMarkedAsPlayed, onCheckedChange = {
                        includeMarkedAsPlayed = it
//                        if (includeMarkedAsPlayed) {
//                            timeFilterFrom = 0
//                            timeFilterTo = Long.MAX_VALUE
//                        }
                    })
                    Text(stringResource(R.string.statistics_include_marked))
                }
                if (!useAllTime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics_start_month))
                        MonthYearInput(convertUnixTimeToMonthYear(oldestDate)) { timeFilterFrom = convertMonthYearToUnixTime(it) ?: oldestDate }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.statistics_end_month))
                        MonthYearInput(convertUnixTimeToMonthYear(System.currentTimeMillis())) { timeFilterTo = convertMonthYearToUnixTime(it, false) ?: Date().time }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useAllTime, onCheckedChange = { useAllTime = it })
                    Text(stringResource(R.string.statistics_filter_all_time))
                }
                Text(stringResource(R.string.statistics_speed_not_counted))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (useAllTime) {
                    timeFilterFrom = oldestDate
                    timeFilterTo = Date().time
                }
                callback(timeFilterFrom, timeFilterTo, includeMarkedAsPlayed)
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
    )
}