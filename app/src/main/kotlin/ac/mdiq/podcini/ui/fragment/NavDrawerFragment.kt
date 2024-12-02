package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeedCount
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment.Companion.ARGUMENT_FEED_ID
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import android.R.attr
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class NavDrawerFragment : Fragment(), OnSharedPreferenceChangeListener {
    val TAG = this::class.simpleName ?: "Anonymous"
    private val feeds = mutableStateListOf<Feed>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        checkHiddenItems()
        getRecentPodcasts()
        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { MainView() } } }

        Logd(TAG, "fragment onCreateView")
        setupDrawerRoundBackground(composeView)
        ViewCompat.setOnApplyWindowInsetsListener(composeView) { view: View, insets: WindowInsetsCompat ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            var navigationBarHeight = 0f
            val activity: Activity? = activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity != null) {
                navigationBarHeight = if (requireActivity().window.navigationBarDividerColor == Color.TRANSPARENT) 0f
                else 1 * resources.displayMetrics.density // Assuming the divider is 1dp in height
            }
            val bottomInset = max(0.0, (bars.bottom - navigationBarHeight).roundToInt().toDouble()).toFloat()
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottomInset.toInt()
            insets
        }
        prefs?.registerOnSharedPreferenceChangeListener(this)
        return composeView
    }

    private fun checkHiddenItems() {
        val hiddenItems = hiddenDrawerItems.map { it.trim() }
        for (nav in navMap.values) nav.show = !hiddenItems.contains(nav.tag)
    }

    private fun getRecentPodcasts() {
        var feeds_ = realm.query(Feed::class).sort("lastPlayed", sortOrder = Sort.DESCENDING).find().toMutableList()
        if (feeds_.size > 5) feeds_ = feeds_.subList(0, 5)
        feeds.clear()
        feeds.addAll(feeds_)
    }

    @Composable
    fun MainView() {
        Column(modifier = Modifier.padding(start = 20.dp, end = 10.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            for (nav in navMap.values) {
                if (nav.show) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    (activity as MainActivity).loadFragment(nav.tag, null)
                    (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                }) {
                    Icon(imageVector = ImageVector.vectorResource(nav.iconRes), tint = textColor, contentDescription = nav.tag, modifier = Modifier.padding(start = 10.dp))
//                    val nametag = if (nav.tag != QueuesFragment.TAG) stringResource(nav.nameRes) else curQueue.name
                    Text(stringResource(nav.nameRes), color = textColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp))
                    Spacer(Modifier.weight(1f))
                    if (nav.count > 0) Text(nav.count.toString(), color = textColor, modifier = Modifier.padding(end = 10.dp))
                }
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            Column {
                for (f in feeds) {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable {
                        val args = Bundle()
                        args.putLong(ARGUMENT_FEED_ID, f.id)
                        (activity as MainActivity).loadFragment(FeedEpisodesFragment.TAG, args)
                        (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                    }) {
                        AsyncImage(model = f.imageUrl, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                            modifier = Modifier.width(40.dp).height(40.dp))
                        Text(f.title?:"No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Currently launching on Google Play, early testers will become pre-IPO members. Click for details", color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clickable(onClick = {
                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/discussions/120")
            }))
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                startActivity(Intent(activity, PreferenceActivity::class.java))
            }) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), tint = textColor, contentDescription = "settings", modifier = Modifier.padding(start = 10.dp))
                Text(stringResource(R.string.settings_label), color = textColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp))
            }
        }
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
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
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
                withContext(Dispatchers.IO) {
                    checkHiddenItems()
                    getRecentPodcasts()
                    getDatasetStats()
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
//        if (PREF_LAST_FRAGMENT_TAG == key) navAdapter.notifyDataSetChanged() // Update selection
    }

    companion object {
        val TAG = NavDrawerFragment::class.simpleName ?: "Anonymous"

        @VisibleForTesting
        const val PREF_LAST_FRAGMENT_TAG: String = "prefLastFragmentTag"
        const val PREF_LAST_FRAGMENT_ARG: String = "prefLastFragmentArg"

        @VisibleForTesting
        const val PREF_NAME: String = "NavDrawerPrefs"
        private var prefs: SharedPreferences? = null
        var feedCount: Int = -1
            get() {
                if (field < 0) field = getFeedCount()
                return field
            }

        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

        class NavItem(val tag: String, val iconRes: Int, val nameRes: Int) {
            var count by mutableIntStateOf(0)
            var show by mutableStateOf(true)
        }

        val navMap: LinkedHashMap<String, NavItem> = linkedMapOf(
            SubscriptionsFragment.TAG to NavItem(SubscriptionsFragment.TAG, R.drawable.ic_subscriptions, R.string.subscriptions_label),
            QueuesFragment.TAG to NavItem(QueuesFragment.TAG, R.drawable.ic_playlist_play, R.string.queue_label),
            EpisodesFragment.TAG to NavItem(EpisodesFragment.TAG, R.drawable.ic_feed, R.string.episodes_label),
            LogsFragment.TAG to NavItem(LogsFragment.TAG, R.drawable.ic_history, R.string.logs_label),
            StatisticsFragment.TAG to NavItem(StatisticsFragment.TAG, R.drawable.ic_chart_box, R.string.statistics_label),
            OnlineSearchFragment.TAG to NavItem(OnlineSearchFragment.TAG, R.drawable.ic_add, R.string.add_feed_label)
        )

        fun saveLastNavFragment(tag: String?, arg: String? = null) {
            Logd(TAG, "saveLastNavFragment(tag: $tag)")
            val edit: SharedPreferences.Editor? = prefs?.edit()
            if (tag != null) {
                edit?.putString(PREF_LAST_FRAGMENT_TAG, tag)
                if (arg != null) edit?.putString(PREF_LAST_FRAGMENT_ARG, arg)
            }
            else edit?.remove(PREF_LAST_FRAGMENT_TAG)
            edit?.apply()
        }

        fun getLastNavFragment(): String = prefs?.getString(PREF_LAST_FRAGMENT_TAG, SubscriptionsFragment.TAG) ?: ""
        fun getLastNavFragmentArg(): String = prefs?.getString(PREF_LAST_FRAGMENT_ARG, "0") ?: ""

        /**
         * Returns data necessary for displaying the navigation drawer. This includes
         * the number of downloaded episodes, the number of episodes in the queue, the number of total episodes, and number of subscriptions
         */
        fun getDatasetStats() {
            Logd(TAG, "getNavDrawerData() called")
            val numItems = getEpisodesCount(unfiltered())
            feedCount = getFeedCount()
            navMap[QueuesFragment.TAG]?.count = realm.query(PlayQueue::class).find().sumOf { it.size()}
            navMap[SubscriptionsFragment.TAG]?.count = feedCount
            navMap[EpisodesFragment.TAG]?.count = numItems
            navMap[LogsFragment.TAG]?.count = realm.query(ShareLog::class).count().find().toInt() +
                    realm.query(SubscriptionLog::class).count().find().toInt() +
                    realm.query(DownloadResult::class).count().find().toInt()
        }
    }
}
