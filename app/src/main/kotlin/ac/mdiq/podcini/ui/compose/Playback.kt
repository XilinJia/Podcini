package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SkipDialog(direction: SkipDirection, onDismissRequest: ()->Unit, callBack: (Int)->Unit) {
    val titleRes = if (direction == SkipDirection.SKIP_FORWARD) R.string.pref_fast_forward else R.string.pref_rewind
    var interval by remember { mutableStateOf((if (direction == SkipDirection.SKIP_FORWARD) UserPreferences.fastForwardSecs else UserPreferences.rewindSecs).toString()) }
    AlertDialog(onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge) },
        text = {
            TextField(value = interval,  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number), label = { Text("seconds") }, singleLine = true,
                onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it }) },
        confirmButton = {
            TextButton(onClick = {
                if (interval.isNotBlank()) {
                    val value = interval.toInt()
                    if (direction == SkipDirection.SKIP_FORWARD) UserPreferences.fastForwardSecs = value
                    else UserPreferences.rewindSecs = value
                    callBack(value)
                    onDismissRequest()
                }
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
    )
}

enum class SkipDirection {
    SKIP_FORWARD, SKIP_REWIND
}