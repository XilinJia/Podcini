package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FragmentSubscriptionsBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.NavDrawerData
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.ui.actions.FeedMultiSelectActionHandler
import ac.mdiq.podcini.ui.actions.menuhandler.FeedMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.adapter.SubscriptionsAdapter
import ac.mdiq.podcini.ui.dialog.FeedSortDialog
import ac.mdiq.podcini.ui.dialog.SubscriptionsFilterDialog
import ac.mdiq.podcini.ui.view.EmptyViewHandler
import ac.mdiq.podcini.ui.view.LiftOnScrollListener
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Fragment for displaying feed subscriptions
 */
class SubscriptionFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var subscriptionRecycler: RecyclerView
    private lateinit var subscriptionAdapter: SubscriptionsAdapter
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var feedsInfoMsg: LinearLayout
    private lateinit var feedsFilteredMsg: TextView
    private lateinit var feedCount: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences
    private lateinit var speedDialView: SpeedDialView

    private var tagFilterIndex = 1
    private var displayedFolder: String = ""
    private var displayUpArrow = false

//    val scope = CoroutineScope(Dispatchers.Main)
//    private var disposable: Disposable? = null
    private var feedList: List<NavDrawerData.FeedDrawerItem> = mutableListOf()
    private var feedListFiltered: List<NavDrawerData.FeedDrawerItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        prefs = requireActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
        subscriptionRecycler.addItemDecoration(SubscriptionsAdapter.GridDividerItemDecorator())
        registerForContextMenu(subscriptionRecycler)
        subscriptionRecycler.addOnScrollListener(LiftOnScrollListener(binding.appbar))
        subscriptionAdapter = object : SubscriptionsAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu) { item: MenuItem ->
                    this@SubscriptionFragment.onContextItemSelected(item)
                }
            }
        }
        val gridLayoutManager = GridLayoutManager(context, 1, RecyclerView.VERTICAL, false)
        subscriptionRecycler.layoutManager = gridLayoutManager

        subscriptionAdapter.setOnSelectModeListener(this)
        subscriptionRecycler.adapter = subscriptionAdapter
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
                    it.title?.lowercase(Locale.getDefault())?.contains(text)?:false
                            || it.feed.author?.lowercase(Locale.getDefault())?.contains(text)?:false
                }
                subscriptionAdapter.setItems(resultList)
                true
            } else false
        }

        progressBar = binding.progressBar
        progressBar.visibility = View.VISIBLE

        val subscriptionAddButton: FloatingActionButton = binding.subscriptionsAdd
        subscriptionAddButton.setOnClickListener {
            if (activity is MainActivity) (activity as MainActivity).loadChildFragment(AddFeedFragment())
        }

        feedsInfoMsg = binding.feedsInfoMessage
//        feedsInfoMsg.setOnClickListener {
//            SubscriptionsFilterDialog().show(
//                childFragmentManager, "filter")
//        }
        feedsFilteredMsg = binding.feedsFilteredMessage
        feedCount = binding.count
        feedCount.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()

        swipeRefreshLayout = binding.swipeRefresh
        swipeRefreshLayout.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
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
            FeedMultiSelectActionHandler(activity as MainActivity, subscriptionAdapter.selectedItems.filterIsInstance<Feed>()).handleAction(actionItem.id)
            true
        }

        loadSubscriptions()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        
//        scope.cancel()
//        disposable?.dispose()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    fun filterOnTag() {
        when (tagFilterIndex) {
//            All feeds
            1 -> feedListFiltered = feedList
//            feeds without tag
            0 -> feedListFiltered = feedList.filter {
                val tags = it.feed.preferences?.getTags()
                tags.isNullOrEmpty() || (tags.size == 1 && tags.toList()[0] == "#root")
            }
//            feeds with the chosen tag
            else -> {
                val tag = tags[tagFilterIndex]
                feedListFiltered = feedList.filter {
                    it.feed.preferences?.getTags()?.contains(tag) ?: false
                }
            }
        }
        feedCount.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
        subscriptionAdapter.setItems(feedListFiltered)
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.FeedListUpdateEvent -> onFeedListChanged(event)
                    is FlowEvent.UnreadItemsUpdateEvent -> loadSubscriptions()
                    is FlowEvent.FeedTagsChangedEvent -> resetTags()
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                when (event) {
                    is FlowEvent.FeedUpdateRunningEvent -> swipeRefreshLayout.isRefreshing = event.isFeedUpdateRunning
                    else -> {}
                }
            }
        }
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.action_search -> {
                (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                return true
            }
            R.id.subscriptions_sort -> {
                FeedSortDialog.showDialog(requireContext())
                return true
            }
            R.id.subscriptions_filter -> {
                SubscriptionsFilterDialog().show(childFragmentManager, "filter")
                return true
            }
            R.id.refresh_item -> {
                FeedUpdateManager.runOnceOrAsk(requireContext())
                return true
            }
            else -> return false
        }
    }

    private fun setupEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_subscriptions)
        emptyView.setTitle(R.string.no_subscriptions_head_label)
        emptyView.setMessage(R.string.no_subscriptions_label)
        emptyView.attachToRecyclerView(subscriptionRecycler)
    }

    override fun onStop() {
        super.onStop()
        subscriptionAdapter.endSelectMode()
    }

    private fun loadSubscriptions() {
//        disposable?.dispose()
        emptyView.hide()
//        disposable = Observable.fromCallable {
//            val data: NavDrawerData = DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter)
//            val items: List<NavDrawerData.FeedDrawerItem> = data.items
//            items
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(
//                { result: List<NavDrawerData.FeedDrawerItem> ->
//                    // We have fewer items. This can result in items being selected that are no longer visible.
//                    if ( feedListFiltered.size > result.size) subscriptionAdapter.endSelectMode()
//                    feedList = result
//                    filterOnTag()
//                    progressBar.visibility = View.GONE
//                    subscriptionAdapter.setItems(feedListFiltered)
//                    feedCount.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
//                    emptyView.updateVisibility()
//                }, { error: Throwable? ->
//                    Log.e(TAG, Log.getStackTraceString(error))
//                })

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val data: NavDrawerData = DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter)
                    val items: List<NavDrawerData.FeedDrawerItem> = data.items
                    items
                }
                withContext(Dispatchers.Main) {
                    // We have fewer items. This can result in items being selected that are no longer visible.
                    if ( feedListFiltered.size > result.size) subscriptionAdapter.endSelectMode()
                    feedList = result
                    filterOnTag()
                    progressBar.visibility = View.GONE
                    subscriptionAdapter.setItems(feedListFiltered)
                    feedCount.text = feedListFiltered.size.toString() + " / " + feedList.size.toString()
                    emptyView.updateVisibility()
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }

        if (UserPreferences.subscriptionsFilter.isEnabled) feedsFilteredMsg.visibility = View.VISIBLE
        else feedsFilteredMsg.visibility = View.GONE
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val drawerItem: NavDrawerData.FeedDrawerItem = subscriptionAdapter.getSelectedItem() ?: return false
        val itemId = item.itemId

        val feed: Feed = drawerItem.feed
        if (itemId == R.id.multi_select) {
            speedDialView.visibility = View.VISIBLE
            return subscriptionAdapter.onContextItemSelected(item)
        }
        return FeedMenuHandler.onMenuItemClicked(this, item.itemId, feed) { this.loadSubscriptions() }
    }

    fun onFeedListChanged(event: FlowEvent.FeedListUpdateEvent?) {
        DBReader.updateFeedList()
        loadSubscriptions()
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        subscriptionAdapter.setItems(feedListFiltered)
    }

    override fun onStartSelectMode() {
        speedDialView.visibility = View.VISIBLE
        val feedsOnly: MutableList<NavDrawerData.FeedDrawerItem> = ArrayList<NavDrawerData.FeedDrawerItem>()
        for (item in feedListFiltered) {
            feedsOnly.add(item)
        }
        subscriptionAdapter.setItems(feedsOnly)
    }

    companion object {
        const val TAG: String = "SubscriptionFragment"
        private const val PREFS = "SubscriptionFragment"
        private const val KEY_UP_ARROW = "up_arrow"
        private const val ARGUMENT_FOLDER = "folder"

        private val tags: MutableList<String> = mutableListOf()

        fun newInstance(folderTitle: String?): SubscriptionFragment {
            val fragment = SubscriptionFragment()
            val args = Bundle()
            args.putString(ARGUMENT_FOLDER, folderTitle)
            fragment.arguments = args
            return fragment
        }

        fun resetTags() {
            tags.clear()
            tags.add("Untagged")
            tags.add("All")
            tags.addAll(DBReader.getTags())
        }
    }
}
