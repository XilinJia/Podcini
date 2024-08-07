package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.NavListBinding
import ac.mdiq.podcini.databinding.NavListitemBinding
import ac.mdiq.podcini.databinding.NavSectionItemBinding
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.storage.algorithms.AutoCleanups
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeedCount
import ac.mdiq.podcini.storage.model.DatasetStats
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.dialog.DrawerPreferencesDialog
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.util.Logd
import android.R.attr
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max

class NavDrawerFragment : Fragment(), OnSharedPreferenceChangeListener {
    val TAG = this::class.simpleName ?: "Anonymous"

    private var _binding: NavListBinding? = null
    private val binding get() = _binding!!

    private var datasetStats: DatasetStats? = null
    private lateinit var navAdapter: NavListAdapter

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

        val navList = binding.navRecycler
        navAdapter = NavListAdapter()
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

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        prefs!!.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    override fun onResume() {
        Logd(TAG, "onResume() called")
        super.onResume()
        loadData()
    }

    fun loadData() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { getDatasetStats() }
                withContext(Dispatchers.Main) {
                    datasetStats = result
                    navAdapter.notifyDataSetChanged()
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PREF_LAST_FRAGMENT_TAG == key) navAdapter.notifyDataSetChanged() // Update selection
    }

    @OptIn(UnstableApi::class)
    private inner class NavListAdapter: RecyclerView.Adapter<Holder>(), OnSharedPreferenceChangeListener {
        private val fragmentTags: MutableList<String?> = ArrayList()
        private val titles: Array<String> = requireContext().resources.getStringArray(R.array.nav_drawer_titles)
        val subscriptionOffset: Int
            get() = if (fragmentTags.size > 0) fragmentTags.size + 1 else 0

        init {
            loadItems()
            appPrefs.registerOnSharedPreferenceChangeListener(this@NavListAdapter)
        }
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (UserPreferences.Prefs.prefHiddenDrawerItems.name == key) loadItems()
        }
        @OptIn(UnstableApi::class) private fun loadItems() {
            val newTags: MutableList<String?> = ArrayList(listOf(*NAV_DRAWER_TAGS))
            val hiddenFragments = hiddenDrawerItems
            newTags.removeAll(hiddenFragments.map { it.trim() })
            fragmentTags.clear()
            fragmentTags.addAll(newTags)
            notifyDataSetChanged()
        }
        fun getLabel(tag: String?): String {
            val index = ArrayUtils.indexOf(NAV_DRAWER_TAGS, tag)
            return titles[index]
        }
        @UnstableApi @DrawableRes
        private fun getDrawable(tag: String?): Int {
            return when (tag) {
                QueuesFragment.TAG -> R.drawable.ic_playlist_play
                AllEpisodesFragment.TAG -> R.drawable.ic_feed
                DownloadsFragment.TAG -> R.drawable.ic_download
                HistoryFragment.TAG -> R.drawable.ic_history
                SubscriptionsFragment.TAG -> R.drawable.ic_subscriptions
                StatisticsFragment.TAG -> R.drawable.ic_chart_box
                AddFeedFragment.TAG -> R.drawable.ic_add
                else -> 0
            }
        }
        fun getFragmentTags(): List<String?> {
            return fragmentTags
        }
        override fun getItemCount(): Int {
            return subscriptionOffset
        }
        override fun getItemId(position: Int): Long {
            val viewType = getItemViewType(position)
            return when (viewType) {
                VIEW_TYPE_NAV -> (-abs(fragmentTags[position].hashCode().toLong().toDouble()) - 1).toLong() // Folder IDs are >0
                else -> 0
            }
        }
        override fun getItemViewType(position: Int): Int {
            return when {
                0 <= position && position < fragmentTags.size -> VIEW_TYPE_NAV
                position < subscriptionOffset -> VIEW_TYPE_SECTION_DIVIDER
                else -> 0
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val inflater = LayoutInflater.from(activity)
            return when (viewType) {
                VIEW_TYPE_NAV -> NavHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
                else -> DividerHolder(inflater.inflate(R.layout.nav_section_item, parent, false))
            }
        }
        @UnstableApi override fun onBindViewHolder(holder: Holder, position: Int) {
            val viewType = getItemViewType(position)

            holder.itemView.setOnCreateContextMenuListener(null)
            when (viewType) {
                VIEW_TYPE_NAV -> bindNavView(getLabel(fragmentTags[position]), position, holder as NavHolder)
                else -> bindSectionDivider(holder as DividerHolder)
            }
            if (viewType != VIEW_TYPE_SECTION_DIVIDER) {
                holder.itemView.isSelected = isSelected(position)
                holder.itemView.setOnClickListener { onItemClick(position) }
                holder.itemView.setOnLongClickListener { onItemLongClick(position) }
                holder.itemView.setOnTouchListener { _: View?, e: MotionEvent ->
                    if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                        onItemLongClick(position)
                        return@setOnTouchListener false
                    }
                    false
                }
            }
        }
        fun isSelected(position: Int): Boolean {
            val lastNavFragment = getLastNavFragment()
            when {
                position < navAdapter.subscriptionOffset -> return navAdapter.getFragmentTags()[position] == lastNavFragment
                // last fragment was not a list, but a feed
                StringUtils.isNumeric(lastNavFragment) -> {
                    Logd(TAG, "not implemented: last fragment was a feed $lastNavFragment")
                }
            }
            return false
        }
        @OptIn(UnstableApi::class)  fun onItemClick(position: Int) {
            if (position < navAdapter.subscriptionOffset) {
                val tag: String = navAdapter.getFragmentTags()[position] ?:""
                (activity as MainActivity).loadFragment(tag, null)
                (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        fun onItemLongClick(position: Int): Boolean {
            if (position < navAdapter.getFragmentTags().size) {
                DrawerPreferencesDialog.show(context!!) {
                    navAdapter.notifyDataSetChanged()
                    if (hiddenDrawerItems.contains(getLastNavFragment())) {
                        MainActivityStarter(requireContext())
                            .withFragmentLoaded(UserPreferences.defaultPage)
                            .withDrawerOpen()
                            .start()
                    }
                }
                return true
            } else return false
        }
        @UnstableApi private fun bindNavView(title: String, position: Int, holder: NavHolder) {
            val context = activity ?: return
            holder.title.text = title
            // reset for re-use
            holder.count.visibility = View.GONE
            holder.count.setOnClickListener(null)
            holder.count.isClickable = false

            val tag = fragmentTags[position]
            when (tag) {
                SubscriptionsFragment.TAG -> {
                    val sum = datasetStats?.numFeeds ?: 0
                    if (sum > 0) {
                        holder.count.text = NumberFormat.getInstance().format(sum.toLong())
                        holder.count.visibility = View.VISIBLE
                    }
                }
                QueuesFragment.TAG -> {
                    val queueSize = datasetStats?.queueSize ?: 0
                    if (queueSize > 0) {
                        holder.count.text = NumberFormat.getInstance().format(queueSize.toLong())
                        holder.count.visibility = View.VISIBLE
                    }
                }
                AllEpisodesFragment.TAG -> {
                    val numEpisodes = datasetStats?.numEpisodes ?: 0
                    if (numEpisodes > 0) {
                        holder.count.text = NumberFormat.getInstance().format(numEpisodes.toLong())
                        holder.count.visibility = View.VISIBLE
                    }
                }
                DownloadsFragment.TAG -> {
                    val epCacheSize = episodeCacheSize
                    // don't count episodes that can be reclaimed
                    val spaceUsed = ((datasetStats?.numDownloaded ?: 0) - (datasetStats?.numReclaimables ?: 0))
                    holder.count.text = NumberFormat.getInstance().format(spaceUsed.toLong())
                    holder.count.visibility = View.VISIBLE
                    if (epCacheSize in 1..spaceUsed) {
                        holder.count.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_disc_alert, 0)
                        holder.count.visibility = View.VISIBLE
                        holder.count.setOnClickListener {
                            MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.episode_cache_full_title)
                                .setMessage(R.string.episode_cache_full_message)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNeutralButton(R.string.open_autodownload_settings) { _: DialogInterface?, _: Int ->
                                    val intent = Intent(context, PreferenceActivity::class.java)
                                    intent.putExtra(PreferenceActivity.OPEN_AUTO_DOWNLOAD_SETTINGS, true)
                                    context.startActivity(intent)
                                }
                                .show()
                        }
                    }
                }
            }
//        Logd("NavListAdapter", "bindNavView getting drawable for: ${fragmentTags[position]}")
            holder.image.setImageResource(getDrawable(fragmentTags[position]))
        }
        private fun bindSectionDivider(holder: DividerHolder) {
            holder.itemView.isEnabled = false
            holder.feedsFilteredMsg.visibility = View.GONE
        }
    }

    open class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class DividerHolder(itemView: View) : Holder(itemView) {
        val binding = NavSectionItemBinding.bind(itemView)
        val feedsFilteredMsg: LinearLayout = binding.navFeedsFilteredMessage
    }

    class NavHolder(itemView: View) : Holder(itemView) {
        val binding = NavListitemBinding.bind(itemView)
        val image: ImageView = binding.imgvCover
        val title: TextView = binding.txtvTitle
        val count: TextView = binding.txtvCount
    }

    companion object {
        val TAG = NavDrawerFragment::class.simpleName ?: "Anonymous"

        @VisibleForTesting
        const val PREF_LAST_FRAGMENT_TAG: String = "prefLastFragmentTag"
        const val VIEW_TYPE_NAV: Int = 0
        const val VIEW_TYPE_SECTION_DIVIDER: Int = 1

        @VisibleForTesting
        const val PREF_NAME: String = "NavDrawerPrefs"
        var prefs: SharedPreferences? = null

        var feedCount: Int = 0

        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

//        caution: an array in re/values/arrays.xml relates to this
        @JvmField
        @UnstableApi
        val NAV_DRAWER_TAGS: Array<String> = arrayOf(
            SubscriptionsFragment.TAG,
            QueuesFragment.TAG,
            AllEpisodesFragment.TAG,
            DownloadsFragment.TAG,
            HistoryFragment.TAG,
            StatisticsFragment.TAG,
            AddFeedFragment.TAG,
        )

        fun saveLastNavFragment(tag: String?) {
            Logd(TAG, "saveLastNavFragment(tag: $tag)")
            val edit: SharedPreferences.Editor = prefs!!.edit()
            if (tag != null) edit.putString(PREF_LAST_FRAGMENT_TAG, tag)
            else edit.remove(PREF_LAST_FRAGMENT_TAG)
            edit.apply()
        }

        fun getLastNavFragment(): String {
            val lastFragment: String = prefs!!.getString(PREF_LAST_FRAGMENT_TAG, SubscriptionsFragment.TAG)?:""
            return lastFragment
        }

        /**
         * Returns data necessary for displaying the navigation drawer. This includes
         * the number of downloaded episodes, the number of episodes in the queue, the number of total episodes, and number of subscriptions
         */
        fun getDatasetStats(): DatasetStats {
            Logd(TAG, "getNavDrawerData() called")
            val numDownloadedItems = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
            val numItems = getEpisodesCount(unfiltered())
            feedCount = getFeedCount()
            while (curQueue.name.isEmpty()) runBlocking { delay(100) }
            val queueSize = curQueue.episodeIds.size
            Logd(TAG, "getDatasetStats: queueSize: $queueSize")
            return DatasetStats(queueSize, numDownloadedItems, AutoCleanups.build().getReclaimableItems(), numItems, feedCount)
        }
    }
}
