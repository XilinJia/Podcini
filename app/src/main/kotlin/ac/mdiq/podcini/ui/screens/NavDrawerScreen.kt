package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeedCount
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.closeDrawer
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NavDrawerScreen"

private lateinit var prefs: SharedPreferences

class NavDrawerVM(val context: Context, val lcScope: CoroutineScope) {
    internal val feeds = mutableStateListOf<Feed>()

    internal fun getRecentPodcasts() {
        var feeds_ = realm.query(Feed::class).sort("lastPlayed", sortOrder = Sort.DESCENDING).limit(5).find()
        feeds.clear()
        feeds.addAll(feeds_)
    }

    init {
        prefs = context.getSharedPreferences("NavDrawerPrefs", Context.MODE_PRIVATE)
    }

    fun loadData() {
        lcScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getRecentPodcasts()
                    getDatasetStats()
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }
}

@Composable
fun NavDrawerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { NavDrawerVM(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
                    vm.getRecentPodcasts()
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_START")
                    vm.loadData()
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val drawerWidth = screenWidth * 0.7f

    Box(Modifier.width(drawerWidth).fillMaxHeight()) {
        Scaffold { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                val textColor = MaterialTheme.colorScheme.onSurface
                for (nav in navMap.entries) {
                    if (nav.value.show) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        mainNavController.navigate(nav.key) { popUpTo(nav.key) { inclusive = true } }
                        closeDrawer()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(nav.value.iconRes), tint = textColor, contentDescription = nav.key, modifier = Modifier.padding(start = 10.dp))
//                    val nametag = if (nav.tag != QueuesFragment.TAG) stringResource(nav.nameRes) else curQueue.name
                        Text(stringResource(nav.value.nameRes), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                        Spacer(Modifier.weight(1f))
                        if (nav.value.count > 0) Text(nav.value.count.toString(), color = textColor, modifier = Modifier.padding(end = 10.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                Column {
                    for (f in vm.feeds) {
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable {
                            feedOnDisplay = f
                            mainNavController.navigate(Screens.FeedEpisodes.name) { popUpTo(Screens.FeedEpisodes.name) { inclusive = true } }
                            closeDrawer()
//                    (context as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                        }) {
                            AsyncImage(model = f.imageUrl, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                                modifier = Modifier.width(40.dp).height(40.dp))
                            Text(f.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
//            Text("Formal listing on Google Play has been approved - many thanks to all for the kind support!", color = textColor,
//                modifier = Modifier.clickable(onClick = {}))
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    context.startActivity(Intent(context, PreferenceActivity::class.java))
                    closeDrawer()
                }) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), tint = textColor, contentDescription = "settings", modifier = Modifier.padding(start = 10.dp))
                    Text(stringResource(R.string.settings_label), color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 20.dp))
                }
            }
        }
    }
}

const val PREF_LAST_FRAGMENT_TAG: String = "prefLastFragmentTag"
const val PREF_LAST_FRAGMENT_ARG: String = "prefLastFragmentArg"

var feedCount: Int = -1
    get() {
        if (field < 0) field = getFeedCount()
        return field
    }

class NavItem(val iconRes: Int, val nameRes: Int) {
    var count by mutableIntStateOf(0)
    var show by mutableStateOf(true)
}

val navMap: LinkedHashMap<String, NavItem> = linkedMapOf(
    Screens.Subscriptions.name to NavItem(R.drawable.ic_subscriptions, R.string.subscriptions_label),
    Screens.Queues.name to NavItem(R.drawable.ic_playlist_play, R.string.queue_label),
    Screens.Episodes.name to NavItem(R.drawable.ic_feed, R.string.episodes_label),
    Screens.Logs.name to NavItem(R.drawable.ic_history, R.string.logs_label),
    Screens.Statistics.name to NavItem(R.drawable.ic_chart_box, R.string.statistics_label),
    Screens.OnlineSearch.name to NavItem(R.drawable.ic_add, R.string.add_feed_label)
)

fun saveLastNavScreen(tag: String?, arg: String? = null) {
    Logd(TAG, "saveLastNavScreen(tag: $tag)")
    val edit: SharedPreferences.Editor? = prefs.edit()
    if (tag != null) {
        edit?.putString(PREF_LAST_FRAGMENT_TAG, tag)
        if (arg != null) edit?.putString(PREF_LAST_FRAGMENT_ARG, arg)
    }
    else edit?.remove(PREF_LAST_FRAGMENT_TAG)
    edit?.apply()
}

fun getLastNavScreen(): String = prefs.getString(PREF_LAST_FRAGMENT_TAG, "") ?: ""
fun getLastNavScreenArg(): String = prefs.getString(PREF_LAST_FRAGMENT_ARG, "0") ?: ""

/**
 * Returns data necessary for displaying the navigation drawer. This includes
 * the number of downloaded episodes, the number of episodes in the queue, the number of total episodes, and number of subscriptions
 */
fun getDatasetStats() {
    Logd(TAG, "getNavDrawerData() called")
    val numItems = getEpisodesCount(unfiltered())
    feedCount = getFeedCount()
    navMap[Screens.Queues.name]?.count = realm.query(PlayQueue::class).find().sumOf { it.size()}
    navMap[Screens.Subscriptions.name]?.count = feedCount
    navMap[Screens.Episodes.name]?.count = numItems
    navMap[Screens.Logs.name]?.count = realm.query(ShareLog::class).count().find().toInt() +
            realm.query(SubscriptionLog::class).count().find().toInt() +
            realm.query(DownloadResult::class).count().find().toInt()
}


