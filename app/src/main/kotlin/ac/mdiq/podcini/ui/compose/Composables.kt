package ac.mdiq.podcini.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
private fun CustomTextField(
        modifier: Modifier = Modifier,
        leadingIcon: (@Composable () -> Unit)? = null,
        trailingIcon: (@Composable () -> Unit)? = null,
        placeholderText: String = "Placeholder",
        fontSize: TextUnit = MaterialTheme.typography.bodyMedium.fontSize
) {
    var text by rememberSaveable { mutableStateOf("") }
    BasicTextField(
        modifier = modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).fillMaxWidth(),
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize
        ),
        decorationBox = { innerTextField ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) leadingIcon()
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty())
                        Text(text = placeholderText, style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = fontSize))
                    innerTextField()
                }
                if (trailingIcon != null) trailingIcon()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedItem: String, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var currentSelectedItem by remember { mutableStateOf(selectedItem) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = currentSelectedItem, onValueChange = {},
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

@Composable
fun CustomToast(message: String, durationMillis: Long = 2000L, onDismiss: () -> Unit) {
    // Launch a coroutine to auto-dismiss the toast after a certain time
    LaunchedEffect(message) {
        delay(durationMillis)
        onDismiss()
    }

    // Box to display the toast message at the bottom of the screen
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Box(modifier = Modifier.background(Color.Black, RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AutoCompleteTextView(suggestions: List<String>, onItemSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        TextField(value = text,
            onValueChange = { text = it },
            label = { Text("Search") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = "Expand")
                }
            }
        )

        if (expanded) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(text = {Text(suggestion)}, onClick = {
                        onItemSelected(suggestion)
                        text = suggestion
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
fun LargeTextEditingDialog(textState: TextFieldValue, onTextChange: (TextFieldValue) -> Unit, onDismissRequest: () -> Unit, onSave: (String) -> Unit) {
    Dialog(onDismissRequest = { onDismissRequest() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Add comment", color = textColor, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                BasicTextField(value = textState, onValueChange = { onTextChange(it) }, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onDismissRequest() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        onSave(textState.text)
                        onDismissRequest()
                    }) {
                        Text("Save")
                    }
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
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (index < itemCount) content(index)
                    }
                }
            }
        }
    }
}