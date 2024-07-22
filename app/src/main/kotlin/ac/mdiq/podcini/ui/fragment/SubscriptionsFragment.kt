package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.ui.actions.menuhandler.FeedMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.FeedFilterDialog
import ac.mdiq.podcini.ui.dialog.FeedSortDialog
import ac.mdiq.podcini.ui.dialog.RemoveFeedDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.ui.utils.CoverLoader
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
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
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for displaying feed subscriptions
 */
class SubscriptionsFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

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
        speedDialView.inflate(R.menu.nav_feed_action_speeddial)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }
            override fun onToggleChanged(isOpen: Boolean) {}
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            FeedMultiSelectActionHandler(activity as MainActivity, adapter.selectedItems.filterIsInstance<Feed>()).handleAction(actionItem.id)
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

    fun queryStringOfTags() : String {
        return when (tagFilterIndex) {
            1 ->  ""    // All feeds
            0 ->  " preferences.tags.@count == 0 OR (preferences.tags.@count == 0 AND ALL preferences.tags == '#root' ) "
            else -> {   // feeds with the chosen tag
                val tag = tags[tagFilterIndex]
                " ANY preferences.tags == '$tag' "
            }
        }
    }

    fun filterOnTag() {
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
                        filterAndSort()
                        resetTags()
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
        val feeds_ = feedList_
        val feedOrder = feedOrderBy
        val dir = 1 - 2*feedOrderDir    // get from 0, 1 to 1, -1
        val comparator: Comparator<Feed> = when (feedOrder) {
            FeedSortOrder.UNPLAYED_NEW_OLD.index -> {
                val queryString = "feedId == $0 AND (playState == ${Episode.NEW} OR playState == ${Episode.UNPLAYED})"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = c.toString() + " unplayed"
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.ALPHABETIC_A_Z.index -> {
                for (f in feeds_) f.sortInfo = ""
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
                val queryString = "feedId == $0 AND playState == ${Episode.PLAYED}"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = c.toString() + " played"
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.LAST_UPDATED_NEW_OLD.index -> {
                val queryString = "feedId == $0 SORT(pubDate DESC)"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
                    counterMap[f.id] = d
                    val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                    f.sortInfo = "Updated on " + dateFormat.format(Date(d))
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.LAST_DOWNLOAD_NEW_OLD.index -> {
                val queryString = "feedId == $0 SORT(media.downloadTime DESC)"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.media?.downloadTime ?: 0L
                    counterMap[f.id] = d
                    val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                    f.sortInfo = "Downloaded on " + dateFormat.format(Date(d))
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.LAST_UPDATED_UNPLAYED_NEW_OLD.index -> {
                val queryString = "feedId == $0 AND (playState == ${Episode.NEW} OR playState == ${Episode.UNPLAYED}) SORT(pubDate DESC)"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val d = realm.query(Episode::class).query(queryString, f.id).first().find()?.pubDate ?: 0L
                    counterMap[f.id] = d
                    val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                    f.sortInfo = "Unplayed since " + dateFormat.format(Date(d))
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.MOST_DOWNLOADED.index -> {
                val queryString = "feedId == $0 AND media.downloaded == true"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = c.toString() + " downloaded"
                }
                comparator(counterMap, dir)
            }
            FeedSortOrder.MOST_DOWNLOADED_UNPLAYED.index -> {
                val queryString = "feedId == $0 AND (playState == ${Episode.NEW} OR playState == ${Episode.UNPLAYED}) AND media.downloaded == true"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = c.toString() + " downloaded unplayed"
                }
                comparator(counterMap, dir)
            }
            //            doing FEED_ORDER_NEW
            else -> {
                val queryString = "feedId == $0 AND playState == ${Episode.NEW}"
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (f in feeds_) {
                    val c = realm.query(Episode::class).query(queryString, f.id).count().find()
                    counterMap[f.id] = c
                    f.sortInfo = c.toString() + " new"
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

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val feed: Feed = adapter.selectedItem ?: return false
        val itemId = item.itemId
        if (itemId == R.id.multi_select) {
            speedDialView.visibility = View.VISIBLE
            return adapter.onContextItemSelected(item)
        }
//        TODO: this appears not called
        return FeedMenuHandler.onMenuItemClicked(this, item.itemId, feed) { this.loadSubscriptions() }
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
    class FeedMultiSelectActionHandler(private val activity: MainActivity, private val selectedItems: List<Feed>) {
        fun handleAction(id: Int) {
            when (id) {
                R.id.remove_feed -> RemoveFeedDialog.show(activity, selectedItems)
                R.id.keep_updated -> keepUpdatedPrefHandler()
                R.id.autodownload -> autoDownloadPrefHandler()
                R.id.autoDeleteDownload -> autoDeleteEpisodesPrefHandler()
                R.id.playback_speed -> playbackSpeedPrefHandler()
                R.id.edit_tags -> TagSettingsDialog.newInstance(selectedItems).show(activity.supportFragmentManager, TAG)
                else -> Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=$id")
            }
        }
        private fun autoDownloadPrefHandler() {
            val preferenceSwitchDialog = PreferenceSwitchDialog(activity, activity.getString(R.string.auto_download_settings_label), activity.getString(R.string.auto_download_label))
            preferenceSwitchDialog.setOnPreferenceChangedListener(@UnstableApi object: PreferenceSwitchDialog.OnPreferenceChangedListener {
                override fun preferenceChanged(enabled: Boolean) {
//                saveFeedPreferences { feedPreferences: FeedPreferences -> feedPreferences.autoDownload = enabled }
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
                    saveFeedPreferences { feedPreferences: FeedPreferences ->
                        feedPreferences.playSpeed = newSpeed
                    }
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
        private fun autoDeleteEpisodesPrefHandler() {
            val preferenceListDialog = PreferenceListDialog(activity, activity.getString(R.string.auto_delete_label))
            val items: Array<String> = activity.resources.getStringArray(R.array.spnAutoDeleteItems)
            preferenceListDialog.openDialog(items)
            preferenceListDialog.setOnPreferenceChangedListener(object: PreferenceListDialog.OnPreferenceChangedListener {
                @UnstableApi override fun preferenceChanged(pos: Int) {
                    val autoDeleteAction: FeedPreferences.AutoDeleteAction = FeedPreferences.AutoDeleteAction.fromCode(pos)
                    saveFeedPreferences { feedPreferences: FeedPreferences ->
                        feedPreferences.autoDeleteAction = autoDeleteAction
                    }
                }
            })
        }
        private fun keepUpdatedPrefHandler() {
            val preferenceSwitchDialog = PreferenceSwitchDialog(activity, activity.getString(R.string.kept_updated), activity.getString(R.string.keep_updated_summary))
            preferenceSwitchDialog.setOnPreferenceChangedListener(object: PreferenceSwitchDialog.OnPreferenceChangedListener {
                @UnstableApi override fun preferenceChanged(enabled: Boolean) {
                    saveFeedPreferences { feedPreferences: FeedPreferences ->
                        feedPreferences.keepUpdated = enabled
                    }
                }
            })
            preferenceSwitchDialog.openDialog()
        }
        @UnstableApi private fun saveFeedPreferences(preferencesConsumer: Consumer<FeedPreferences>) {
            for (feed in selectedItems) {
                if (feed.preferences == null) continue
                preferencesConsumer.accept(feed.preferences!!)
                persistFeedPreferences(feed)
//                EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feed.preferences!!.feedID))
            }
            val numItems = selectedItems.size
            activity.showSnackbarAbovePlayer(activity.resources.getQuantityString(R.plurals.updated_feeds_batch_label, numItems, numItems), Snackbar.LENGTH_LONG)
        }
        companion object {
            private val TAG: String = FeedMultiSelectActionHandler::class.simpleName ?: "Anonymous"
        }
    }

    @OptIn(UnstableApi::class)
    private abstract inner class SubscriptionsAdapter<T : RecyclerView.ViewHolder?> : SelectableAdapter<T>(activity as MainActivity), View.OnCreateContextMenuListener {
        protected var feedList: List<Feed>
        var selectedItem: Feed? = null
        protected var longPressedPosition: Int = 0 // used to init actionMode
        val selectedItems: List<Any>
            get() {
                val items = ArrayList<Feed>()
                for (i in 0 until itemCount) {
                    if (isSelected(i)) {
                        val feed: Feed = feedList[i]
                        items.add(feed)
                    }
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
        @OptIn(UnstableApi::class)
        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            if (selectedItem == null) return
            val mainActRef = (activity as MainActivity)
            val inflater: MenuInflater = mainActRef.menuInflater
            if (inActionMode()) {
//            inflater.inflate(R.menu.multi_select_context_popup, menu)
//            menu.findItem(R.id.multi_select).setVisible(true)
            } else {
                inflater.inflate(R.menu.nav_feed_context, menu)
//            menu.findItem(R.id.multi_select).setVisible(true)
                menu.setHeaderTitle(selectedItem?.title)
            }
            MenuItemUtils.setOnClickListeners(menu) { item: MenuItem ->
                this@SubscriptionsFragment.onContextItemSelected(item)
            }
        }
        fun onContextItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.multi_select) {
                startSelectMode(longPressedPosition)
                return true
            }
            return false
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

    class PreferenceListDialog(private var context: Context, private val title: String) {
        private var onPreferenceChangedListener: OnPreferenceChangedListener? = null
        private var selectedPos = 0

        interface OnPreferenceChangedListener {
            /**
             * Notified when user confirms preference
             * @param pos The index of the item that was selected
             */
            fun preferenceChanged(pos: Int)
        }
        fun openDialog(items: Array<String>?) {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(title)
            builder.setSingleChoiceItems(items, selectedPos) { _: DialogInterface?, which: Int ->
                selectedPos = which
            }
            builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
                if (onPreferenceChangedListener != null && selectedPos >= 0) onPreferenceChangedListener!!.preferenceChanged(selectedPos)
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.create().show()
        }
        fun setOnPreferenceChangedListener(onPreferenceChangedListener: OnPreferenceChangedListener?) {
            this.onPreferenceChangedListener = onPreferenceChangedListener
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
