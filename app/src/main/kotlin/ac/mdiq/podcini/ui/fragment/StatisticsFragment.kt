package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.update
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringShort
import ac.mdiq.podcini.storage.utils.DurationConverter.shortLocalizedDuration
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.DatesFilterDialog
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.max
import kotlin.math.min

class StatisticsFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    val prefs: SharedPreferences by lazy { requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private var _binding: ComposeFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var toolbar: MaterialToolbar

    private var includeMarkedAsPlayed by mutableStateOf(false)
    private var statisticsState by mutableIntStateOf(0)

    private val selectedTabIndex = mutableIntStateOf(0)
    lateinit var statsResult: StatisticsResult

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)
        _binding = ComposeFragmentBinding.inflate(inflater)
        toolbar = binding.toolbar
        toolbar.title = getString(R.string.statistics_label)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.inflateMenu(R.menu.statistics)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        (activity as MainActivity).setupToolbarToggle(toolbar, false)
        binding.mainView.setContent {
            CustomTheme(requireContext()) {
                val tabTitles = listOf(R.string.subscriptions_label, R.string.months_statistics_label, R.string.downloads_label)
                Column {
                    TabRow(modifier = Modifier.fillMaxWidth(), selectedTabIndex = selectedTabIndex.value, divider = {}, indicator = { tabPositions ->
                        Box(modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex.value]).height(4.dp).background(Color.Blue))
                    }) {
                        tabTitles.forEachIndexed { index, titleRes ->
                            Tab(text = { Text(stringResource(titleRes)) }, selected = selectedTabIndex.value == index, onClick = { selectedTabIndex.value = index })
                        }
                    }
                    when (selectedTabIndex.value) {
                        0 -> PlayedTime()
                        1 -> MonthlyStats()
                        2 -> DownloadStats()
                    }
                }
            }
        }
        return binding.root
    }

    @Composable
    fun PlayedTime() {
        val context = LocalContext.current
        lateinit var chartData: LineChartData
        var timeSpentSum =  0L
        var timeFilterFrom = 0L
        var timeFilterTo = Long.MAX_VALUE
        var timePlayedToday = 0L
        var timeSpentToday = 0L

        fun setTimeFilter(includeMarkedAsPlayed_: Boolean, timeFilterFrom_: Long, timeFilterTo_: Long) {
            includeMarkedAsPlayed = includeMarkedAsPlayed_
            timeFilterFrom = timeFilterFrom_
            timeFilterTo = timeFilterTo_
        }
        fun loadStatistics() {
            includeMarkedAsPlayed = prefs.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
            val statsToday = getStatistics(includeMarkedAsPlayed, LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), Long.MAX_VALUE)
            for (item in statsToday.statsItems) {
                timePlayedToday += item.timePlayed
                timeSpentToday += item.timeSpent
            }
            timeFilterFrom = prefs.getLong(PREF_FILTER_FROM, 0)
            timeFilterTo = prefs.getLong(PREF_FILTER_TO, Long.MAX_VALUE)
            try {
                statsResult = getStatistics(includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
                statsResult.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem -> item2.timePlayed.compareTo(item1.timePlayed) }
                val dataValues = MutableList(statsResult.statsItems.size){0f}
                for (i in statsResult.statsItems.indices) {
                    val item = statsResult.statsItems[i]
                    dataValues[i] = item.timePlayed.toFloat()
                    timeSpentSum += item.timeSpent
                }
                chartData = LineChartData(dataValues)
                // When "from" is "today", set it to today
                setTimeFilter(includeMarkedAsPlayed,
                    max(min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), statsResult.oldestDate.toDouble()).toLong(),
                    min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
            } catch (error: Throwable) { Log.e(TAG, Log.getStackTraceString(error)) }
        }
        refreshToolbarState()
        if (statisticsState >= 0) loadStatistics()

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.statistics_today), color = MaterialTheme.colorScheme.onSurface)
            Row {
                Text(stringResource(R.string.duration) + ": " + getDurationStringShort(timePlayedToday.toInt(), true), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(20.dp))
                Text( stringResource(R.string.spent) + ": " + getDurationStringShort(timeSpentToday.toInt(), true), color = MaterialTheme.colorScheme.onSurface)
            }
            val headerCaption = if (includeMarkedAsPlayed) stringResource(R.string.statistics_counting_total)
            else {
                if (timeFilterFrom != 0L || timeFilterTo != Long.MAX_VALUE) {
                    val skeleton = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM yyyy")
                    val dateFormat = SimpleDateFormat(skeleton, Locale.getDefault())
                    val dateFrom = dateFormat.format(Date(timeFilterFrom))
                    // FilterTo is first day of next month => Subtract one day
                    val dateTo = dateFormat.format(Date(timeFilterTo - 24L * 3600000L))
                    stringResource(R.string.statistics_counting_range, dateFrom, dateTo)
                } else stringResource(R.string.statistics_counting_total)
            }
            Text(headerCaption, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp))
            Row {
                Text(stringResource(R.string.duration) + ": " + shortLocalizedDuration(context, chartData.sum.toLong()), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(20.dp))
                Text( stringResource(R.string.spent) + ": " + shortLocalizedDuration(context, timeSpentSum), color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalLineChart(chartData)
            StatsList(statsResult, chartData) { item ->
                context.getString(R.string.duration) + ": " + shortLocalizedDuration(context, item.timePlayed) + " \t " + context.getString(R.string.spent) + ": " + shortLocalizedDuration(context, item.timeSpent)
            }
        }
    }

    @Composable
    fun MonthlyStats() {
        lateinit var monthlyStats: List<MonthlyStatisticsItem>
        var monthlyMaxDataValue = 1f

        fun loadMonthlyStatistics() {
            try {
                includeMarkedAsPlayed = prefs.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
                val months: MutableList<MonthlyStatisticsItem> = ArrayList()
                val medias = realm.query(EpisodeMedia::class).query("lastPlayedTime > 0").find()
                val groupdMedias = medias.groupBy {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = it.lastPlayedTime
                    "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}"
                }
                val orderedGroupedItems = groupdMedias.toList().sortedBy {
                    val (key, _) = it
                    val year = key.substringBefore("-").toInt()
                    val month = key.substringAfter("-").toInt()
                    year * 12 + month
                }.toMap()
                for (key in orderedGroupedItems.keys) {
                    val medias_ = orderedGroupedItems[key] ?: continue
                    val mItem = MonthlyStatisticsItem()
                    mItem.year = key.substringBefore("-").toInt()
                    mItem.month = key.substringAfter("-").toInt()
                    var dur = 0L
                    var spent = 0L
                    for (m in medias_) {
                        dur += if (m.playedDuration > 0) m.playedDuration
                        else {
                            if (includeMarkedAsPlayed) {
                                if (m.playbackCompletionTime > 0 || (m.episodeOrFetch()?.playState ?: -10) >= PlayState.SKIPPED.code) m.duration
                                else if (m.position > 0) m.position else 0
                            } else m.position
                        }
                        spent += m.timeSpent
                    }
                    mItem.timePlayed = dur
                    mItem.timeSpent = spent
                    months.add(mItem)
                }
                monthlyStats = months
                for (item in monthlyStats) monthlyMaxDataValue = max(monthlyMaxDataValue.toDouble(), item.timePlayed.toDouble()).toFloat()
                Logd(TAG, "maxDataValue: $monthlyMaxDataValue")
            } catch (error: Throwable) { Log.e(TAG, Log.getStackTraceString(error)) }
        }
        @Composable
        fun BarChart() {
            val barWidth = 40f
            val spaceBetweenBars = 16f
            Canvas(modifier = Modifier.width((monthlyStats.size * (barWidth + spaceBetweenBars)).dp).height(150.dp)) {
//                val canvasWidth = size.width
                val canvasHeight = size.height
                for (index in monthlyStats.indices) {
                    val barHeight = (monthlyStats[index].timePlayed / monthlyMaxDataValue) * canvasHeight // Normalize height
                    Logd(TAG, "index: $index barHeight: $barHeight")
                    val xOffset = spaceBetweenBars + index * (barWidth + spaceBetweenBars) // Calculate x position
                    drawRect(color = Color.Cyan,
                        topLeft = androidx.compose.ui.geometry.Offset(xOffset, canvasHeight - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }
        }
        @Composable
        fun MonthList() {
            val lazyListState = rememberLazyListState()
            val textColor = MaterialTheme.colorScheme.onSurface
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(monthlyStats) { index, _ ->
                    Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        Column {
                            val monthString = String.format(Locale.getDefault(), "%d-%d", monthlyStats[index].year, monthlyStats[index].month)
                            Text(monthString, color = textColor, style = MaterialTheme.typography.bodyLarge.merge())
                            val hoursString = stringResource(R.string.duration) + ": " + String.format(Locale.getDefault(), "%.1f ", monthlyStats[index].timePlayed / 3600000.0f) + stringResource(R.string.time_hours) +
                                    " \t " + stringResource(R.string.spent) + ": " + String.format(Locale.getDefault(), "%.1f ", monthlyStats[index].timeSpent / 3600000.0f) + stringResource(R.string.time_hours)
                            Text(hoursString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        refreshToolbarState()
        if (statisticsState >= 0) loadMonthlyStatistics()
        Column {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp)) { BarChart() }
            Spacer(Modifier.height(20.dp))
            MonthList()
        }
    }

    @Composable
    fun DownloadStats() {
        val context = LocalContext.current
        lateinit var downloadstatsData: StatisticsResult
        lateinit var downloadChartData: LineChartData

        fun loadDownloadStatistics() {
            downloadstatsData = getStatistics(false, 0, Long.MAX_VALUE, forDL = true)
            downloadstatsData.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem -> item2.totalDownloadSize.compareTo(item1.totalDownloadSize) }
            val dataValues = MutableList(downloadstatsData.statsItems.size) { 0f }
            for (i in downloadstatsData.statsItems.indices) {
                val item = downloadstatsData.statsItems[i]
                dataValues[i] = item.totalDownloadSize.toFloat()
            }
            downloadChartData = LineChartData(dataValues)
        }

        refreshToolbarState()
        loadDownloadStatistics()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.total_size_downloaded_podcasts), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            Text(Formatter.formatShortFileSize(context, downloadChartData.sum.toLong()), color = MaterialTheme.colorScheme.onSurface)
            HorizontalLineChart(downloadChartData)
            StatsList(downloadstatsData, downloadChartData) { item ->
                ("${Formatter.formatShortFileSize(context, item.totalDownloadSize)} • "
                        + String.format(Locale.getDefault(), "%d%s", item.episodesDownloadCount, context.getString(R.string.episodes_suffix)))
            }
        }
    }

    @Composable
    fun HorizontalLineChart(lineChartData: LineChartData) {
        val data = lineChartData.values
        val total = data.sum()
        Canvas(modifier = Modifier.fillMaxWidth().height(50.dp).padding(start = 20.dp, end = 20.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val lineY = canvasHeight / 2
            var startX = 0f
            for (index in data.indices) {
                val segmentWidth = (data[index] / total) * canvasWidth
//                    Logd(TAG, "index: $index segmentWidth: $segmentWidth")
                drawRect(color = lineChartData.getComposeColorOfItem(index),
                    topLeft = androidx.compose.ui.geometry.Offset(startX, lineY - 10),
                    size = androidx.compose.ui.geometry.Size(segmentWidth, 20f))
                startX += segmentWidth
            }
        }
    }

    @Composable
    fun StatsList(statisticsData: StatisticsResult, lineChartData: LineChartData, infoCB: (StatisticsItem)->String) {
        val lazyListState = rememberLazyListState()
        val context = LocalContext.current
        var showFeedStats by remember { mutableStateOf(false) }
        var feedId by remember { mutableLongStateOf(0L) }
        var feedTitle by remember { mutableStateOf("") }
        if (showFeedStats) FeedStatisticsDialog(feedTitle, feedId) { showFeedStats = false }
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(statisticsData.statsItems, key = { _, item -> item.feed.id }) { index, item ->
                Row(Modifier.background(MaterialTheme.colorScheme.surface).fillMaxWidth().clickable(onClick = {
                    Logd(SubscriptionsFragment.TAG, "icon clicked!")
                    feedId = item.feed.id
                    feedTitle = item.feed.title ?: "No title"
                    showFeedStats = true
                })) {
                    val imgLoc = remember(item) { item.feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(40.dp).height(40.dp).padding(end = 5.dp)
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column {
                        Text(item.feed.title?:"No title", color = textColor, style = MaterialTheme.typography.bodyLarge.merge())
                        Row {
                            val chipColor = lineChartData.getComposeColorOfItem(index)
                            Text("⬤", style = MaterialTheme.typography.bodyMedium.merge(), color = chipColor)
                            Text(infoCB(item), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        toolbar.setOnMenuItemClickListener(null)
        super.onDestroyView()
    }

    private fun refreshToolbarState() {
        when (selectedTabIndex.value) {
            0 -> {
                toolbar.menu?.findItem(R.id.statistics_reset)?.isVisible = true
                toolbar.menu?.findItem(R.id.statistics_filter)?.isVisible = true
            }
            1 -> {
                toolbar.menu?.findItem(R.id.statistics_reset)?.isVisible = true
                toolbar.menu?.findItem(R.id.statistics_filter)?.isVisible = false
            }
            else -> {
                toolbar.menu?.findItem(R.id.statistics_reset)?.isVisible = false
                toolbar.menu?.findItem(R.id.statistics_filter)?.isVisible = false
            }
        }
    }

     override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.statistics_reset -> {
                confirmResetStatistics()
                return true
            }
            R.id.statistics_filter -> {
                val dialog = object: DatesFilterDialog(requireContext(), statsResult.oldestDate) {
                    override fun initParams() {
//                        prefs = prefs
                        includeMarkedAsPlayed = prefs.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
                        timeFilterFrom = prefs.getLong(PREF_FILTER_FROM, 0)
                        timeFilterTo = prefs.getLong(PREF_FILTER_TO, Long.MAX_VALUE)
                    }
                    override fun callback(timeFilterFrom: Long, timeFilterTo: Long, includeMarkedAsPlayed_: Boolean) {
                        prefs.edit()
                            ?.putBoolean(PREF_INCLUDE_MARKED_PLAYED, includeMarkedAsPlayed_)
                            ?.putLong(PREF_FILTER_FROM, timeFilterFrom)
                            ?.putLong(PREF_FILTER_TO, timeFilterTo)
                            ?.apply()
//                        EventFlow.postEvent(FlowEvent.StatisticsEvent())
                        includeMarkedAsPlayed = includeMarkedAsPlayed_
                        statisticsState++
                    }
                }
                dialog.show()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

     private fun confirmResetStatistics() {
        val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(),
            R.string.statistics_reset_data, R.string.statistics_reset_data_msg) {
            override fun onConfirmButtonPressed(dialog: DialogInterface) {
                dialog.dismiss()
                prefs.edit()
                    ?.putBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
                    ?.putLong(PREF_FILTER_FROM, 0)
                    ?.putLong(PREF_FILTER_TO, Long.MAX_VALUE)
                    ?.apply()
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val mediaAll = realm.query(EpisodeMedia::class).find()
                            for (m in mediaAll) update(m) { m.playedDuration = 0 }
                        }
                        statisticsState++
                    } catch (error: Throwable) { Log.e(TAG, Log.getStackTraceString(error)) }
                }
            }
        }
        conDialog.createNewDialog().show()
    }

    class LineChartData(val values: MutableList<Float>) {
        val sum: Float

        init {
            var valueSum = 0f
            for (datum in values) valueSum += datum
            this.sum = valueSum
        }
        private fun getPercentageOfItem(index: Int): Float {
            if (sum == 0f) return 0f
            return values[index] / sum
        }
        private fun isLargeEnoughToDisplay(index: Int): Boolean {
            return getPercentageOfItem(index) > 0.01
        }
        fun getComposeColorOfItem(index: Int): Color {
            if (!isLargeEnoughToDisplay(index)) return Color.Gray
            return Color(COLOR_VALUES[index % COLOR_VALUES.size])
        }
        companion object {
            private val COLOR_VALUES = mutableListOf(-0xc88a1a, -0x1ae3dd, -0x6800, -0xda64dc, -0x63d850,
                -0xff663a, -0x22bb89, -0x995600, -0x47d1d2, -0xce9c6b,
                -0x66bb67, -0xdd5567, -0x5555ef, -0x99cc34, -0xff8c1a)
        }
    }

    companion object {
        val TAG = StatisticsFragment::class.simpleName ?: "Anonymous"

        private const val PREF_NAME: String = "StatisticsActivityPrefs"
        const val PREF_INCLUDE_MARKED_PLAYED: String = "countAll"
        const val PREF_FILTER_FROM: String = "filterFrom"
        const val PREF_FILTER_TO: String = "filterTo"
//        var prefs: SharedPreferences? = null
//
//        fun getSharedPrefs(context: Context) {
//            if (prefs == null) prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        }

        fun getStatistics(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long, feedId: Long = 0L, forDL: Boolean = false): StatisticsResult {
            Logd(TAG, "getStatistics called")
            val queryString = if (feedId != 0L) "episode.feedId == $feedId AND ((lastPlayedTime > $timeFilterFrom AND lastPlayedTime < $timeFilterTo) OR downloaded == true)"
            else if (forDL) "downloaded == true" else "lastPlayedTime > $timeFilterFrom AND lastPlayedTime < $timeFilterTo"
            val medias = realm.query(EpisodeMedia::class).query(queryString).find()

            val groupdMedias = medias.groupBy { it.episodeOrFetch()?.feedId ?: 0L }
            val result = StatisticsResult()
            result.oldestDate = Long.MAX_VALUE
            for ((fid, feedMedias) in groupdMedias) {
                val feed = getFeed(fid, false) ?: continue
                val numEpisodes = feed.episodes.size.toLong()
                var feedPlayedTime = 0L
                var timeSpent = 0L
                var durationWithSkip = 0L
                var feedTotalTime = 0L
                var episodesStarted = 0L
                var totalDownloadSize = 0L
                var episodesDownloadCount = 0L
                for (m in feedMedias) {
                    if (feedId != 0L || !forDL) {
                        if (m.lastPlayedTime > 0 && m.lastPlayedTime < result.oldestDate) result.oldestDate = m.lastPlayedTime
                        feedTotalTime += m.duration
                        if (includeMarkedAsPlayed) {
                            if ((m.playbackCompletionTime > 0 && m.playedDuration > 0) || (m.episodeOrFetch()?.playState ?: -10) >= PlayState.SKIPPED.code || m.position > 0) {
                                episodesStarted += 1
                                feedPlayedTime += m.duration
                                timeSpent += m.timeSpent
                            }
                        } else {
                            feedPlayedTime += m.playedDuration
                            timeSpent += m.timeSpent
                            Logd(TAG, "m.playedDuration: ${m.playedDuration} m.timeSpent: ${m.timeSpent}")
                            if (m.playbackCompletionTime > 0 && m.playedDuration > 0) episodesStarted += 1
                        }
                        durationWithSkip += m.duration
                    }
                    if (feedId != 0L || forDL) {
                        if (m.downloaded) {
                            episodesDownloadCount += 1
                            totalDownloadSize += m.size
                        }
                    }
                }
                feedPlayedTime /= 1000
                durationWithSkip /= 1000
                timeSpent /= 1000
                feedTotalTime /= 1000
                result.statsItems.add(StatisticsItem(feed, feedTotalTime, feedPlayedTime, timeSpent, durationWithSkip, numEpisodes, episodesStarted, totalDownloadSize, episodesDownloadCount))
            }
            return result
        }

        @Composable
        fun FeedStatisticsDialog(title: String, feedId: Long, onDismissRequest: () -> Unit) {
            var statisticsData: StatisticsItem? = null
            fun loadStatistics() {
                try {
                    val data = getStatistics(false, 0, Long.MAX_VALUE, feedId)
                    data.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem -> item2.timePlayed.compareTo(item1.timePlayed) }
                    if (data.statsItems.isNotEmpty()) statisticsData = data.statsItems[0]
                } catch (error: Throwable) { error.printStackTrace() }
            }
            loadStatistics()
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    val context = LocalContext.current
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(title)
                        Row {
                            Text(stringResource(R.string.statistics_episodes_started_total), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.getDefault(), "%d / %d", statisticsData?.episodesStarted ?: 0, statisticsData?.numEpisodes ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Text(stringResource(R.string.statistics_length_played), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(shortLocalizedDuration(context, statisticsData?.durationOfStarted ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Text(stringResource(R.string.statistics_time_played), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(shortLocalizedDuration(context, statisticsData?.timePlayed ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Text(stringResource(R.string.statistics_time_spent), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(shortLocalizedDuration(context, statisticsData?.timeSpent ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Text(stringResource(R.string.statistics_total_duration), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(shortLocalizedDuration(context, statisticsData?.time ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Text(stringResource(R.string.statistics_episodes_on_device), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.getDefault(), "%d", statisticsData?.episodesDownloadCount ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Text(stringResource(R.string.statistics_space_used), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Formatter.formatShortFileSize(context, statisticsData?.totalDownloadSize ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                        }
                        Row {
                            Button(onClick = { onDismissRequest() }) {Text(stringResource(android.R.string.ok)) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                MainActivityStarter(context).withOpenFeed(feedId).withAddToBackStack().start()
                                onDismissRequest()
                            }) {Text(stringResource(R.string.open_podcast)) }
                        }
                    }
                }
            }
        }
    }
}
