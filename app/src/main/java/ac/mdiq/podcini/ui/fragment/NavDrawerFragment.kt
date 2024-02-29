package ac.mdiq.podcini.ui.fragment

//import ac.mdiq.podcini.ui.home.HomeFragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.NavListBinding
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.NavDrawerData
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.adapter.NavListAdapter
import ac.mdiq.podcini.ui.appstartintent.MainActivityStarter
import ac.mdiq.podcini.ui.common.ThemeUtils
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.ui.menuhandler.MenuItemUtils
import android.R.attr
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.Insets
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max

class NavDrawerFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var navDrawerData: NavDrawerData? = null
    private var flatItemList: List<NavDrawerData.FeedDrawerItem>? = null
    private var contextPressedItem: NavDrawerData.FeedDrawerItem? = null
    private var disposable: Disposable? = null

    private lateinit var navAdapter: NavListAdapter
    private lateinit var progressBar: ProgressBar
    
    private var openFolders: MutableSet<String> = HashSet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = NavListBinding.inflate(inflater)
//        val root: View = inflater.inflate(R.layout.nav_list, container, false)

        Log.d(TAG, "fragment onCreateView")
        setupDrawerRoundBackground(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root
        ) { view: View, insets: WindowInsetsCompat ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            var navigationBarHeight = 0f
            val activity: Activity? = activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity != null) {
                navigationBarHeight = if (requireActivity().window.navigationBarDividerColor == Color.TRANSPARENT
                ) 0f else 1 * resources.displayMetrics.density // Assuming the divider is 1dp in height
            }
            val bottomInset = max(0.0, Math.round(bars.bottom - navigationBarHeight).toDouble())
                .toFloat()
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottomInset.toInt()
            insets
        }

        val preferences: SharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        TODO: what is this?
        openFolders = HashSet(preferences.getStringSet(PREF_OPEN_FOLDERS, HashSet<String>())) // Must not modify

        progressBar = binding.progressBar
        val navList = binding.navRecycler
        navAdapter = NavListAdapter(itemAccess, requireActivity())
        navAdapter.setHasStableIds(true)
        navList.adapter = navAdapter
        navList.layoutManager = LinearLayoutManager(context)

        binding.navSettings.setOnClickListener {
            startActivity(Intent(activity, PreferenceActivity::class.java))
        }

        preferences.registerOnSharedPreferenceChangeListener(this)
        return binding.root
    }

    private fun setupDrawerRoundBackground(root: View) {
        // Akin to this logic:
        //   https://github.com/material-components/material-components-android/blob/8938da8c/lib/java/com/google/android/material/navigation/NavigationView.java#L405
        val shapeBuilder: ShapeAppearanceModel.Builder = ShapeAppearanceModel.builder()
        val cornerSize = resources.getDimension(R.dimen.drawer_corner_size)
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        if (isRtl) {
            shapeBuilder.setTopLeftCornerSize(cornerSize).setBottomLeftCornerSize(cornerSize)
        } else {
            shapeBuilder.setTopRightCornerSize(cornerSize).setBottomRightCornerSize(cornerSize)
        }
        val drawable = MaterialShapeDrawable(shapeBuilder.build())
        val themeColor = ThemeUtils.getColorFromAttr(root.context, attr.colorBackground)
        drawable.fillColor = ColorStateList.valueOf(themeColor)
        root.background = drawable
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()

        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater = requireActivity().menuInflater
        if (contextPressedItem != null) {
            menu.setHeaderTitle(contextPressedItem!!.title)
            inflater.inflate(R.menu.nav_feed_context, menu)
            // episodes are not loaded, so we cannot check if the podcast has new or unplayed ones!
        }
        MenuItemUtils.setOnClickListeners(menu
        ) { item: MenuItem -> this.onContextItemSelected(item) }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val pressedItem: NavDrawerData.FeedDrawerItem? = contextPressedItem
        contextPressedItem = null
        if (pressedItem == null) {
            return false
        }
        return onFeedContextMenuClicked(pressedItem.feed, item)
    }

    @OptIn(UnstableApi::class) private fun onFeedContextMenuClicked(feed: Feed, item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.remove_all_inbox_item -> {
                val removeAllNewFlagsConfirmationDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(),
                    R.string.remove_all_inbox_label,
                    R.string.remove_all_inbox_confirmation_msg) {
                    @OptIn(UnstableApi::class) override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        dialog.dismiss()
                        DBWriter.removeFeedNewFlag(feed.id)
                    }
                }
                removeAllNewFlagsConfirmationDialog.createNewDialog().show()
                return true
            }
            R.id.edit_tags -> {
                if (feed.preferences != null)
                    TagSettingsDialog.newInstance(listOf(feed.preferences!!)).show(childFragmentManager, TagSettingsDialog.TAG)
                return true
            }
            R.id.rename_item -> {
                RenameItemDialog(activity as Activity, feed).show()
                return true
            }
            R.id.remove_feed -> {
                RemoveFeedDialog.show(requireContext(), feed) {
                    if (feed.id.toString() == getLastNavFragment(requireContext())) {
                        (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                        // Make sure fragment is hidden before actually starting to delete
                        requireActivity().supportFragmentManager.executePendingTransactions()
                    }
                }
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

    private fun onTagContextMenuClicked(drawerItem: NavDrawerData.FeedDrawerItem?, item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.rename_folder_item) {
            RenameItemDialog(activity as Activity, drawerItem).show()
            return true
        }
        return super.onContextItemSelected(item)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: ac.mdiq.podcini.util.event.UnreadItemsUpdateEvent?) {
        loadData()
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: ac.mdiq.podcini.util.event.FeedListUpdateEvent?) {
        loadData()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: ac.mdiq.podcini.util.event.QueueEvent) {
        Log.d(TAG, "onQueueChanged($event)")
        // we are only interested in the number of queue items, not download status or position
        if (event.action == ac.mdiq.podcini.util.event.QueueEvent.Action.DELETED_MEDIA || event.action == ac.mdiq.podcini.util.event.QueueEvent.Action.SORTED || event.action == ac.mdiq.podcini.util.event.QueueEvent.Action.MOVED) {
            return
        }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private val itemAccess: NavListAdapter.ItemAccess = object : NavListAdapter.ItemAccess {
        override val count: Int
            get() = if (flatItemList != null) {
                flatItemList!!.size
            } else {
                0
            }

        override fun getItem(position: Int): NavDrawerData.FeedDrawerItem? {
            return if (flatItemList != null && 0 <= position && position < flatItemList!!.size) {
                flatItemList!![position]
            } else {
                null
            }
        }

        override fun isSelected(position: Int): Boolean {
            val lastNavFragment = getLastNavFragment(requireContext())
            if (position < navAdapter.subscriptionOffset) {
                return navAdapter.getFragmentTags()[position] == lastNavFragment
            } else if (StringUtils.isNumeric(lastNavFragment)) { // last fragment was not a list, but a feed
                val feedId = lastNavFragment.toLong()
                if (navDrawerData != null) {
                    val itemToCheck: NavDrawerData.FeedDrawerItem = flatItemList!![position - navAdapter.subscriptionOffset]
                    // When the same feed is displayed multiple times, it should be highlighted multiple times.
                    return itemToCheck.feed.id == feedId
                }
            }
            return false
        }

        override val queueSize: Int
            get() = if ((navDrawerData != null)) navDrawerData!!.queueSize else 0

        override val numberOfNewItems: Int
            get() = if ((navDrawerData != null)) navDrawerData!!.numNewItems else 0

        override val numberOfDownloadedItems: Int
            get() = if ((navDrawerData != null)) navDrawerData!!.numDownloadedItems else 0

        override val reclaimableItems: Int
            get() = if ((navDrawerData != null)) navDrawerData!!.reclaimableSpace else 0

        override val feedCounterSum: Int
            get() {
                if (navDrawerData == null) {
                    return 0
                }
                var sum = 0
                for (counter in navDrawerData!!.feedCounters.values) {
                    sum += counter
                }
                return sum
            }

        @OptIn(UnstableApi::class) override fun onItemClick(position: Int) {
            val viewType: Int = navAdapter.getItemViewType(position)
            if (viewType != NavListAdapter.VIEW_TYPE_SECTION_DIVIDER) {
                if (position < navAdapter.subscriptionOffset) {
                    val tag: String = navAdapter.getFragmentTags()[position] ?:""
                    (activity as MainActivity).loadFragment(tag, null)
                    (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
                } else {
                    val pos: Int = position - navAdapter.subscriptionOffset
                    val clickedItem: NavDrawerData.FeedDrawerItem = flatItemList!![pos]
                    val feedId: Long = clickedItem.feed.id
                    (activity as MainActivity).loadFeedFragmentById(feedId, null)
                    (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
                }
            } else if (UserPreferences.subscriptionsFilter.isEnabled
                    && navAdapter.showSubscriptionList) {
                SubscriptionsFilterDialog().show(childFragmentManager, "filter")
            }
        }

        override fun onItemLongClick(position: Int): Boolean {
            if (position < navAdapter.getFragmentTags().size) {
                DrawerPreferencesDialog.show(context!!) {
                    navAdapter.notifyDataSetChanged()
                    if (UserPreferences.hiddenDrawerItems.contains(getLastNavFragment(requireContext()))) {
                        MainActivityStarter(requireContext())
                            .withFragmentLoaded(UserPreferences.defaultPage)
                            .withDrawerOpen()
                            .start()
                    }
                }
                return true
            } else {
                contextPressedItem = flatItemList!![position - navAdapter.subscriptionOffset]
                return false
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            this@NavDrawerFragment.onCreateContextMenu(menu, v, menuInfo)
        }
    }

    private fun loadData() {
        disposable = Observable.fromCallable {
            val data: NavDrawerData = DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter)
            Pair(data, makeFlatDrawerData(data.items, 0))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Pair<NavDrawerData, List<NavDrawerData.FeedDrawerItem>> ->
                    navDrawerData = result.first
                    flatItemList = result.second
                    navAdapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE // Stays hidden once there is something in the list
                }, { error: Throwable? ->
                    Log.e(TAG, Log.getStackTraceString(error))
                    progressBar.visibility = View.GONE
                })
    }

    private fun makeFlatDrawerData(items: List<NavDrawerData.FeedDrawerItem>, layer: Int): List<NavDrawerData.FeedDrawerItem> {
        val flatItems: MutableList<NavDrawerData.FeedDrawerItem> = ArrayList()
        for (item in items) {
            item.layer = layer
            flatItems.add(item)
        }
        return flatItems
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PREF_LAST_FRAGMENT_TAG == key) {
            navAdapter.notifyDataSetChanged() // Update selection
        }
    }

    companion object {
        @VisibleForTesting
        const val PREF_LAST_FRAGMENT_TAG: String = "prefLastFragmentTag"
        private const val PREF_OPEN_FOLDERS = "prefOpenFolders"

        @VisibleForTesting
        const val PREF_NAME: String = "NavDrawerPrefs"
        const val TAG: String = "NavDrawerFragment"

//        caution: an array in re/values/arrays.xml relates to this
        @JvmField
        @UnstableApi
        val NAV_DRAWER_TAGS: Array<String> = arrayOf(
            SubscriptionFragment.TAG,
            QueueFragment.TAG,
            AllEpisodesFragment.TAG,
            CompletedDownloadsFragment.TAG,
            InboxFragment.TAG,
            PlaybackHistoryFragment.TAG,
//            StatisticsFragment.TAG,
            AddFeedFragment.TAG,
        )

        fun saveLastNavFragment(context: Context, tag: String?) {
            Log.d(TAG, "saveLastNavFragment(tag: $tag)")
            val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val edit: SharedPreferences.Editor = prefs.edit()
            if (tag != null) {
                edit.putString(PREF_LAST_FRAGMENT_TAG, tag)
            } else {
                edit.remove(PREF_LAST_FRAGMENT_TAG)
            }
            edit.apply()
        }

        fun getLastNavFragment(context: Context): String {
            val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastFragment: String = prefs.getString(PREF_LAST_FRAGMENT_TAG, SubscriptionFragment.TAG)?:""
            Log.d(TAG, "getLastNavFragment() -> $lastFragment")
            return lastFragment
        }
    }
}
