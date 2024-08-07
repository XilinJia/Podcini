package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.fragments.ImportExportPreferencesFragment.*
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.dialog.FeedFilterDialog
import ac.mdiq.podcini.ui.dialog.FeedSortDialog
import ac.mdiq.podcini.ui.dialog.RemoveFeedDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.ui.fragment.FeedSettingsFragment.Companion.queueSettingOptions
import ac.mdiq.podcini.ui.utils.CoverLoader
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.util.DateFormatter.formatAbbrev
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for displaying feed subscriptions
 */
class SubscriptionsFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

    private val chooseOpmlExportPathLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val uri = result.data!!.data
        multiSelectHandler?.exportOPML(uri)
    }

    private var multiSelectHandler: FeedMultiSelectActionHandler? = null
    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SubscriptionsAdapter<*>
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var toolbar: MaterialToolbar
    private lateinit var speedDialView: SpeedDialView

    private lateinit var catAdapter: ArrayAdapter<String>

    private var tagFilterIndex = 1
//    TODO: currently not used
    private var displayedFolder: String = ""
    private var displayUpArrow = false

    private var feedList: MutableList<Feed> = mutableListOf()
    private var feedListFiltered: List<Feed> = mutableListOf()
    private val tags: MutableList<String> = mutableListOf()

    private var useGrid: Boolean? = null
    private val useGridLayout: Boolean
        get() = appPrefs.getBoolean(UserPreferences.Prefs.prefFeedGridLayout.name, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
            recyclerView.scrollToPosition(5)
            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.subscriptions)

        if (arguments != null) {
            displayedFolder = requireArguments().getString(ARGUMENT_FOLDER, null)
            toolbar.title = displayedFolder
        }

        recyclerView = binding.subscriptionsGrid
        recyclerView.addItemDecoration(GridDividerItemDecorator())
        registerForContextMenu(recyclerView)
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        initAdapter()
        setupEmptyView()

        resetTags()

        catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tags)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categorySpinner.setAdapter(catAdapter)
        binding.categorySpinner.setSelection(catAdapter.getPosition("All"))
        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                tagFilterIndex = position
//                filterOnTag()
                loadSubscriptions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val searchBox = binding.searchBox
        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val text = searchBox.text.toString().lowercase(Locale.getDefault())
                val resultList = feedListFiltered.filter {
                    it.title?.lowercase(Locale.getDefault())?.contains(text)?:false || it.author?.lowercase(Locale.getDefault())?.contains(text)?:false
                }
                adapter.setItems(resultList)
                true
            } else false
        }

        binding.progressBar.visibility = View.VISIBLE

        val subscriptionAddButton: FloatingActionButton = binding.subscriptionsAdd
        subscriptionAddButton.setOnClickListener {
            if (activity is MainActivity) (activity as MainActivity).loadChildFragment(AddFeedFragment())
        }

        binding.count.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
        binding.swipeRefresh.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        binding.swipeRefresh.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext())
        }
        val speedDialBinding = MultiSelectSpeedDialBinding.bind(binding.root)
        speedDialView = speedDialBinding.fabSD
        speedDialView.overlayLayout = speedDialBinding.fabSDOverlay
        speedDialView.inflate(R.menu.feed_action_speeddial)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }
            override fun onToggleChanged(isOpen: Boolean) {}
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            multiSelectHandler = FeedMultiSelectActionHandler(activity as MainActivity, adapter.selectedItems.filterIsInstance<Feed>())
            multiSelectHandler?.handleAction(actionItem.id)
            true
        }
        loadSubscriptions()
        return binding.root
    }

    private fun initAdapter() {
        if (useGrid != useGridLayout) {
            useGrid = useGridLayout
            var spanCount = 1
            if (useGrid!!) {
                adapter = GridAdapter()
                spanCount = 3
            } else adapter = ListAdapter()
            recyclerView.layoutManager = GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, false)
            adapter.setOnSelectModeListener(this)
            recyclerView.adapter = adapter
            adapter.setItems(feedListFiltered)
        }
    }

    override fun onStart() {
        Logd(TAG, "onStart()")
        super.onStart()
        initAdapter()
        procFlowEvents()
    }

    override fun onStop() {
        Logd(TAG, "onStop()")
        super.onStop()
        adapter.endSelectMode()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        feedList = mutableListOf()
        feedListFiltered = mutableListOf()
        adapter.clearData()
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

    private fun filterOnTag() {
        feedListFiltered = feedList
        binding.count.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
        adapter.setItems(feedListFiltered)
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
//                    is FlowEvent.FeedPrefsChangeEvent -> onFeedPrefsChangeEvent(event)
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
                        binding.swipeRefresh.isRefreshing = event.isRunning
                        if (!event.isRunning && event.id != prevFeedUpdatingEvent?.id) loadSubscriptions()
                        prevFeedUpdatingEvent = event
                    }
                    else -> {}
                }
            }
        }
    }

//    private fun onFeedPrefsChangeEvent(event: FlowEvent.FeedPrefsChangeEvent) {
//        val feed = getFeed(event.feed.id)
//    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.subscriptions_filter -> FeedFilterDialog.newInstance(FeedFilter(feedsFilter)).show(childFragmentManager, null)
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            R.id.subscriptions_sort -> FeedSortDialog().show(childFragmentManager, "FeedSortDialog")
            R.id.refresh_item -> FeedUpdateManager.runOnceOrAsk(requireContext())
            else -> return false
        }
        return true
    }

    private fun setupEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_subscriptions)
        emptyView.setTitle(R.string.no_subscriptions_head_label)
        emptyView.setMessage(R.string.no_subscriptions_label)
        emptyView.attachToRecyclerView(recyclerView)
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
                        if (feedListFiltered.size > feedList.size) adapter.endSelectMode()
                        filterOnTag()
                        binding.progressBar.visibility = View.GONE
                        adapter.setItems(feedListFiltered)
                        binding.count.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
                        if (feedsFilter.isNotEmpty()) {
                            val filter = FeedFilter(feedsFilter)
                            binding.txtvInformation.text = ("{gmo-info} " + getString(R.string.filtered_label))
                            binding.txtvInformation.setOnClickListener {
                                val dialog = FeedFilterDialog.newInstance(filter)
                                dialog.show(childFragmentManager, null)
                            }
                            binding.txtvInformation.visibility = View.VISIBLE
                        } else binding.txtvInformation.visibility = View.GONE
                        emptyView.updateVisibility()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                } finally {
                    loadItemsRunning = false
                }
            }
        }
    }

    private fun filterAndSort() {
        val tagsQueryStr = queryStringOfTags()
        val fQueryStr = if (tagsQueryStr.isEmpty()) FeedFilter(feedsFilter).queryString() else FeedFilter(feedsFilter).queryString() + " AND " + tagsQueryStr
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

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        adapter.setItems(feedListFiltered)
    }

    override fun onStartSelectMode() {
        speedDialView.visibility = View.VISIBLE
        val feedsOnly: MutableList<Feed> = ArrayList<Feed>(feedListFiltered)
//        feedsOnly.addAll(feedListFiltered)
        adapter.setItems(feedsOnly)
    }

    @UnstableApi
    private inner class FeedMultiSelectActionHandler(private val activity: MainActivity, private val selectedItems: List<Feed>) {
        fun handleAction(id: Int) {
            when (id) {
                R.id.remove_feed -> RemoveFeedDialog.show(activity, selectedItems)
                R.id.keep_updated -> keepUpdatedPrefHandler()
                R.id.autodownload -> autoDownloadPrefHandler()
                R.id.autoDeleteDownload -> autoDeleteEpisodesPrefHandler()
                R.id.playback_speed -> playbackSpeedPrefHandler()
                R.id.export_opml -> openExportPathPicker()
                R.id.associate_queue -> associatedQueuePrefHandler()
                R.id.edit_tags -> TagSettingsDialog.newInstance(selectedItems).show(activity.supportFragmentManager, TAG)
                else -> Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=$id")
            }
        }
        private fun openExportPathPicker() {
            val exportType = Export.OPML_SELECTED
            val title = String.format(exportType.outputNameTemplate, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(exportType.contentType)
                .putExtra(Intent.EXTRA_TITLE, title)
            try {
                chooseOpmlExportPathLauncher.launch(intentPickAction)
                return
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No activity found. Should never happen...")
            }
            // if on SDK lower than API 21 or the implicit intent failed, fallback to the legacy export process
            exportOPML(null)
        }
        fun exportOPML(uri: Uri?) {
            try {
                runBlocking {
                    Logd(TAG, "selectedFeeds: ${selectedItems.size}")
                    if (uri == null) ExportWorker(OpmlWriter(), requireContext()).exportFile(selectedItems)
                    else {
                        val worker = DocumentFileExportWorker(OpmlWriter(), requireContext(), uri)
                        worker.exportFile(selectedItems)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "exportOPML error: ${e.message}")
            }
        }
        private fun autoDownloadPrefHandler() {
            val preferenceSwitchDialog = PreferenceSwitchDialog(activity, activity.getString(R.string.auto_download_settings_label), activity.getString(R.string.auto_download_label))
            preferenceSwitchDialog.setOnPreferenceChangedListener(@UnstableApi object: PreferenceSwitchDialog.OnPreferenceChangedListener {
                override fun preferenceChanged(enabled: Boolean) {
                saveFeedPreferences { it: FeedPreferences -> it.autoDownload = enabled }
                }
            })
            preferenceSwitchDialog.openDialog()
        }
        @UnstableApi private fun playbackSpeedPrefHandler() {
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
        }
        private fun autoDeleteEpisodesPrefHandler() {
            val composeView = ComposeView(activity).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(activity) {
                        AutoDeleteDialog(showDialog.value, onDismissRequest = { showDialog.value = false })
                    }
                }
            }
            (activity.window.decorView as ViewGroup).addView(composeView)
        }
        private fun associatedQueuePrefHandler() {
            val composeView = ComposeView(activity).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(activity) {
                        SetAssociatedQueue(showDialog.value, onDismissRequest = { showDialog.value = false })
                    }
                }
            }
            (activity.window.decorView as ViewGroup).addView(composeView)
        }
        private fun keepUpdatedPrefHandler() {
            val composeView = ComposeView(activity).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(activity) {
                        KeepUpdatedDialog(showDialog.value, onDismissRequest = { showDialog.value = false })
                    }
                }
            }
            (activity.window.decorView as ViewGroup).addView(composeView)
        }
        @UnstableApi private fun saveFeedPreferences(preferencesConsumer: Consumer<FeedPreferences>) {
            for (feed in selectedItems) {
                if (feed.preferences == null) continue
                runOnIOScope {
                    upsert(feed) {
                        preferencesConsumer.accept(it.preferences!!)
                    }
                }
            }
            val numItems = selectedItems.size
            activity.showSnackbarAbovePlayer(activity.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems), Snackbar.LENGTH_LONG)
        }
        @Composable
        fun KeepUpdatedDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
            if (showDialog) {
                Dialog(onDismissRequest = { onDismissRequest() }) {
                    Card(
                        modifier = Modifier
                            .wrapContentSize(align = Alignment.Center)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "")
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(
                                    text = stringResource(R.string.keep_updated),
                                    style = MaterialTheme.typography.h6
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                var checked by remember { mutableStateOf(false) }
                                Switch(
                                    checked = checked,
                                    onCheckedChange = {
                                        checked = it
                                        saveFeedPreferences { pref: FeedPreferences ->
                                            pref.keepUpdated = checked
                                        }
                                    }
                                )
                            }
                            Text(
                                text = stringResource(R.string.keep_updated_summary),
                                style = MaterialTheme.typography.body2
                            )
                        }
                    }
                }
            }
        }
        @Composable
        fun AutoDeleteDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
            if (showDialog) {
                val (selectedOption, onOptionSelected) = remember { mutableStateOf("") }
                Dialog(onDismissRequest = { onDismissRequest() }) {
                    Card(
                        modifier = Modifier
                            .wrapContentSize(align = Alignment.Center)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column {
                                FeedAutoDeleteOptions.forEach { text ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = (text == selectedOption),
                                                onClick = {
                                                    if (text != selectedOption) {
                                                        val autoDeleteAction: AutoDeleteAction = AutoDeleteAction.fromTag(text)
                                                        saveFeedPreferences { it: FeedPreferences ->
                                                            it.autoDeleteAction = autoDeleteAction
                                                        }
                                                        onDismissRequest()
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = (text == selectedOption),
                                            onClick = { }
                                        )
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.body1.merge(),
                                            modifier = Modifier.padding(start = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        @Composable
        private fun SetAssociatedQueue(showDialog: Boolean, onDismissRequest: () -> Unit) {
            var selected by remember {mutableStateOf("")}
            if (showDialog) {
                Dialog(onDismissRequest = { onDismissRequest() }) {
                    Card(modifier = Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            queueSettingOptions.forEach { option ->
                                Row(modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = option == selected,
                                        onCheckedChange = { isChecked ->
                                            selected = option
                                            if (isChecked) Logd(TAG, "$option is checked")
                                            when (selected) {
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
                            if (selected == "Custom") {
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
        }
    }

    @OptIn(UnstableApi::class)
    private abstract inner class SubscriptionsAdapter<T : RecyclerView.ViewHolder?> : SelectableAdapter<T>(activity as MainActivity) {
        protected var feedList: List<Feed>
        var selectedItem: Feed? = null
        protected var longPressedPosition: Int = 0 // used to init actionMode
        val selectedItems: List<Any>
            get() {
                val items = ArrayList<Feed>()
                for (i in 0 until itemCount) {
                    if (isSelected(i)) items.add(feedList[i])
                }
                return items
            }

        init {
            this.feedList = ArrayList()
            setHasStableIds(true)
        }
        fun clearData() {
            feedList = listOf()
        }
        fun getItem(position: Int): Any {
            return feedList[position]
        }
        override fun getItemCount(): Int {
            return feedList.size
        }
        override fun getItemId(position: Int): Long {
            if (position >= feedList.size) return RecyclerView.NO_ID // Dummy views
            return feedList[position].id
        }
        fun setItems(listItems: List<Feed>) {
            this.feedList = listItems
            notifyDataSetChanged()
        }
    }

    private inner class ListAdapter : SubscriptionsAdapter<ViewHolderExpanded>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderExpanded {
            val itemView: View = LayoutInflater.from(activity).inflate(R.layout.subscription_item, parent, false)
            return ViewHolderExpanded(itemView)
        }
        @UnstableApi override fun onBindViewHolder(holder: ViewHolderExpanded, position: Int) {
            val feed: Feed = feedList[position]
            holder.bind(feed)
            if (inActionMode()) {
                holder.selectCheckbox.visibility = View.VISIBLE
                holder.selectView.visibility = View.VISIBLE

                holder.selectCheckbox.setChecked(isSelected(position))
                holder.selectCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    setSelected(holder.bindingAdapterPosition, isChecked)
                }
                holder.coverImage.alpha = 0.6f
                holder.count.visibility = View.GONE
            } else {
                holder.selectView.visibility = View.GONE
                holder.coverImage.alpha = 1.0f
            }
            holder.coverImage.setOnClickListener {
                if (inActionMode()) holder.selectCheckbox.setChecked(!isSelected(holder.bindingAdapterPosition))
                else {
                    val fragment: Fragment = FeedInfoFragment.newInstance(feed)
                    (activity as MainActivity).loadChildFragment(fragment)
                }
            }
            holder.infoCard.setOnClickListener {
                if (inActionMode()) holder.selectCheckbox.setChecked(!isSelected(holder.bindingAdapterPosition))
                else {
                    val fragment: Fragment = FeedEpisodesFragment.newInstance(feed.id)
                    (activity as MainActivity).loadChildFragment(fragment)
                }
            }
//        holder.infoCard.setOnCreateContextMenuListener(this)
            holder.infoCard.setOnLongClickListener {
                longPressedPosition = holder.bindingAdapterPosition
                selectedItem = feed
                startSelectMode(longPressedPosition)
                true
            }
            holder.itemView.setOnTouchListener { _: View?, e: MotionEvent ->
                if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    if (!inActionMode()) {
                        longPressedPosition = holder.bindingAdapterPosition
                        selectedItem = feed
                    }
                }
                false
            }
            holder.itemView.setOnClickListener {
                if (inActionMode()) holder.selectCheckbox.setChecked(!isSelected(holder.bindingAdapterPosition))
                else {
//                    val fragment: Fragment = FeedEpisodesFragment.newInstance(feed.id)
//                    mainActivityRef.get()?.loadChildFragment(fragment)
                }
            }
        }
    }

    private inner class GridAdapter : SubscriptionsAdapter<ViewHolderBrief>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderBrief {
            val itemView: View = LayoutInflater.from(activity).inflate(R.layout.subscription_item_brief, parent, false)
            return ViewHolderBrief(itemView)
        }
        @UnstableApi override fun onBindViewHolder(holder: ViewHolderBrief, position: Int) {
            val feed: Feed = feedList[position]
            holder.bind(feed)
            if (inActionMode()) {
                holder.selectCheckbox.visibility = View.VISIBLE
                holder.selectView.visibility = View.VISIBLE

                holder.selectCheckbox.setChecked(isSelected(position))
                holder.selectCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    setSelected(holder.bindingAdapterPosition, isChecked)
                }
                holder.coverImage.alpha = 0.6f
                holder.count.visibility = View.GONE
            } else {
                holder.selectView.visibility = View.GONE
                holder.coverImage.alpha = 1.0f
            }
            holder.coverImage.setOnClickListener {
                if (inActionMode()) holder.selectCheckbox.setChecked(!isSelected(holder.bindingAdapterPosition))
                else {
                    val fragment: Fragment = FeedEpisodesFragment.newInstance(feed.id)
                    (activity as MainActivity).loadChildFragment(fragment)
                }
            }
            holder.coverImage.setOnLongClickListener {
                longPressedPosition = holder.bindingAdapterPosition
                selectedItem = feed
                startSelectMode(longPressedPosition)
                true
            }
            holder.itemView.setOnTouchListener { _: View?, e: MotionEvent ->
                if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    if (!inActionMode()) {
                        longPressedPosition = holder.bindingAdapterPosition
                        selectedItem = feed
                    }
                }
                false
            }
            holder.itemView.setOnClickListener {
                if (inActionMode()) holder.selectCheckbox.setChecked(!isSelected(holder.bindingAdapterPosition))
                else {
//                    val fragment: Fragment = FeedEpisodesFragment.newInstance(feed.id)
//                    mainActivityRef.get()?.loadChildFragment(fragment)
                }
            }
        }
    }

    private inner class ViewHolderExpanded(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = SubscriptionItemBinding.bind(itemView)
        val count: TextView = binding.episodeCount
        val coverImage: ImageView = binding.coverImage
        val infoCard: LinearLayout = binding.infoCard
        val selectView: FrameLayout = binding.selectContainer
        val selectCheckbox: CheckBox = binding.selectCheckBox
        private val errorIcon: View = binding.errorIcon

        @UnstableApi
        fun bind(feed: Feed) {
            val drawable: Drawable? = AppCompatResources.getDrawable(selectView.context, R.drawable.ic_checkbox_background)
            selectView.background = drawable // Setting this in XML crashes API <= 21
            binding.titleLabel.text = feed.title
            binding.producerLabel.text = feed.author
            binding.sortInfo.text = feed.sortInfo
            coverImage.contentDescription = feed.title
            coverImage.setImageDrawable(null)

            count.text = NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " episodes"
            count.visibility = View.VISIBLE

            val mainActRef = (activity as MainActivity)
            val coverLoader = CoverLoader(mainActRef)
            coverLoader.withUri(feed.imageUrl)
            errorIcon.visibility = if (feed.lastUpdateFailed) View.VISIBLE else View.GONE

            coverLoader.withCoverView(coverImage)
            coverLoader.load()

            val density: Float = mainActRef.resources.displayMetrics.density
            binding.outerContainer.setCardBackgroundColor(SurfaceColors.getColorForElevation(mainActRef, 1 * density))

            val textHPadding = 20
            val textVPadding = 5
            binding.titleLabel.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)
            binding.producerLabel.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)
            count.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)

            val textSize = 14
            binding.titleLabel.textSize = textSize.toFloat()
        }
    }

    private inner class ViewHolderBrief(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = SubscriptionItemBriefBinding.bind(itemView)
        private val title = binding.titleLabel
        val count: TextView = binding.episodeCount

        val coverImage: ImageView = binding.coverImage
        val selectView: FrameLayout = binding.selectContainer
        val selectCheckbox: CheckBox = binding.selectCheckBox

        private val errorIcon: View = binding.errorIcon

        @UnstableApi
        fun bind(feed: Feed) {
            val drawable: Drawable? = AppCompatResources.getDrawable(selectView.context, R.drawable.ic_checkbox_background)
            selectView.background = drawable // Setting this in XML crashes API <= 21
            title.text = feed.title
            coverImage.contentDescription = feed.title
            coverImage.setImageDrawable(null)

            count.text = NumberFormat.getInstance().format(feed.episodes.size.toLong())
            count.visibility = View.VISIBLE

            val mainActRef = (activity as MainActivity)
            val coverLoader = CoverLoader(mainActRef)
            coverLoader.withUri(feed.imageUrl)
            errorIcon.visibility = if (feed.lastUpdateFailed) View.VISIBLE else View.GONE

            coverLoader.withCoverView(coverImage)
            coverLoader.load()

            val density: Float = mainActRef.resources.displayMetrics.density
            binding.outerContainer.setCardBackgroundColor(SurfaceColors.getColorForElevation(mainActRef, 1 * density))

            val textHPadding = 20
            val textVPadding = 5
            title.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)
            count.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)

            val textSize = 14
            title.textSize = textSize.toFloat()
        }
    }

    private inner class GridDividerItemDecorator : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            super.getItemOffsets(outRect, view, parent, state)
            val context = parent.context
            val insetOffset = convertDpToPixel(context, 1f).toInt()
            outRect[insetOffset, insetOffset, insetOffset] = insetOffset
        }
        private fun convertDpToPixel(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }
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
