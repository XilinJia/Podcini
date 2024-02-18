package ac.mdiq.podvinci.fragment

import ac.mdiq.podvinci.activity.MainActivity
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.adapter.SelectableAdapter
import ac.mdiq.podvinci.adapter.SubscriptionsRecyclerAdapter
import ac.mdiq.podvinci.core.menuhandler.MenuItemUtils
import ac.mdiq.podvinci.core.storage.DBReader
import ac.mdiq.podvinci.core.storage.NavDrawerData
import ac.mdiq.podvinci.core.util.download.FeedUpdateManager
import ac.mdiq.podvinci.dialog.FeedSortDialog
import ac.mdiq.podvinci.dialog.RenameItemDialog
import ac.mdiq.podvinci.dialog.SubscriptionsFilterDialog
import ac.mdiq.podvinci.event.FeedListUpdateEvent
import ac.mdiq.podvinci.event.FeedUpdateRunningEvent
import ac.mdiq.podvinci.event.UnreadItemsUpdateEvent
import ac.mdiq.podvinci.fragment.actions.FeedMultiSelectActionHandler
import ac.mdiq.podvinci.menuhandler.FeedMenuHandler
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import ac.mdiq.podvinci.ui.statistics.StatisticsFragment
import ac.mdiq.podvinci.view.EmptyViewHandler
import ac.mdiq.podvinci.view.LiftOnScrollListener
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

/**
 * Fragment for displaying feed subscriptions
 */
class SubscriptionFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {
    private lateinit var subscriptionRecycler: RecyclerView
    private lateinit var subscriptionAdapter: SubscriptionsRecyclerAdapter
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var feedsFilteredMsg: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences
    private lateinit var speedDialView: SpeedDialView

    private var displayedFolder: String = ""
    private var displayUpArrow = false

    private var disposable: Disposable? = null
    private var listItems: List<NavDrawerData.DrawerItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        prefs = requireActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                           savedInstanceState: Bundle?
    ): View {
        val root: View = inflater.inflate(R.layout.fragment_subscriptions, container, false)
        toolbar = root.findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener { v: View? ->
            subscriptionRecycler.scrollToPosition(5)
            subscriptionRecycler.post { subscriptionRecycler.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.subscriptions)
        for (i in COLUMN_CHECKBOX_IDS.indices) {
            // Do this in Java to localize numbers
            toolbar.menu?.findItem(COLUMN_CHECKBOX_IDS[i])
                ?.setTitle(String.format(Locale.getDefault(), "%d", i + MIN_NUM_COLUMNS))
        }
        refreshToolbarState()

        if (arguments != null) {
            displayedFolder = requireArguments().getString(ARGUMENT_FOLDER, null)
            toolbar.title = displayedFolder
        }

        subscriptionRecycler = root.findViewById(R.id.subscriptions_grid)
        subscriptionRecycler.addItemDecoration(SubscriptionsRecyclerAdapter.GridDividerItemDecorator())
        registerForContextMenu(subscriptionRecycler)
        subscriptionRecycler.addOnScrollListener(LiftOnScrollListener(root.findViewById(R.id.appbar)))
        subscriptionAdapter = object : SubscriptionsRecyclerAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem ->
                    this@SubscriptionFragment.onContextItemSelected(item)
                }
            }
        }
        setColumnNumber(prefs.getInt(PREF_NUM_COLUMNS, defaultNumOfColumns))
        subscriptionAdapter.setOnSelectModeListener(this)
        subscriptionRecycler.adapter = subscriptionAdapter
        setupEmptyView()

        progressBar = root.findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        val subscriptionAddButton: FloatingActionButton =
            root.findViewById(R.id.subscriptions_add)
        subscriptionAddButton.setOnClickListener { view: View? ->
            if (activity is MainActivity) {
                (activity as MainActivity).loadChildFragment(AddFeedFragment())
            }
        }

        feedsFilteredMsg = root.findViewById(R.id.feeds_filtered_message)
        feedsFilteredMsg.setOnClickListener { l: View? ->
            SubscriptionsFilterDialog().show(
                childFragmentManager, "filter")
        }

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext())
        }

        speedDialView = root.findViewById(R.id.fabSD)
        speedDialView.overlayLayout = root.findViewById(R.id.fabSDOverlay)
        speedDialView.inflate(R.menu.nav_feed_action_speeddial)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }

            override fun onToggleChanged(isOpen: Boolean) {
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            FeedMultiSelectActionHandler(activity as MainActivity,
                subscriptionAdapter.selectedItems.filterIsInstance<Feed>()).handleAction(actionItem.id)
            true
        }

        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshToolbarState() {
        val columns: Int = prefs.getInt(PREF_NUM_COLUMNS, defaultNumOfColumns)
        toolbar.menu?.findItem(COLUMN_CHECKBOX_IDS[columns - MIN_NUM_COLUMNS])?.setChecked(true)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.isRefreshing = event.isFeedUpdateRunning
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.refresh_item -> {
                FeedUpdateManager.runOnceOrAsk(requireContext())
                return true
            }
            R.id.subscriptions_filter -> {
                SubscriptionsFilterDialog().show(childFragmentManager, "filter")
                return true
            }
            R.id.subscriptions_sort -> {
                FeedSortDialog.showDialog(requireContext())
                return true
            }
            R.id.subscription_num_columns_2 -> {
                setColumnNumber(2)
                return true
            }
            R.id.subscription_num_columns_3 -> {
                setColumnNumber(3)
                return true
            }
            R.id.subscription_num_columns_4 -> {
                setColumnNumber(4)
                return true
            }
            R.id.subscription_num_columns_5 -> {
                setColumnNumber(5)
                return true
            }
            R.id.action_search -> {
                (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                return true
            }
            R.id.action_statistics -> {
                (activity as MainActivity).loadChildFragment(StatisticsFragment())
                return true
            }
            else -> return false
        }
    }

    private fun setColumnNumber(columns: Int) {
        val gridLayoutManager = GridLayoutManager(context,
            columns, RecyclerView.VERTICAL, false)
        subscriptionAdapter.setColumnCount(columns)
        subscriptionRecycler.layoutManager = gridLayoutManager
        prefs.edit().putInt(PREF_NUM_COLUMNS, columns).apply()
        refreshToolbarState()
    }

    private fun setupEmptyView() {
        emptyView = EmptyViewHandler(context)
        emptyView.setIcon(R.drawable.ic_subscriptions)
        emptyView.setTitle(R.string.no_subscriptions_head_label)
        emptyView.setMessage(R.string.no_subscriptions_label)
        emptyView.attachToRecyclerView(subscriptionRecycler)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        loadSubscriptions()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
        subscriptionAdapter.endSelectMode()
    }

    private fun loadSubscriptions() {
        disposable?.dispose()
        emptyView.hide()
        disposable = Observable.fromCallable {
            val data: NavDrawerData = DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter)
            val items: List<NavDrawerData.DrawerItem> = data.items
            for (item in items) {
                if (item.type == NavDrawerData.DrawerItem.Type.TAG && item.title == displayedFolder) {
                    return@fromCallable (item as NavDrawerData.TagDrawerItem).children
                }
            }
            items
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: List<NavDrawerData.DrawerItem> ->
                    if ( listItems.size > result.size) {
                        // We have fewer items. This can result in items being selected that are no longer visible.
                        subscriptionAdapter.endSelectMode()
                    }
                    listItems = result
                    progressBar.visibility = View.GONE
                    subscriptionAdapter.setItems(result)
                    emptyView.updateVisibility()
                }, { error: Throwable? ->
                    Log.e(TAG, Log.getStackTraceString(error))
                })

        if (UserPreferences.subscriptionsFilter.isEnabled) {
            feedsFilteredMsg.visibility = View.VISIBLE
        } else {
            feedsFilteredMsg.visibility = View.GONE
        }
    }

    private val defaultNumOfColumns: Int
        get() = resources.getInteger(R.integer.subscriptions_default_num_of_columns)

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val drawerItem: NavDrawerData.DrawerItem = subscriptionAdapter.getSelectedItem() ?: return false
        val itemId = item.itemId
        if (drawerItem.type == NavDrawerData.DrawerItem.Type.TAG && itemId == R.id.rename_folder_item) {
            RenameItemDialog(activity as Activity, drawerItem).show()
            return true
        }

        val feed: Feed = (drawerItem as NavDrawerData.FeedDrawerItem).feed
        if (itemId == R.id.multi_select) {
            speedDialView.visibility = View.VISIBLE
            return subscriptionAdapter.onContextItemSelected(item)
        }
        return FeedMenuHandler.onMenuItemClicked(this, item.itemId, feed) { this.loadSubscriptions() }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        loadSubscriptions()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        loadSubscriptions()
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        subscriptionAdapter.setItems(listItems)
    }

    override fun onStartSelectMode() {
        val feedsOnly: MutableList<NavDrawerData.DrawerItem> = ArrayList<NavDrawerData.DrawerItem>()
        for (item in listItems) {
            if (item.type == NavDrawerData.DrawerItem.Type.FEED) {
                feedsOnly.add(item)
            }
        }
        subscriptionAdapter.setItems(feedsOnly)
    }

    companion object {
        const val TAG: String = "SubscriptionFragment"
        private const val PREFS = "SubscriptionFragment"
        private const val PREF_NUM_COLUMNS = "columns"
        private const val KEY_UP_ARROW = "up_arrow"
        private const val ARGUMENT_FOLDER = "folder"

        private const val MIN_NUM_COLUMNS = 2
        private val COLUMN_CHECKBOX_IDS = intArrayOf(R.id.subscription_num_columns_2,
            R.id.subscription_num_columns_3,
            R.id.subscription_num_columns_4,
            R.id.subscription_num_columns_5)

        fun newInstance(folderTitle: String?): SubscriptionFragment {
            val fragment = SubscriptionFragment()
            val args = Bundle()
            args.putString(ARGUMENT_FOLDER, folderTitle)
            fragment.arguments = args
            return fragment
        }
    }
}
