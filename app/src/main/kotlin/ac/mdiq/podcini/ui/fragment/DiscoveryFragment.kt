package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.searcher.ItunesTopListLoader
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class DiscoveryFragment : Fragment() {
    internal val prefs: SharedPreferences by lazy { requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

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

        internal var showSelectCounrty by mutableStateOf(false)
    }

    private lateinit var vm: DiscoveryVM

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { DiscoveryScreen() } } }
        return composeView
    }

    @Composable
    fun DiscoveryScreen() {
        if (!::vm.isInitialized) vm = remember { DiscoveryVM() }

        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        Logd(TAG, "ON_CREATE")
                        vm.countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)
                        vm.hidden = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)
                        vm.needsConfirm = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
                        loadToplist(vm.countryCode)
                    }
                    Lifecycle.Event.ON_START -> {
                        Logd(TAG, "ON_START")
                    }
                    Lifecycle.Event.ON_STOP -> {
                        Logd(TAG, "ON_STOP")
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        Logd(TAG, "ON_DESTROY")
                        vm.searchResults.clear()
                        vm.topList = null
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val textColor = MaterialTheme.colorScheme.onSurface

        if (vm.showSelectCounrty == true) SelectCountryDialog { vm.showSelectCounrty = false }

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
                        vm.showSelectCounrty = true
                        expanded = false
                    })
                }
            }
        )
    }

    @Composable
    fun SelectCountryDialog(onDismiss: () -> Unit) {
        val countryNameCodeMap: MutableMap<String, String> = remember { hashMapOf() }
        val countryCodeNameMap: MutableMap<String?, String> = remember { hashMapOf() }
        val countryNamesSort = remember { mutableStateListOf<String>() }
        var selectedCountry by remember { mutableStateOf("") }
        var textInput by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val countryCodeArray: List<String> = listOf(*Locale.getISOCountries())
            for (code in countryCodeArray) {
                val locale = Locale("", code)
                val countryName = locale.displayCountry
                Logd(TAG, "code: $code countryName: $countryName")
                countryCodeNameMap[code] = countryName
                countryNameCodeMap[countryName] = code
            }
            countryNamesSort.addAll(countryCodeNameMap.values)
            countryNamesSort.sort()
            selectedCountry = countryCodeNameMap[vm.countryCode] ?: ""
            textInput = selectedCountry
        }
        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun CountrySelection() {
            val filteredCountries = remember { countryNamesSort.toMutableStateList() }
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                TextField(value = textInput, modifier = Modifier.fillMaxWidth().padding(20.dp).menuAnchor(MenuAnchorType.PrimaryNotEditable, false), readOnly = false,
                    onValueChange = { input ->
                        textInput = input
                        if (textInput.length > 1) {
                            filteredCountries.clear()
                            filteredCountries.addAll(countryNamesSort.filter { it.contains(input, ignoreCase = true) }.take(5))
                            Logd(TAG, "input: $input filteredCountries: ${filteredCountries.size}")
                            expanded = filteredCountries.isNotEmpty()
                        }
                    },
                    label = { Text(stringResource(id = R.string.select_country)) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filteredCountries.forEach { country ->
                        DropdownMenuItem(text = { Text(text = country) }, onClick = {
                            selectedCountry = country
                            textInput = country
                            expanded = false
                        })
                    }
                }
            }
        }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.pref_custom_media_dir_title), style = CustomTextStyles.titleCustom) },
            text = { CountrySelection() },
            confirmButton = {
                TextButton(onClick = {
                    if (countryNameCodeMap.containsKey(selectedCountry)) {
                        vm.countryCode = countryNameCodeMap[selectedCountry]
                        vm.hidden = false
                    }
                    prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, vm.hidden).apply()
                    prefs.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, vm.countryCode).apply()
                    EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                    loadToplist(vm.countryCode)
                    onDismiss()
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    companion object {
        val TAG = DiscoveryFragment::class.simpleName ?: "Anonymous"
        private const val NUM_OF_TOP_PODCASTS = 25
    }
}
