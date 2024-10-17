package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.fragments.ImportExportPreferencesFragment.*
import ac.mdiq.podcini.storage.database.Feeds
import ac.mdiq.podcini.storage.database.Feeds.createSynthetic
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.dialog.CustomFeedNameDialog
import ac.mdiq.podcini.ui.dialog.FeedSortDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.ui.fragment.FeedSettingsFragment.Companion.queueSettingOptions
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SubscriptionsFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var emptyView: EmptyViewHandler
    private lateinit var toolbar: MaterialToolbar

    private val tags: MutableList<String> = mutableListOf()
    private val queueIds: MutableList<Long> = mutableListOf()
    private lateinit var queuesAdapter: ArrayAdapter<String>
    private lateinit var tagsAdapter: ArrayAdapter<String>
    private var tagFilterIndex = 1
    private var queueFilterIndex = 0

    private var infoTextFiltered = ""
    private var infoTextUpdate = ""

    //    TODO: currently not used
    private var displayedFolder: String = ""
    private var displayUpArrow = false

    private var txtvInformation by mutableStateOf("")
    private var feedCount by mutableStateOf("")
    private var feedSorted by mutableIntStateOf(0)

    private var feedList: MutableList<Feed> = mutableListOf()
    private var feedListFiltered = mutableStateListOf<Feed>()

    private var useGrid by mutableStateOf<Boolean?>(null)
    private val useGridLayout by mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefFeedGridLayout.name, false))

    private var selectMode by mutableStateOf(false)

    private val swipeToRefresh: Boolean
        get() = appPrefs.getBoolean(UserPreferences.Prefs.prefSwipeToRefreshAll.name, true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.subscriptions)

        if (arguments != null) {
            displayedFolder = requireArguments().getString(ARGUMENT_FOLDER, null)
            toolbar.title = displayedFolder
        }

        binding.infobar.setContent {
            CustomTheme(requireContext()) {
                InforBar()
            }
        }
        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                LazyList()
            }
        }
        setupEmptyView()
        resetTags()

        val queues = realm.query(PlayQueue::class).find()
        queueIds.addAll(queues.map { it.id })
        val spinnerTexts: MutableList<String> = mutableListOf("Any queue", "No queue")
        spinnerTexts.addAll(queues.map { it.name })
        queuesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerTexts)
        queuesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.queuesSpinner.setAdapter(queuesAdapter)
        binding.queuesSpinner.setSelection(queuesAdapter.getPosition("Any queue"))
        binding.queuesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                queueFilterIndex = position
                loadSubscriptions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        tagsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tags)
        tagsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categorySpinner.setAdapter(tagsAdapter)
        binding.categorySpinner.setSelection(tagsAdapter.getPosition("All"))
        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                tagFilterIndex = position
//                filterOnTag()
                loadSubscriptions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        feedCount = feedListFiltered.size.toString() + " / " + feedList.size.toString()
        loadSubscriptions()
        return binding.root
    }

//    override fun onResume() {
//        Logd(TAG, "onResume() called")
//        super.onResume()
//    }

    override fun onStart() {
        Logd(TAG, "onStart()")
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        Logd(TAG, "onStop()")
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        feedList = mutableListOf()
        feedListFiltered.clear()
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun queryStringOfTags() : String {
        return when (tagFilterIndex) {
            1 ->  ""    // All feeds
//            TODO: #root appears not used in RealmDB, is it a SQLite specialty
            0 ->  " (preferences.tags.@count == 0 OR (preferences.tags.@count != 0 AND ALL preferences.tags == '#root' )) "
            else -> {   // feeds with the chosen tag
                val tag = tags[tagFilterIndex]
                " ANY preferences.tags == '$tag' "
            }
        }
    }

    private fun queryStringOfQueues() : String {
        return when (queueFilterIndex) {
            0 ->  ""    // All feeds
            1 -> " preferences.queueId == -2 "
            else -> {   // feeds associated with the chosen queue
                val qid = queueIds[queueFilterIndex-2]
                " preferences.queueId == '$qid' "
            }
        }
    }

    private fun resetTags() {
        tags.clear()
        tags.add("Untagged")
        tags.add("All")
        tags.addAll(getTags())
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedListEvent, is FlowEvent.FeedsSortedEvent -> loadSubscriptions()
                    is FlowEvent.FeedsFilterEvent -> loadSubscriptions()
                    is FlowEvent.EpisodePlayedEvent -> loadSubscriptions()
                    is FlowEvent.FeedTagsChangedEvent -> loadSubscriptions()
                    is FlowEvent.FeedPrefsChangeEvent -> loadSubscriptions()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedUpdatingEvent -> {
                        Logd(TAG, "FeedUpdateRunningEvent: ${event.isRunning}")
                        infoTextUpdate = if (event.isRunning) " " + getString(R.string.refreshing_label) else ""
                        txtvInformation = (infoTextFiltered + infoTextUpdate)
//                        if (swipeToRefresh) binding.swipeRefresh.isRefreshing = event.isRunning
                        if (!event.isRunning && event.id != prevFeedUpdatingEvent?.id) loadSubscriptions()
                        prevFeedUpdatingEvent = event
                    }
                    else -> {}
                }
            }
        }
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.subscriptions_filter -> FeedFilterDialog.newInstance(FeedFilter(feedsFilter)).show(childFragmentManager, null)
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            R.id.subscriptions_sort -> FeedSortDialog().show(childFragmentManager, "FeedSortDialog")
            R.id.new_synth -> {
                val feed = createSynthetic(0, "")
                feed.type = Feed.FeedType.RSS.name
                CustomFeedNameDialog(activity as Activity, feed).show()
            }
            R.id.new_synth_yt -> {
                val feed = createSynthetic(0, "")
                feed.type = Feed.FeedType.YOUTUBE.name
                feed.hasVideoMedia = true
                feed.preferences!!.videoModePolicy = VideoMode.WINDOW_VIEW
                CustomFeedNameDialog(activity as Activity, feed).show()
            }
            R.id.refresh_item -> FeedUpdateManager.runOnceOrAsk(requireContext())
            R.id.toggle_grid_list -> useGrid = if (useGrid == null) !useGridLayout else !useGrid!!
            else -> return false
        }
        return true
    }

    private fun setupEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_subscriptions)
        emptyView.setTitle(R.string.no_subscriptions_head_label)
        emptyView.setMessage(R.string.no_subscriptions_label)
//        emptyView.attachToRecyclerView(recyclerView)
    }

    private var loadItemsRunning = false
    @OptIn(UnstableApi::class)
    private fun loadSubscriptions() {
        emptyView.hide()
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        resetTags()
                        filterAndSort()
                    }
                    withContext(Dispatchers.Main) {
                        // We have fewer items. This can result in items being selected that are no longer visible.
//                        if (feedListFiltered.size > feedList.size) adapter.endSelectMode()
//                        filterOnTag()
                        feedListFiltered.clear()
                        feedListFiltered.addAll(feedList)
                        feedCount = feedListFiltered.size.toString() + " / " + feedList.size.toString()
                        infoTextFiltered = " "
                        if (feedsFilter.isNotEmpty()) infoTextFiltered = getString(R.string.filtered_label)
                        txtvInformation = (infoTextFiltered + infoTextUpdate)
                        emptyView.updateVisibility()
                    }
                } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
    }

    private fun filterAndSort() {
        var fQueryStr = FeedFilter(feedsFilter).queryString()
        val tagsQueryStr = queryStringOfTags()
        if (tagsQueryStr.isNotEmpty())  fQueryStr += " AND $tagsQueryStr"
        val queuesQueryStr = queryStringOfQueues()
        if (queuesQueryStr.isNotEmpty())  fQueryStr += " AND $queuesQueryStr"
        Logd(TAG, "sortFeeds() called $feedsFilter $fQueryStr")
        val feedList_ = getFeedList(fQueryStr).toMutableList()
        val feedOrder = feedOrderBy
        val dir = 1 - 2*feedOrderDir    // get from 0, 1 to 1, -1
        val comparator: Comparator<Feed> = when (feedOrder) {
            FeedSortOrder.UNPLAYED_NEW_OLD.index -> {
                val queryString = "feedId == $0 AND (playState == ${Episode.PlayState.NEW.code} OR playState == ${Episode.PlayState.UNPLAYED.code})"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = "$c unplayed"
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.ALPHABETIC_A_Z.index -> {
                for (f in feedList_) f.sortInfo = ""
                Comparator { lhs: Feed, rhs: Feed ->
                    val t1 = lhs.title
                    val t2 = rhs.title
                    when {
                        t1 == null -> dir
                        t2 == null -> -dir
                        else -> t1.compareTo(t2, ignoreCase = true) * dir
                    }
                }
            }
            FeedSortOrder.MOST_PLAYED.index -> {
                val queryString = "feedId == $0 AND playState == ${Episode.PlayState.PLAYED.code}"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = "$c played"
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.LAST_UPDATED_NEW_OLD.index -> {
                val queryString = "feedId == $0 SORT(pubDate DESC)"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
                    counterMap[f.id] = d
//                    val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                    f.sortInfo = "Updated: " + formatAbbrev(requireContext(), Date(d))
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.LAST_DOWNLOAD_NEW_OLD.index -> {
                val queryString = "feedId == $0 SORT(media.downloadTime DESC)"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.media?.downloadTime ?: 0L
                    counterMap[f.id] = d
//                    val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                    f.sortInfo = "Downloaded: " + formatAbbrev(requireContext(), Date(d))
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.LAST_UPDATED_UNPLAYED_NEW_OLD.index -> {
                val queryString =
                    "feedId == $0 AND (playState == ${Episode.PlayState.NEW.code} OR playState == ${Episode.PlayState.UNPLAYED.code}) SORT(pubDate DESC)"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
                    counterMap[f.id] = d
//                    val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                    f.sortInfo = "Unplayed: " + formatAbbrev(requireContext(), Date(d))
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.MOST_DOWNLOADED.index -> {
                val queryString = "feedId == $0 AND media.downloaded == true"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = "$c downloaded"
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.MOST_DOWNLOADED_UNPLAYED.index -> {
                val queryString =
                    "feedId == $0 AND (playState == ${Episode.PlayState.NEW.code} OR playState == ${Episode.PlayState.UNPLAYED.code}) AND media.downloaded == true"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = "$c downloaded unplayed"
                }
                comparator(counterMap, dir)
            }
            //            doing FEED_ORDER_NEW
            else -> {
                val queryString = "feedId == $0 AND playState == ${Episode.PlayState.NEW.code}"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feedList_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = "$c new"
                }
                comparator(counterMap, dir)
            }
        }
        synchronized(feedList_) { feedList = feedList_.sortedWith(comparator).toMutableList() }
        feedSorted++
    }

    private fun comparator(counterMap: Map<Long, Long>, dir: Int): Comparator<Feed> {
        return Comparator { lhs: Feed, rhs: Feed ->
            val counterLhs = counterMap[lhs.id]?:0
            val counterRhs = counterMap[rhs.id]?:0
            when {
                // reverse natural order: podcast with most unplayed episodes first
                counterLhs > counterRhs -> -dir
                counterLhs == counterRhs -> (lhs.title?.compareTo(rhs.title!!, ignoreCase = true) ?: -1) * dir
                else -> dir
            }
        }
    }

    @Composable
    fun InforBar() {
        Row(Modifier.padding(start = 20.dp, end = 20.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Icon(painter = painterResource(R.drawable.ic_info), contentDescription = "info", tint = textColor)
            Spacer(Modifier.weight(1f))
            Text(txtvInformation, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable {
                if (feedsFilter.isNotEmpty()) {
                    val filter = FeedFilter(feedsFilter)
                    val dialog = FeedFilterDialog.newInstance(filter)
                    dialog.show(childFragmentManager, null)
                }
            } )
            Spacer(Modifier.weight(1f))
            Text(feedCount, color = textColor)
        }
    }

    @kotlin.OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun LazyList() {
        var selectedSize by remember { mutableStateOf(0) }
        val selected = remember { mutableStateListOf<Feed>() }
        var longPressIndex by remember { mutableIntStateOf(-1) }
        var refreshing by remember { mutableStateOf(false)}

        var showRemoveFeedDialog by remember { mutableStateOf(false) }
        if (showRemoveFeedDialog) RemoveFeedDialog(selected, onDismissRequest = {showRemoveFeedDialog = false}, null)

        fun saveFeedPreferences(preferencesConsumer: Consumer<FeedPreferences>) {
            for (feed in selected) {
                if (feed.preferences == null) continue
                runOnIOScope {
                    upsert(feed) {
                        preferencesConsumer.accept(it.preferences!!)
                    }
                }
            }
            val numItems = selected.size
            (activity as MainActivity).showSnackbarAbovePlayer(activity!!.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems), Snackbar.LENGTH_LONG)
        }

        @Composable
        fun AutoDeleteHandlerDialog(onDismissRequest: () -> Unit) {
            val (selectedOption, _) = remember { mutableStateOf("") }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            FeedAutoDeleteOptions.forEach { text ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    .selectable(selected = (text == selectedOption),
                                        onClick = {
                                            if (text != selectedOption) {
                                                val autoDeleteAction: AutoDeleteAction = AutoDeleteAction.fromTag(text)
                                                saveFeedPreferences { it: FeedPreferences ->
                                                    it.autoDeleteAction = autoDeleteAction
                                                }
                                                onDismissRequest()
                                            }
                                        }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = (text == selectedOption), onClick = { })
                                    Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetAssociateQueueDialog(onDismissRequest: () -> Unit) {
            var selectedOption by remember {mutableStateOf("")}
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        queueSettingOptions.forEach { option ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = option == selectedOption,
                                    onCheckedChange = { isChecked ->
                                        selectedOption = option
                                        if (isChecked) Logd(TAG, "$option is checked")
                                        when (selectedOption) {
                                            "Default" -> {
                                                saveFeedPreferences { it: FeedPreferences -> it.queueId = 0L }
                                                onDismissRequest()
                                            }
                                            "Active" -> {
                                                saveFeedPreferences { it: FeedPreferences -> it.queueId = -1L }
                                                onDismissRequest()
                                            }
                                            "None" -> {
                                                saveFeedPreferences { it: FeedPreferences -> it.queueId = -2L }
                                                onDismissRequest()
                                            }
                                            "Custom" -> {}
                                        }
                                    }
                                )
                                Text(option)
                            }
                        }
                        if (selectedOption == "Custom") {
                            val queues = realm.query(PlayQueue::class).find()
                            Spinner(items = queues.map { it.name }, selectedItem = "Default") { name ->
                                Logd(TAG, "Queue selected: $name")
                                val q = queues.firstOrNull { it.name == name }
                                if (q != null) {
                                    saveFeedPreferences { it: FeedPreferences -> it.queueId = q.id }
                                    onDismissRequest()
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun SetKeepUpdateDialog(onDismissRequest: () -> Unit) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "")
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.keep_updated), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            var checked by remember { mutableStateOf(false) }
                            Switch(checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    saveFeedPreferences { pref: FeedPreferences ->
                                        pref.keepUpdated = checked
                                    }
                                }
                            )
                        }
                        Text(text = stringResource(R.string.keep_updated_summary), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        @Composable
        fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
            Dialog(onDismissRequest = onDismissRequest) {
                Surface(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (rating in Rating.entries) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                                for (item in selected) Feeds.setRating(item, rating.code)
                                onDismissRequest()
                            }) {
                                Icon(imageVector = ImageVector.vectorResource(id = rating.res), "")
                                Text(rating.name, Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }

        var showAutoDeleteHandlerDialog by remember { mutableStateOf(false) }
        if (showAutoDeleteHandlerDialog) AutoDeleteHandlerDialog {showAutoDeleteHandlerDialog = false}

        var showAssociateDialog by remember { mutableStateOf(false) }
        if (showAssociateDialog) SetAssociateQueueDialog {showAssociateDialog = false}

        var showKeepUpdateDialog by remember { mutableStateOf(false) }
        if (showKeepUpdateDialog) SetKeepUpdateDialog {showKeepUpdateDialog = false}

        @Composable
        fun EpisodeSpeedDial(activity: MainActivity, selected: SnapshotStateList<Feed>, modifier: Modifier = Modifier) {
            val TAG = "EpisodeSpeedDial ${selected.size}"
            var isExpanded by remember { mutableStateOf(false) }
            val options = listOf<@Composable () -> Unit>(
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        showRemoveFeedDialog = true
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_delete: ${selected.size}")
//                    RemoveFeedDialog.show(activity, selected)
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "")
                    Text(stringResource(id = R.string.remove_feed_label))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        showKeepUpdateDialog = true
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_refresh: ${selected.size}")
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh), "")
                    Text(stringResource(id = R.string.keep_updated))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_download: ${selected.size}")
                        val preferenceSwitchDialog = PreferenceSwitchDialog(activity, activity.getString(R.string.auto_download_settings_label), activity.getString(R.string.auto_download_label))
                        preferenceSwitchDialog.setOnPreferenceChangedListener(@UnstableApi object: PreferenceSwitchDialog.OnPreferenceChangedListener {
                            override fun preferenceChanged(enabled: Boolean) {
                                saveFeedPreferences { it: FeedPreferences -> it.autoDownload = enabled }
                            }
                        })
                        preferenceSwitchDialog.openDialog()
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "")
                    Text(stringResource(id = R.string.auto_download_label))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        showAutoDeleteHandlerDialog = true
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_delete_auto: ${selected.size}")
//                        autoDeleteEpisodesPrefHandler()
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete_auto), "")
                    Text(stringResource(id = R.string.auto_delete_label))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_playback_speed: ${selected.size}")
                        val vBinding = PlaybackSpeedFeedSettingDialogBinding.inflate(activity.layoutInflater)
                        vBinding.seekBar.setProgressChangedListener { speed: Float? ->
                            vBinding.currentSpeedLabel.text = String.format(Locale.getDefault(), "%.2fx", speed)
                        }
                        vBinding.useGlobalCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                            vBinding.seekBar.isEnabled = !isChecked
                            vBinding.seekBar.alpha = if (isChecked) 0.4f else 1f
                            vBinding.currentSpeedLabel.alpha = if (isChecked) 0.4f else 1f
                        }
                        vBinding.seekBar.updateSpeed(1.0f)
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.playback_speed)
                            .setView(vBinding.root)
                            .setPositiveButton("OK") { _: DialogInterface?, _: Int ->
                                val newSpeed = if (vBinding.useGlobalCheckbox.isChecked) FeedPreferences.SPEED_USE_GLOBAL
                                else vBinding.seekBar.currentSpeed
                                saveFeedPreferences { it: FeedPreferences ->
                                    it.playSpeed = newSpeed
                                }
                            }
                            .setNegativeButton(R.string.cancel_label, null)
                            .show()
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "")
                    Text(stringResource(id = R.string.playback_speed))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_tag: ${selected.size}")
                        TagSettingsDialog.newInstance(selected).show(activity.supportFragmentManager, Companion.TAG)
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_tag), "")
                    Text(stringResource(id = R.string.edit_tags))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        showAssociateDialog = true
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "ic_playlist_play: ${selected.size}")
//                        associatedQueuePrefHandler()
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
                    Text(stringResource(id = R.string.pref_feed_associated_queue))
                } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        selectMode = false
                        Logd(TAG, "ic_star: ${selected.size}")
                        showChooseRatingDialog = true
                        isExpanded = false
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                    Text(stringResource(id = R.string.set_rating_label)) } },
                { Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "baseline_import_export_24: ${selected.size}")
                        val exportType = Export.OPML_SELECTED
                        val title = String.format(exportType.outputNameTemplate, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                        val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(exportType.contentType)
                            .putExtra(Intent.EXTRA_TITLE, title)
                        try {
                            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                                if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
                                val uri = result.data!!.data
                                exportOPML(uri, selected)
                            }.launch(intentPickAction)
                            return@clickable
                        } catch (e: ActivityNotFoundException) { Log.e(Companion.TAG, "No activity found. Should never happen...") }
                        // if on SDK lower than API 21 or the implicit intent failed, fallback to the legacy export process
                        exportOPML(null, selected)
                    }, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_import_export_24), "")
                    Text(stringResource(id = R.string.opml_export_label))
                } },
            )
            val scrollState = rememberScrollState()
            Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
                if (isExpanded) options.forEachIndexed { _, button ->
                    FloatingActionButton(modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(40.dp), containerColor = Color.LightGray, onClick = {}) { button() }
                }
                FloatingActionButton(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.secondary,
                    onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
            }
        }

        PullToRefreshBox(modifier = Modifier.fillMaxWidth(), isRefreshing = refreshing, indicator = {}, onRefresh = {
//            coroutineScope.launch {
                refreshing = true
                if (swipeToRefresh) FeedUpdateManager.runOnceOrAsk(requireContext())
                refreshing = false
//            }
        }) {
            if (if (useGrid == null) useGridLayout else useGrid!!) {
                val lazyGridState = rememberLazyGridState()
                LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)
                )  {
                    items(feedListFiltered.size, key = {index -> feedListFiltered[index].id}) { index ->
                        val feed by remember { mutableStateOf(feedListFiltered[index]) }
                        var isSelected by remember { mutableStateOf(false) }
                        LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                            isSelected = selectMode && feed in selected
                        }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) selected.add(feed)
                            else selected.remove(feed)
                        }
                        Column(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (selectMode) toggleSelected()
                                    else (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id))
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    selectMode = !selectMode
                                    isSelected = selectMode
                                    selected.clear()
                                    if (selectMode) {
                                        selected.add(feed)
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                }
                                Logd(TAG, "long clicked: ${feed.title}")
                            })) {
                            val textColor = MaterialTheme.colorScheme.onSurface
                            ConstraintLayout(Modifier.fillMaxSize()) {
                                val (coverImage, episodeCount, rating, error) = createRefs()
                                AsyncImage(model = feed.imageUrl, contentDescription = "coverImage",
                                    placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .constrainAs(coverImage) {
                                            top.linkTo(parent.top)
                                            bottom.linkTo(parent.bottom)
                                            start.linkTo(parent.start)
                                        })
                                Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()), color = Color.Green,
                                    modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                                        end.linkTo(parent.end)
                                        top.linkTo(coverImage.top)
                                    })
                                if (feed.rating != Rating.UNRATED.code)
                                    Icon(painter = painterResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                        start.linkTo(parent.start)
                                        centerVerticallyTo(coverImage)
                                    })
//                                TODO: need to use state
                                if (feed.lastUpdateFailed) Icon(painter = painterResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error",
                                    modifier = Modifier.background(Color.Gray).constrainAs(error) {
                                        end.linkTo(parent.end)
                                        bottom.linkTo(coverImage.bottom)
                                    })
                            }
                            Text(feed.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                val lazyListState = rememberLazyListState()
                LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(feedListFiltered, key = {index, feed -> feed.id}) { index, feed ->
                        var isSelected by remember { mutableStateOf(false) }
                        LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                            isSelected = selectMode && feed in selected
                        }
                        fun toggleSelected() {
                            isSelected = !isSelected
                            if (isSelected) selected.add(feed)
                            else selected.remove(feed)
                            Logd(TAG, "toggleSelected: selected: ${selected.size}")
                        }
                        Row(Modifier.background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                            ConstraintLayout {
                                val (coverImage, rating) = createRefs()
                                AsyncImage(model = feed.imageUrl,
                                    contentDescription = "imgvCover",
                                    placeholder = painterResource(R.mipmap.ic_launcher),
                                    error = painterResource(R.mipmap.ic_launcher),
                                    modifier = Modifier.width(80.dp).height(80.dp)
                                        .constrainAs(coverImage) {
                                            top.linkTo(parent.top)
                                            bottom.linkTo(parent.bottom)
                                            start.linkTo(parent.start)
                                        }.clickable(onClick = {
                                            Logd(TAG, "icon clicked!")
                                            if (!feed.isBuilding) {
                                                if (selectMode) toggleSelected()
                                                else (activity as MainActivity).loadChildFragment(FeedInfoFragment.newInstance(feed))
                                            }
                                        })
                                )
                                if (feed.rating != Rating.UNRATED.code)
                                    Icon(painter = painterResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary,
                                        contentDescription = "rating",
                                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                            start.linkTo(parent.start)
                                            centerVerticallyTo(coverImage)
                                        })
                            }
                            val textColor = MaterialTheme.colorScheme.onSurface
                            Column(Modifier.weight(1f).padding(start = 10.dp).combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${feed.title}")
                                if (!feed.isBuilding) {
                                    if (selectMode) toggleSelected()
                                    else (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id))
                                }
                            }, onLongClick = {
                                if (!feed.isBuilding) {
                                    selectMode = !selectMode
                                    isSelected = selectMode
                                    selected.clear()
                                    if (selectMode) {
                                        selected.add(feed)
                                        longPressIndex = index
                                    } else {
                                        selectedSize = 0
                                        longPressIndex = -1
                                    }
                                }
                                Logd(TAG, "long clicked: ${feed.title}")
                            })) {
                                Text(feed.title ?: "No title", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(feed.author ?: "No author", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.padding(top = 5.dp)) {
                                    Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " episodes",
                                        color = textColor, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    var feedSortInfo by remember { mutableStateOf(feed.sortInfo) }
                                    LaunchedEffect(feedSorted) { feedSortInfo = feed.sortInfo }
                                    Text(feedSortInfo, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            //                                TODO: need to use state
                            if (feed.lastUpdateFailed) Icon(painter = painterResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error")
                        }
                    }
                }
            }
            if (selectMode) {
                Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(Color.LightGray),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(R.drawable.baseline_arrow_upward_24), tint = Color.Black, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                            .clickable(onClick = {
                                selected.clear()
                                for (i in 0..longPressIndex) {
                                    selected.add(feedListFiltered[i])
                                }
                                selectedSize = selected.size
                                Logd(TAG, "selectedIds: ${selected.size}")
                            }))
                    Icon(painter = painterResource(R.drawable.baseline_arrow_downward_24), tint = Color.Black, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                            .clickable(onClick = {
                                selected.clear()
                                for (i in longPressIndex..<feedListFiltered.size) {
                                    selected.add(feedListFiltered[i])
                                }
                                selectedSize = selected.size
                                Logd(TAG, "selectedIds: ${selected.size}")
                            }))
                    var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                    Icon(painter = painterResource(selectAllRes), tint = Color.Black, contentDescription = null,
                        modifier = Modifier.width(35.dp).height(35.dp)
                            .clickable(onClick = {
                                if (selectedSize != feedListFiltered.size) {
                                    selected.clear()
                                    selected.addAll(feedListFiltered)
                                    selectAllRes = R.drawable.ic_select_none
                                } else {
                                    selected.clear()
                                    longPressIndex = -1
                                    selectAllRes = R.drawable.ic_select_all
                                }
                                selectedSize = selected.size
                                Logd(TAG, "selectedIds: ${selected.size}")
                            }))
                }
                EpisodeSpeedDial(activity as MainActivity, selected.toMutableStateList(), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
            }
            FloatingActionButton(shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp),
                onClick = {
                    if (activity is MainActivity) (activity as MainActivity).loadChildFragment(OnlineSearchFragment())
                }) { Icon(Icons.Outlined.AddCircle, "Add", modifier = Modifier.size(60.dp)) }
        }
    }

    private fun exportOPML(uri: Uri?, selectedItems: List<Feed>) {
        try {
            runBlocking {
                Logd(TAG, "selectedFeeds: ${selectedItems.size}")
                if (uri == null) ExportWorker(OpmlWriter(), requireContext()).exportFile(selectedItems)
                else {
                    val worker = DocumentFileExportWorker(OpmlWriter(), requireContext(), uri)
                    worker.exportFile(selectedItems)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "exportOPML error: ${e.message}") }
    }

    class PreferenceSwitchDialog(private var context: Context, private val title: String, private val text: String) {
        private var onPreferenceChangedListener: OnPreferenceChangedListener? = null
        interface OnPreferenceChangedListener {
            /**
             * Notified when user confirms preference
             * @param enabled The preference
             */
            fun preferenceChanged(enabled: Boolean)
        }
        fun openDialog() {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(title)

            val inflater = LayoutInflater.from(this.context)
            val layout = inflater.inflate(R.layout.dialog_switch_preference, null, false)
            val binding = DialogSwitchPreferenceBinding.bind(layout)
            val switchButton = binding.dialogSwitch
            switchButton.text = text
            builder.setView(layout)

            builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
                onPreferenceChangedListener?.preferenceChanged(switchButton.isChecked)
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.create().show()
        }
        fun setOnPreferenceChangedListener(onPreferenceChangedListener: OnPreferenceChangedListener?) {
            this.onPreferenceChangedListener = onPreferenceChangedListener
        }
    }

    class FeedFilterDialog : BottomSheetDialogFragment() {
        private lateinit var rows: LinearLayout
        private var _binding: FilterDialogBinding? = null
        private val binding get() = _binding!!

        var filter: FeedFilter? = null
        private val buttonMap: MutableMap<String, Button> = mutableMapOf()

        private val newFilterValues: Set<String>
            get() {
                val newFilterValues: MutableSet<String> = HashSet()
                for (i in 0 until rows.childCount) {
                    if (rows.getChildAt(i) !is MaterialButtonToggleGroup) continue
                    val group = rows.getChildAt(i) as MaterialButtonToggleGroup
                    if (group.checkedButtonId == View.NO_ID) continue
                    val tag = group.findViewById<View>(group.checkedButtonId).tag as? String ?: continue
                    newFilterValues.add(tag)
                }
                return newFilterValues
            }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val layout = inflater.inflate(R.layout.filter_dialog, container, false)
            _binding = FilterDialogBinding.bind(layout)
            rows = binding.filterRows
            Logd("FeedFilterDialog", "fragment onCreateView")

            //add filter rows
            for (item in FeedFilterGroup.entries) {
//            Logd("EpisodeFilterDialog", "FeedItemFilterGroup: ${item.values[0].filterId} ${item.values[1].filterId}")
                val rBinding = FilterDialogRowBinding.inflate(inflater)
//            rowBinding.root.addOnButtonCheckedListener { _: MaterialButtonToggleGroup?, _: Int, _: Boolean ->
//                onFilterChanged(newFilterValues)
//            }
                rBinding.filterButton1.setOnClickListener { onFilterChanged(newFilterValues) }
                rBinding.filterButton2.setOnClickListener { onFilterChanged(newFilterValues) }

                rBinding.filterButton1.setText(item.values[0].displayName)
                rBinding.filterButton1.tag = item.values[0].filterId
                buttonMap[item.values[0].filterId] = rBinding.filterButton1
                rBinding.filterButton2.setText(item.values[1].displayName)
                rBinding.filterButton2.tag = item.values[1].filterId
                buttonMap[item.values[1].filterId] = rBinding.filterButton2
                rBinding.filterButton1.maxLines = 3
                rBinding.filterButton1.isSingleLine = false
                rBinding.filterButton2.maxLines = 3
                rBinding.filterButton2.isSingleLine = false
                rows.addView(rBinding.root, rows.childCount - 1)
            }

            binding.confirmFiltermenu.setOnClickListener { dismiss() }
            binding.resetFiltermenu.setOnClickListener {
                onFilterChanged(emptySet())
                for (i in 0 until rows.childCount) {
                    if (rows.getChildAt(i) is MaterialButtonToggleGroup) (rows.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
                }
            }

            if (filter != null) {
                for (filterId in filter!!.values) {
                    if (filterId.isNotEmpty()) {
                        val button = buttonMap[filterId]
                        if (button != null) (button.parent as MaterialButtonToggleGroup).check(button.id)
                    }
                }
            }
            return layout
        }

        override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            _binding = null
            super.onDestroyView()
        }

        private fun onFilterChanged(newFilterValues: Set<String>) {
            feedsFilter = StringUtils.join(newFilterValues, ",")
            Logd(TAG, "onFilterChanged: $feedsFilter")
            EventFlow.postEvent(FlowEvent.FeedsFilterEvent(newFilterValues))
        }

        enum class FeedFilterGroup(vararg values: ItemProperties) {
            KEEP_UPDATED(ItemProperties(R.string.keep_updated, FeedFilter.States.keepUpdated.name), ItemProperties(R.string.not_keep_updated, FeedFilter.States.not_keepUpdated.name)),
            PLAY_SPEED(ItemProperties(R.string.global_speed, FeedFilter.States.global_playSpeed.name), ItemProperties(R.string.custom_speed, FeedFilter.States.custom_playSpeed.name)),
            SKIPS(ItemProperties(R.string.has_skips, FeedFilter.States.has_skips.name), ItemProperties(R.string.no_skips, FeedFilter.States.no_skips.name)),
            AUTO_DELETE(ItemProperties(R.string.always_auto_delete, FeedFilter.States.always_auto_delete.name), ItemProperties(R.string.never_auto_delete, FeedFilter.States.never_auto_delete.name)),
            AUTO_DOWNLOAD(ItemProperties(R.string.auto_download, FeedFilter.States.autoDownload.name), ItemProperties(R.string.not_auto_download, FeedFilter.States.not_autoDownload.name));

            @JvmField
            val values: Array<ItemProperties> = arrayOf(*values)

            class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
        }

        companion object {
            fun newInstance(filter: FeedFilter?): FeedFilterDialog {
                val dialog = FeedFilterDialog()
                dialog.filter = filter
                return dialog
            }
        }
    }

    companion object {
        val TAG = SubscriptionsFragment::class.simpleName ?: "Anonymous"

        private const val KEY_UP_ARROW = "up_arrow"
        private const val ARGUMENT_FOLDER = "folder"

        private var prevFeedUpdatingEvent: FlowEvent.FeedUpdatingEvent? = null

        val feedOrderBy: Int
            get() {
                val value = appPrefs.getString(UserPreferences.Prefs.prefDrawerFeedOrder.name, "" + FeedSortOrder.UNPLAYED_NEW_OLD.index)
                return value!!.toInt()
            }

        val feedOrderDir: Int
            get() {
                val value = appPrefs.getInt(UserPreferences.Prefs.prefDrawerFeedOrderDir.name, 0)
                return value
            }

        var feedsFilter: String
            get() = appPrefs.getString(UserPreferences.Prefs.prefFeedFilter.name, "")?:""
            set(filter) {
                appPrefs.edit().putString(UserPreferences.Prefs.prefFeedFilter.name, filter).apply()
            }

        fun newInstance(folderTitle: String?): SubscriptionsFragment {
            val fragment = SubscriptionsFragment()
            val args = Bundle()
            args.putString(ARGUMENT_FOLDER, folderTitle)
            fragment.arguments = args
            return fragment
        }
    }
}
