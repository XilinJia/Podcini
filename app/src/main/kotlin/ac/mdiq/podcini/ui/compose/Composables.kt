package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.getPref
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.FeedInfoFragment
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment
import ac.mdiq.podcini.util.Logd
import android.app.Activity
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.NumberFormat

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
            appPrefs.edit().putBoolean(prefName, it).apply() })
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
fun MediaPlayerErrorDialog(activity: Activity, message: String, showDialog: MutableState<Boolean>) {
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
