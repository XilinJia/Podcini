package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// TODO: to be removed
abstract class EpisodeFilterDialog : BottomSheetDialogFragment() {

    var filter: EpisodeFilter? = null
    val filtersDisabled: MutableSet<EpisodeFilter.EpisodesFilterGroup> = mutableSetOf()
    private val filterValues: MutableSet<String> = mutableSetOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    MainView()
                }
            }
        }
        return composeView
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            var selectNone by remember { mutableStateOf(false) }
            for (item in EpisodeFilter.EpisodesFilterGroup.entries) {
                if (item in filtersDisabled) continue
                if (item.values.size == 2) {
                    Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        var selectedIndex by remember { mutableStateOf(-1) }
                        if (selectNone) selectedIndex = -1
                        LaunchedEffect(Unit) {
                            if (filter != null) {
                                if (item.values[0].filterId in filter!!.properties) selectedIndex = 0
                                else if (item.values[1].filterId in filter!!.properties) selectedIndex = 1
                            }
                        }
                        Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                        Spacer(Modifier.weight(0.3f))
                        OutlinedButton(
                            modifier = Modifier.padding(2.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) textColor else Color.Green),
                            onClick = {
                                if (selectedIndex != 0) {
                                    selectNone = false
                                    selectedIndex = 0
                                    filterValues.add(item.values[0].filterId)
                                    filterValues.remove(item.values[1].filterId)
                                } else {
                                    selectedIndex = -1
                                    filterValues.remove(item.values[0].filterId)
                                }
                                onFilterChanged(filterValues)
                            },
                        ) {
                            Text(text = stringResource(item.values[0].displayName), color = textColor)
                        }
                        Spacer(Modifier.weight(0.1f))
                        OutlinedButton(
                            modifier = Modifier.padding(2.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) textColor else Color.Green),
                            onClick = {
                                if (selectedIndex != 1) {
                                    selectNone = false
                                    selectedIndex = 1
                                    filterValues.add(item.values[1].filterId)
                                    filterValues.remove(item.values[0].filterId)
                                } else {
                                    selectedIndex = -1
                                    filterValues.remove(item.values[1].filterId)
                                }
                                onFilterChanged(filterValues)
                            },
                        ) {
                            Text(text = stringResource(item.values[1].displayName), color = textColor)
                        }
                        Spacer(Modifier.weight(0.5f))
                    }
                } else {
                    Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                        Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor)
                        NonlazyGrid(columns = 3, itemCount = item.values.size) { index ->
                            var selected by remember { mutableStateOf(false) }
                            if (selectNone) selected = false
                            OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(),
                                border = BorderStroke(2.dp, if (selected) Color.Green else textColor),
                                onClick = {
                                    selectNone = false
                                    selected = !selected
                                    if (selected) filterValues.add(item.values[index].filterId)
                                    else filterValues.remove(item.values[index].filterId)
                                    onFilterChanged(filterValues)
                                },
                            ) {
                                Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor)
                            }
                        }
                    }
                }
            }
            Row {
                Spacer(Modifier.weight(0.3f))
                Button(onClick = {
                    selectNone = true
                    onFilterChanged(setOf(""))
                }) {
                    Text(stringResource(R.string.reset))
                }
                Spacer(Modifier.weight(0.4f))
                Button(onClick = {
                    dismiss()
                }) {
                    Text(stringResource(R.string.close_label))
                }
                Spacer(Modifier.weight(0.3f))
            }
        }
    }

    abstract fun onFilterChanged(newFilterValues: Set<String>)

}
