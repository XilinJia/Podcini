package ac.mdiq.podcini.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Spinner(
        items: List<String>,
        selectedItem: String,
        onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            readOnly = true,
            value = selectedItem,
            onValueChange = {},
            label = { Text("Select an item") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                ) {
                    Text(text = item)
                }
            }
        }
    }
}

@Composable
fun SpeedDial(
        modifier: Modifier = Modifier,
        mainButtonIcon: @Composable () -> Unit,
        fabButtons: List<@Composable () -> Unit>,
        onMainButtonClick: () -> Unit,
        onFabButtonClick: (Int) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (isExpanded) {
            fabButtons.forEachIndexed { index, button ->
                FloatingActionButton(
                    onClick = { onFabButtonClick(index) },
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    button()
                }
            }
        }
        FloatingActionButton(
            onClick = { onMainButtonClick(); isExpanded = !isExpanded },
//            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            mainButtonIcon()
        }
    }
}