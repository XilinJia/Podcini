package ac.mdiq.podcini.ui.fragment

//import ac.mdiq.podcini.ui.home.HomeFragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.NavListBinding
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.NavDrawerData
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.appstartintent.MainActivityStarter
import ac.mdiq.podcini.ui.adapter.NavListAdapter
import ac.mdiq.podcini.ui.dialog.DrawerPreferencesDialog
import ac.mdiq.podcini.ui.dialog.SubscriptionsFilterDialog
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.R.attr
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import kotlin.math.max

class NavDrawerFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var _binding: NavListBinding? = null
    private val binding get() = _binding!!

    private var navDrawerData: NavDrawerData? = null
    private var flatItemList: List<NavDrawerData.FeedDrawerItem>? = null
//    val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var navAdapter: NavListAdapter

    private var openFolders: MutableSet<String> = HashSet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = NavListBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        setupDrawerRoundBackground(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view: View, insets: WindowInsetsCompat ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            var navigationBarHeight = 0f
            val activity: Activity? = activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity != null) {
                navigationBarHeight = if (requireActivity().window.navigationBarDividerColor == Color.TRANSPARENT) 0f
                else 1 * resources.displayMetrics.density // Assuming the divider is 1dp in height
            }
            val bottomInset = max(0.0, Math.round(bars.bottom - navigationBarHeight).toDouble()).toFloat()
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottomInset.toInt()
            insets
        }

//        val preferences: SharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        TODO: what is this?
        openFolders = HashSet(prefs!!.getStringSet(PREF_OPEN_FOLDERS, HashSet<String>())!!) // Must not modify

        val navList = binding.navRecycler
        navAdapter = NavListAdapter(itemAccess, requireActivity())
        navAdapter.setHasStableIds(true)
        navList.adapter = navAdapter
        navList.layoutManager = LinearLayoutManager(context)

        binding.navSettings.setOnClickListener {
            startActivity(Intent(activity, PreferenceActivity::class.java))
        }

        prefs!!.registerOnSharedPreferenceChangeListener(this)
        return binding.root
    }

    private fun setupDrawerRoundBackground(root: View) {
        // Akin to this logic:
        //   https://github.com/material-components/material-components-android/blob/8938da8c/lib/java/com/google/android/material/navigation/NavigationView.java#L405
        val shapeBuilder: ShapeAppearanceModel.Builder = ShapeAppearanceModel.builder()
        val cornerSize = resources.getDimension(R.dimen.drawer_corner_size)
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        if (isRtl) shapeBuilder.setTopLeftCornerSize(cornerSize).setBottomLeftCornerSize(cornerSize)
        else shapeBuilder.setTopRightCornerSize(cornerSize).setBottomRightCornerSize(cornerSize)

        val drawable = MaterialShapeDrawable(shapeBuilder.build())
        val themeColor = ThemeUtils.getColorFromAttr(root.context, attr.colorBackground)
        drawable.fillColor = ColorStateList.valueOf(themeColor)
        root.background = drawable
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        procFlowEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        
//        scope.cancel()
        prefs!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: $event")
                when (event) {
                    is FlowEvent.UnreadItemsUpdateEvent, is FlowEvent.FeedListUpdateEvent -> loadData()
                    is FlowEvent.QueueEvent -> onQueueChanged(event)
                    else -> {}
                }
            }
        }
    }

    fun onQueueChanged(event: FlowEvent.QueueEvent) {
        Logd(TAG, "onQueueChanged($event)")
        // we are only interested in the number of queue items, not download status or position
        if (event.action == FlowEvent.QueueEvent.Action.DELETED_MEDIA
                || event.action == FlowEvent.QueueEvent.Action.SORTED
                || event.action == FlowEvent.QueueEvent.Action.MOVED) return
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private val itemAccess: NavListAdapter.ItemAccess = object : NavListAdapter.ItemAccess {
        override val count: Int
            get() =  flatItemList?.size ?: 0

        override fun getItem(position: Int): NavDrawerData.FeedDrawerItem? {
            return if (flatItemList != null && 0 <= position && position < flatItemList!!.size) flatItemList!![position]
            else null
        }

        override fun isSelected(position: Int): Boolean {
            val lastNavFragment = getLastNavFragment(requireContext())
            when {
                position < navAdapter.subscriptionOffset -> return navAdapter.getFragmentTags()[position] == lastNavFragment
                StringUtils.isNumeric(lastNavFragment) -> { // last fragment was not a list, but a feed
                    val feedId = lastNavFragment.toLong()
                    if (navDrawerData != null) {
                        val itemToCheck: NavDrawerData.FeedDrawerItem = flatItemList!![position - navAdapter.subscriptionOffset]
                        // When the same feed is displayed multiple times, it should be highlighted multiple times.
                        return itemToCheck.feed.id == feedId
                    }
                }
            }
            return false
        }

        override val queueSize: Int
            get() = navDrawerData?.queueSize ?: 0

        override val numberOfNewItems: Int
            get() = navDrawerData?.numNewItems ?: 0

        override val numberOfItems: Int
            get() = navDrawerData?.numItems ?: 0

        override val numberOfDownloadedItems: Int
            get() = navDrawerData?.numDownloadedItems ?: 0

        override val reclaimableItems: Int
            get() = navDrawerData?.reclaimableSpace ?: 0

        override val numberOfFeeds: Int
            get() = navDrawerData?.numFeeds ?: 0

        override val feedCounterSum: Int
            get() {
                if (navDrawerData == null) return 0

                var sum = 0
                for (counter in navDrawerData!!.feedCounters.values) {
                    sum += counter
                }
                return sum
            }

        @OptIn(UnstableApi::class) override fun onItemClick(position: Int) {
            val viewType: Int = navAdapter.getItemViewType(position)
            when {
                viewType != NavListAdapter.VIEW_TYPE_SECTION_DIVIDER -> {
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
                }
                UserPreferences.subscriptionsFilter.isEnabled && navAdapter.showSubscriptionList -> {
                    SubscriptionsFilterDialog().show(childFragmentManager, "filter")
                }
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
//                contextPressedItem = flatItemList!![position - navAdapter.subscriptionOffset]
                return false
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            this@NavDrawerFragment.onCreateContextMenu(menu, v, menuInfo)
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val data: NavDrawerData = DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter)
                    Pair(data, makeFlatDrawerData(data.items, 0))
                }
                withContext(Dispatchers.Main) {
                    navDrawerData = result.first
                    flatItemList = result.second
                    navAdapter.notifyDataSetChanged()
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
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
        if (PREF_LAST_FRAGMENT_TAG == key) navAdapter.notifyDataSetChanged() // Update selection
    }

    companion object {
        @VisibleForTesting
        const val PREF_LAST_FRAGMENT_TAG: String = "prefLastFragmentTag"
        private const val PREF_OPEN_FOLDERS = "prefOpenFolders"

        @VisibleForTesting
        const val PREF_NAME: String = "NavDrawerPrefs"
        const val TAG: String = "NavDrawerFragment"
        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

//        caution: an array in re/values/arrays.xml relates to this
        @JvmField
        @UnstableApi
        val NAV_DRAWER_TAGS: Array<String> = arrayOf(
            SubscriptionFragment.TAG,
            QueueFragment.TAG,
            AllEpisodesFragment.TAG,
            DownloadsFragment.TAG,
            HistoryFragment.TAG,
            StatisticsFragment.TAG,
            AddFeedFragment.TAG,
        )

        fun saveLastNavFragment(context: Context, tag: String?) {
            Logd(TAG, "saveLastNavFragment(tag: $tag)")
//            val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val edit: SharedPreferences.Editor = prefs!!.edit()
            if (tag != null) edit.putString(PREF_LAST_FRAGMENT_TAG, tag)
            else edit.remove(PREF_LAST_FRAGMENT_TAG)

            edit.apply()
        }

        fun getLastNavFragment(context: Context): String {
//            val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastFragment: String = prefs!!.getString(PREF_LAST_FRAGMENT_TAG, SubscriptionFragment.TAG)?:""
//            Logd(TAG, "getLastNavFragment() -> $lastFragment")
            return lastFragment
        }
    }
}
