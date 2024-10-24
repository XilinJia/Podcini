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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedItem: String, onItemSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(readOnly = true, value = selectedItem, onValueChange = {}, label = { Text("Select an item") },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, colors = ExposedDropdownMenuDefaults.textFieldColors())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) },
                    onClick = {
                        onItemSelected(item)
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