package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.feedOrder
import ac.mdiq.podcini.preferences.UserPreferences.useGridLayout
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedPreferences
import ac.mdiq.podcini.ui.actions.menuhandler.FeedMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
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
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*

/**
 * Fragment for displaying feed subscriptions
 */
class SubscriptionsFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var subscriptionRecycler: RecyclerView
    private lateinit var listAdapter: SubscriptionsAdapter<*>
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var toolbar: MaterialToolbar
    private lateinit var speedDialView: SpeedDialView

    private var tagFilterIndex = 1
    private var displayedFolder: String = ""
    private var displayUpArrow = false

    private var feedList: MutableList<Feed> = mutableListOf()
    private var feedListFiltered: List<Feed> = mutableListOf()

    private var useGrid: Boolean? = null

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
            subscriptionRecycler.scrollToPosition(5)
            subscriptionRecycler.post { subscriptionRecycler.smoothScrollToPosition(0) }
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

        subscriptionRecycler = binding.subscriptionsGrid
        subscriptionRecycler.addItemDecoration(GridDividerItemDecorator())
        registerForContextMenu(subscriptionRecycler)
        subscriptionRecycler.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        initAdapter()
        setupEmptyView()

        resetTags()

        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tags)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val catSpinner = binding.categorySpinner
        catSpinner.setAdapter(catAdapter)
        catSpinner.setSelection(catAdapter.getPosition("All"))
        catSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                tagFilterIndex = position
                filterOnTag()
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
                listAdapter.setItems(resultList)
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
            FeedMultiSelectActionHandler(activity as MainActivity, listAdapter.selectedItems.filterIsInstance<Feed>()).handleAction(actionItem.id)
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
                listAdapter = GridAdapter()
                spanCount = 3
            } else listAdapter = ListAdapter()
            subscriptionRecycler.layoutManager = GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, false)
            listAdapter.setOnSelectModeListener(this)
            subscriptionRecycler.adapter = listAdapter
            listAdapter.setItems(feedListFiltered)
        }
    }

    override fun onStart() {
        super.onStart()
        initAdapter()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        listAdapter.endSelectMode()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    fun filterOnTag() {
        when (tagFilterIndex) {
            1 -> feedListFiltered = feedList    // All feeds
            0 -> feedListFiltered = feedList.filter {   // feeds without tag
                val tags = it.preferences?.getTags()
                tags.isNullOrEmpty() || (tags.size == 1 && tags.toList()[0] == "#root")
            }
            else -> {   // feeds with the chosen tag
                val tag = tags[tagFilterIndex]
                feedListFiltered = feedList.filter {
                    it.preferences?.getTags()?.contains(tag) ?: false
                }
            }
        }
        binding.count.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
        listAdapter.setItems(feedListFiltered)
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
                    is FlowEvent.FeedListEvent -> onFeedListChanged(event)
                    is FlowEvent.EpisodePlayedEvent, is FlowEvent.FeedsSortedEvent -> loadSubscriptions()
                    is FlowEvent.FeedTagsChangedEvent -> resetTags()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedUpdateRunningEvent -> binding.swipeRefresh.isRefreshing = event.isFeedUpdateRunning
                    else -> {}
                }
            }
        }
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            R.id.subscriptions_sort -> FeedSortDialog.showDialog(requireContext())
//            R.id.subscriptions_filter -> SubscriptionsFilterDialog().show(childFragmentManager, "filter")
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
        emptyView.attachToRecyclerView(subscriptionRecycler)
    }

    @OptIn(UnstableApi::class) private fun loadSubscriptions() {
        emptyView.hide()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    sortFeeds()
                    val fList: List<Feed> = getFeedList()
                    fList
                }
                withContext(Dispatchers.Main) {
                    // We have fewer items. This can result in items being selected that are no longer visible.
                    if ( feedListFiltered.size > result.size) listAdapter.endSelectMode()
                    feedList = result.toMutableList()
                    filterOnTag()
                    binding.progressBar.visibility = View.GONE
                    listAdapter.setItems(feedListFiltered)
                    binding.count.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
                    emptyView.updateVisibility()
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }

//        if (UserPreferences.subscriptionsFilter.isEnabled) feedsFilteredMsg.visibility = View.VISIBLE
//        else feedsFilteredMsg.visibility = View.GONE
    }

    private fun sortFeeds() {
        Logd(TAG, "sortFeeds() called")
        val feedOrder = feedOrder
        val comparator: Comparator<Feed> = when (feedOrder) {
            UserPreferences.FEED_ORDER_UNPLAYED -> {
                val episodes = realm.query(Episode::class).query("(playState == ${Episode.NEW} OR playState == ${Episode.UNPLAYED})").find()
                val counterMap = counterMap(episodes)
                comparator(counterMap)
            }
            UserPreferences.FEED_ORDER_ALPHABETICAL -> {
                Comparator { lhs: Feed, rhs: Feed ->
                    val t1 = lhs.title
                    val t2 = rhs.title
                    when {
                        t1 == null -> 1
                        t2 == null -> -1
                        else -> t1.compareTo(t2, ignoreCase = true)
                    }
                }
            }
            UserPreferences.FEED_ORDER_MOST_PLAYED -> {
                val episodes = realm.query(Episode::class).query("playState == ${Episode.PLAYED}").find()
                val counterMap = counterMap(episodes)
                comparator(counterMap)
            }
            UserPreferences.FEED_ORDER_LAST_UPDATED -> {
                val episodes = realm.query(Episode::class).sort("pubDate", Sort.DESCENDING).find()
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (episode in episodes) {
                    val feedId = episode.feedId ?: continue
                    val pDateOld = counterMap[feedId] ?: 0
                    if (pDateOld < episode.pubDate) counterMap[feedId] = episode.pubDate
                }
                comparator(counterMap)
            }
            UserPreferences.FEED_ORDER_LAST_UNREAD_UPDATED -> {
                val episodes = realm.query(Episode::class)
                    .query("playState == ${Episode.NEW} OR playState == ${Episode.UNPLAYED}").find()
                val counterMap: MutableMap<Long, Long> = mutableMapOf()
                for (episode in episodes) {
                    val feedId = episode.feedId ?: continue
                    val pDateOld = counterMap[feedId] ?: 0
                    if (pDateOld < episode.pubDate) counterMap[feedId] = episode.pubDate
                }
                comparator(counterMap)
            }
            UserPreferences.FEED_ORDER_DOWNLOADED -> {
                val episodes = realm.query(Episode::class).query("media.downloaded == true").find()
                val counterMap = counterMap(episodes)
                comparator(counterMap)
            }
            UserPreferences.FEED_ORDER_DOWNLOADED_UNPLAYED -> {
                val episodes = realm.query(Episode::class)
                    .query("(playState == ${Episode.NEW} OR playState == ${Episode.UNPLAYED}) AND media.downloaded == true").find()
                val counterMap = counterMap(episodes)
                comparator(counterMap)
            }
            //            doing FEED_ORDER_NEW
            else -> {
                val episodes = realm.query(Episode::class).query("playState == ${Episode.NEW}").find()
                val counterMap = counterMap(episodes)
                comparator(counterMap)
            }
        }
        feedList.sortWith(comparator)
    }

    private fun counterMap(episodes: RealmResults<Episode>): Map<Long, Long> {
        val counterMap: MutableMap<Long, Long> = mutableMapOf()
        for (episode in episodes) {
            val feedId = episode.feedId ?: continue
            val count = counterMap[feedId] ?: 0
            counterMap[feedId] = count + 1
        }
        return counterMap
    }

    private fun comparator(counterMap: Map<Long, Long>): Comparator<Feed> {
        return Comparator { lhs: Feed, rhs: Feed ->
            val counterLhs = counterMap[lhs.id]?:0
            val counterRhs = counterMap[rhs.id]?:0
            when {
                // reverse natural order: podcast with most unplayed episodes first
                counterLhs > counterRhs -> -1
                counterLhs == counterRhs -> lhs.title?.compareTo(rhs.title!!, ignoreCase = true) ?: -1
                else -> 1
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val feed: Feed = listAdapter.selectedItem ?: return false
        val itemId = item.itemId
        if (itemId == R.id.multi_select) {
            speedDialView.visibility = View.VISIBLE
            return listAdapter.onContextItemSelected(item)
        }
//        TODO: this appears not called
        return FeedMenuHandler.onMenuItemClicked(this, item.itemId, feed) { this.loadSubscriptions() }
    }

    private fun onFeedListChanged(event: FlowEvent.FeedListEvent) {
//        val feeds_ = realm.query(Feed::class,"id IN $0", event.feedIds).find()
//        updateFeedMap(feeds_)
        loadSubscriptions()
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        listAdapter.setItems(feedListFiltered)
    }

    override fun onStartSelectMode() {
        speedDialView.visibility = View.VISIBLE
        val feedsOnly: MutableList<Feed> = ArrayList<Feed>()
        for (item in feedListFiltered) {
            feedsOnly.add(item)
        }
        listAdapter.setItems(feedsOnly)
    }

    @UnstableApi
    class FeedMultiSelectActionHandler(private val activity: MainActivity, private val selectedItems: List<Feed>) {
        fun handleAction(id: Int) {
            when (id) {
                R.id.remove_feed -> RemoveFeedDialog.show(activity, selectedItems)
//            R.id.notify_new_episodes -> {
//                notifyNewEpisodesPrefHandler()
//            }
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
            val viewBinding = PlaybackSpeedFeedSettingDialogBinding.inflate(activity.layoutInflater)
            viewBinding.seekBar.setProgressChangedListener { speed: Float? ->
                viewBinding.currentSpeedLabel.text = String.format(Locale.getDefault(), "%.2fx", speed)
            }
            viewBinding.useGlobalCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                viewBinding.seekBar.isEnabled = !isChecked
                viewBinding.seekBar.alpha = if (isChecked) 0.4f else 1f
                viewBinding.currentSpeedLabel.alpha = if (isChecked) 0.4f else 1f
            }
            viewBinding.seekBar.updateSpeed(1.0f)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.playback_speed)
                .setView(viewBinding.root)
                .setPositiveButton("OK") { _: DialogInterface?, _: Int ->
                    val newSpeed = if (viewBinding.useGlobalCheckbox.isChecked) FeedPreferences.SPEED_USE_GLOBAL
                    else viewBinding.seekBar.currentSpeed
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
                        feedPreferences.currentAutoDelete = autoDeleteAction
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
        val count: TextView = binding.countLabel

        val coverImage: ImageView = binding.coverImage
        val infoCard: LinearLayout = binding.infoCard
        val selectView: FrameLayout = binding.selectContainer
        val selectCheckbox: CheckBox = binding.selectCheckBox

        private val errorIcon: View = binding.errorIcon

        fun bind(drawerItem: Feed) {
            val drawable: Drawable? = AppCompatResources.getDrawable(selectView.context, R.drawable.ic_checkbox_background)
            selectView.background = drawable // Setting this in XML crashes API <= 21
            binding.titleLabel.text = drawerItem.title
            binding.producerLabel.text = drawerItem.author
            coverImage.contentDescription = drawerItem.title
            coverImage.setImageDrawable(null)

            val counter = drawerItem.episodes.size
            count.text = NumberFormat.getInstance().format(counter.toLong()) + " episodes"
            count.visibility = View.VISIBLE

            val mainActRef = (activity as MainActivity)
            val coverLoader = CoverLoader(mainActRef)
            val feed: Feed = drawerItem
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
        val count: TextView = binding.countLabel

        val coverImage: ImageView = binding.coverImage
        val selectView: FrameLayout = binding.selectContainer
        val selectCheckbox: CheckBox = binding.selectCheckBox

        private val errorIcon: View = binding.errorIcon

        fun bind(drawerItem: Feed) {
            val drawable: Drawable? = AppCompatResources.getDrawable(selectView.context, R.drawable.ic_checkbox_background)
            selectView.background = drawable // Setting this in XML crashes API <= 21
            title.text = drawerItem.title
            coverImage.contentDescription = drawerItem.title
            coverImage.setImageDrawable(null)

            val counter = drawerItem.episodes.size
            count.text = NumberFormat.getInstance().format(counter.toLong())
            count.visibility = View.VISIBLE

            val mainActRef = (activity as MainActivity)
            val coverLoader = CoverLoader(mainActRef)
            val feed: Feed = drawerItem
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

    companion object {
        val TAG = SubscriptionsFragment::class.simpleName ?: "Anonymous"

        private const val KEY_UP_ARROW = "up_arrow"
        private const val ARGUMENT_FOLDER = "folder"

        private val tags: MutableList<String> = mutableListOf()

        fun newInstance(folderTitle: String?): SubscriptionsFragment {
            val fragment = SubscriptionsFragment()
            val args = Bundle()
            args.putString(ARGUMENT_FOLDER, folderTitle)
            fragment.arguments = args
            return fragment
        }

        fun resetTags() {
            tags.clear()
            tags.add("Untagged")
            tags.add("All")
            tags.addAll(getTags())
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
}
