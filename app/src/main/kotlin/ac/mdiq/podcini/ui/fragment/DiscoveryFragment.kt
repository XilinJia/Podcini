package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SelectCountryDialogBinding
import ac.mdiq.podcini.net.feed.searcher.ItunesTopListLoader
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class DiscoveryFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

    class DiscoveryVM {
        internal var topList: List<PodcastSearchResult>? = listOf()

        internal var countryCode: String? = "US"
        internal var hidden by mutableStateOf(false)
        internal var needsConfirm = false

        internal var searchResults = mutableStateListOf<PodcastSearchResult>()
        internal var errorText by mutableStateOf("")
        internal var retryQerry by mutableStateOf("")
        internal var showProgress by mutableStateOf(true)
        internal var noResultText by mutableStateOf("")
    }

    private val vm = DiscoveryVM()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)
        vm.hidden = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)
        vm.needsConfirm = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            val (gridView, progressBar, empty, txtvError, butRetry) = createRefs()
                            if (vm.showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
                            val lazyListState = rememberLazyListState()
                            if (vm.searchResults.isNotEmpty()) LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                                .constrainAs(gridView) {
                                    top.linkTo(parent.top)
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                },
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(vm.searchResults.size) { index -> OnlineFeedItem(activity = activity as MainActivity, vm.searchResults[index]) }
                            }
                            if (vm.searchResults.isEmpty()) Text(vm.noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
                            if (vm.errorText.isNotEmpty()) Text(vm.errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
                            if (vm.retryQerry.isNotEmpty()) Button(
                                modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) },
                                onClick = {
                                    if (vm.needsConfirm) {
                                        prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                                        vm.needsConfirm = false
                                    }
                                    loadToplist(vm.countryCode)
                                },
                            ) { Text(stringResource(id = R.string.retry_label)) }
//                Text( getString(R.string.search_powered_by, searchProvider!!.name), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(
//                    Color.LightGray)
//                    .constrainAs(powered) {
//                        bottom.linkTo(parent.bottom)
//                        end.linkTo(parent.end)
//                    })
                        }
                    }
                }
            }
        }
        loadToplist(vm.countryCode)
        return composeView
    }

    override fun onDestroy() {
        vm.searchResults.clear()
        vm.topList = null
        super.onDestroy()
    }

    private fun loadToplist(country: String?) {
        vm.searchResults.clear()
        vm.errorText = ""
        vm.retryQerry = ""
        vm.noResultText = ""
        vm.showProgress = true
        if (vm.hidden) {
            vm.errorText = resources.getString(R.string.discover_is_hidden)
            vm.showProgress = false
            return
        }
        if (BuildConfig.FLAVOR == "free" && vm.needsConfirm) {
            vm.errorText = ""
            vm.retryQerry = resources.getString(R.string.discover_confirm)
            vm.noResultText = ""
            vm.showProgress = false
            return
        }

        val loader = ItunesTopListLoader(requireContext())
        lifecycleScope.launch {
            try {
                val podcasts = withContext(Dispatchers.IO) { loader.loadToplist(country?:"", NUM_OF_TOP_PODCASTS, getFeedList()) }
                withContext(Dispatchers.Main) {
                    vm.showProgress = false
                    vm.topList = podcasts
                    vm.searchResults.clear()
                    if (!vm.topList.isNullOrEmpty()) {
                        vm.searchResults.addAll(vm.topList!!)
                        vm.noResultText = ""
                    } else vm.noResultText = getString(R.string.no_results_for_query)
                    vm.showProgress = false
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                vm.searchResults.clear()
                vm.errorText = e.message ?: "no error message"
                vm.retryQerry = " retry"
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text(stringResource(R.string.discover)) },
            navigationIcon = { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    var hideChecked by remember { mutableStateOf(vm.hidden) }
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.discover_hide))
                            Checkbox(checked = hideChecked, onCheckedChange = {  })
                        }
                    }, onClick = {
                        hideChecked = !hideChecked
                        vm.hidden = hideChecked
                        prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, vm.hidden).apply()
                        EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                        loadToplist(vm.countryCode)
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.select_country)) }, onClick = {
                        selectCountry()
                        expanded = false
                    })
                }
            }
        )
    }

    private fun selectCountry() {
        val inflater = layoutInflater
        val selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setView(selectCountryDialogView)

        val countryCodeArray: List<String> = listOf(*Locale.getISOCountries())
        val countryCodeNames: MutableMap<String?, String> = HashMap()
        val countryNameCodes: MutableMap<String, String> = HashMap()
        for (code in countryCodeArray) {
            val locale = Locale("", code)
            val countryName = locale.displayCountry
            countryCodeNames[code] = countryName
            countryNameCodes[countryName] = code
        }

        val countryNamesSort: MutableList<String> = ArrayList(countryCodeNames.values)
        countryNamesSort.sort()

        val dataAdapter = ArrayAdapter(this.requireContext(), android.R.layout.simple_list_item_1, countryNamesSort)
        val scBinding = SelectCountryDialogBinding.bind(selectCountryDialogView)
        val textInput = scBinding.countryTextInput
        val editText = textInput.editText as? MaterialAutoCompleteTextView
        editText!!.setAdapter(dataAdapter)
        editText.setText(countryCodeNames[vm.countryCode])
        editText.setOnClickListener {
            if (editText.text.isNotEmpty()) {
                editText.setText("")
                editText.postDelayed({ editText.showDropDown() }, 100)
            }
        }
        editText.onFocusChangeListener = View.OnFocusChangeListener { _: View?, hasFocus: Boolean ->
            if (hasFocus) {
                editText.setText("")
                editText.postDelayed({ editText.showDropDown() }, 100)
            }
        }

        builder.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
            val countryName = editText.text.toString()
            if (countryNameCodes.containsKey(countryName)) {
                vm.countryCode = countryNameCodes[countryName]
                vm.hidden = false
            }

            prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, vm.hidden).apply()
            prefs.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, vm.countryCode).apply()

            EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
            loadToplist(vm.countryCode)
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    companion object {
        val TAG = DiscoveryFragment::class.simpleName ?: "Anonymous"
        private const val NUM_OF_TOP_PODCASTS = 25
    }
}
