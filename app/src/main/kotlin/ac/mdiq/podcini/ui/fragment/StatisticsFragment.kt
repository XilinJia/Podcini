package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.PagerFragmentBinding
import ac.mdiq.podcini.storage.database.LogsAndStats.getStatistics
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.update
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.DurationConverter.shortLocalizedDuration
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.DatesFilterDialog
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import android.view.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.max
import kotlin.math.min

class StatisticsFragment : Fragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var toolbar: MaterialToolbar

    private var _binding: PagerFragmentBinding? = null
    private val binding get() = _binding!!

     override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)
        _binding = PagerFragmentBinding.inflate(inflater)
        viewPager = binding.viewpager
        toolbar = binding.toolbar
        toolbar.title = getString(R.string.statistics_label)
        toolbar.inflateMenu(R.menu.statistics)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        (activity as MainActivity).setupToolbarToggle(toolbar, false)

        viewPager.adapter = PagerAdapter(this)
        // Give the TabLayout the ViewPager
        tabLayout = binding.slidingTabs
        setupPagedToolbar(toolbar, viewPager)

        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            when (position) {
                POS_SUBSCRIPTIONS -> tab.setText(R.string.subscriptions_label)
                POS_YEARS -> tab.setText(R.string.months_statistics_label)
                POS_SPACE_TAKEN -> tab.setText(R.string.downloads_label)
                else -> {}
            }
        }.attach()
        return binding.root
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
     override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.statistics_reset) {
            confirmResetStatistics()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupPagedToolbar(toolbar: MaterialToolbar, viewPager: ViewPager2) {
        this.toolbar = toolbar
        this.viewPager = viewPager

        toolbar.setOnMenuItemClickListener { item: MenuItem? ->
            if (this.onOptionsItemSelected(item!!)) return@setOnMenuItemClickListener true
            val child = childFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
            if (child != null) return@setOnMenuItemClickListener child.onOptionsItemSelected(item)
            false
        }
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val child = childFragmentManager.findFragmentByTag("f$position")
                child?.onPrepareOptionsMenu(toolbar.menu)
            }
        })
    }

     private fun confirmResetStatistics() {
        val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(),
            R.string.statistics_reset_data, R.string.statistics_reset_data_msg) {
            override fun onConfirmButtonPressed(dialog: DialogInterface) {
                dialog.dismiss()
                doResetStatistics()
            }
        }
        conDialog.createNewDialog().show()
    }

     private fun doResetStatistics() {
        prefs!!.edit()
            .putBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
            .putLong(PREF_FILTER_FROM, 0)
            .putLong(PREF_FILTER_TO, Long.MAX_VALUE)
            .apply()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { resetStatistics() }
                EventFlow.postEvent(FlowEvent.StatisticsEvent())
            } catch (error: Throwable) { Log.e(TAG, Log.getStackTraceString(error)) }
        }
    }

    private fun resetStatistics(): Job {
        return runOnIOScope {
            val mediaAll = realm.query(EpisodeMedia::class).find()
            for (m in mediaAll) update(m) { m.playedDuration = 0 }
        }
    }

    private class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_SUBSCRIPTIONS -> SubscriptionStatisticsFragment()
                POS_YEARS -> MonthlyStatisticsFragment()
                POS_SPACE_TAKEN -> DownloadStatisticsFragment()
                else -> DownloadStatisticsFragment()
            }
        }
        override fun getItemCount(): Int {
            return TOTAL_COUNT
        }
    }

    class SubscriptionStatisticsFragment : Fragment() {
        lateinit var statisticsData: StatisticsResult
        private lateinit var lineChartData: LineChartData
        private var timeSpentSum = 0L
        private var timeFilterFrom: Long = 0
        private var timeFilterTo = Long.MAX_VALUE
        private var includeMarkedAsPlayed = false

        private var timePlayedToday: Long = 0
        private var timeSpentToday: Long = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setHasOptionsMenu(true)
        }
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            loadStatistics()
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    CustomTheme(requireContext()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.statistics_today), color = MaterialTheme.colorScheme.onSurface)
                            Row {
                                Text(stringResource(R.string.duration) + ": " + shortLocalizedDuration(context, timePlayedToday), color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(20.dp))
                                Text( stringResource(R.string.spent) + ": " + shortLocalizedDuration(context, timeSpentToday), color = MaterialTheme.colorScheme.onSurface)
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
                                Text(stringResource(R.string.duration) + ": " + shortLocalizedDuration(context, lineChartData.sum.toLong()), color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(20.dp))
                                Text( stringResource(R.string.spent) + ": " + shortLocalizedDuration(context, timeSpentSum), color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalLineChart(lineChartData)
                            StatsList(statisticsData, lineChartData) { item ->
                                context.getString(R.string.duration) + ": " + shortLocalizedDuration(context, item!!.timePlayed) +
                                        "\t" + context.getString(R.string.spent) + ": " + shortLocalizedDuration(context, item.timeSpent)
                            }
                        }
                    }
                }
            }
            return composeView
        }
        override fun onStart() {
            super.onStart()
            procFlowEvents()
        }
        override fun onStop() {
            super.onStop()
            cancelFlowEvents()
        }
        private var eventSink: Job?     = null
        private fun cancelFlowEvents() {
            eventSink?.cancel()
            eventSink = null
        }
        private fun procFlowEvents() {
            if (eventSink != null) return
            eventSink = lifecycleScope.launch {
                EventFlow.events.collectLatest { event ->
                    Logd(TAG, "Received event: ${event.TAG}")
                    when (event) {
                        is FlowEvent.StatisticsEvent -> loadStatistics()
                        else -> {}
                    }
                }
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onPrepareOptionsMenu(menu: Menu) {
            super.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.statistics_reset).setVisible(true)
            menu.findItem(R.id.statistics_filter).setVisible(true)
        }
        @Deprecated("Deprecated in Java")
        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.statistics_filter) {
                val dialog = object: DatesFilterDialog(requireContext(), statisticsData.oldestDate) {
                    override fun initParams() {
                        prefs = Companion.prefs
                        includeMarkedAsPlayed = prefs!!.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
                        timeFilterFrom = prefs!!.getLong(PREF_FILTER_FROM, 0)
                        timeFilterTo = prefs!!.getLong(PREF_FILTER_TO, Long.MAX_VALUE)
                    }
                    override fun callback(timeFilterFrom: Long, timeFilterTo: Long, includeMarkedAsPlayed: Boolean) {
                        prefs!!.edit()
                            .putBoolean(PREF_INCLUDE_MARKED_PLAYED, includeMarkedAsPlayed)
                            .putLong(PREF_FILTER_FROM, timeFilterFrom)
                            .putLong(PREF_FILTER_TO, timeFilterTo)
                            .apply()
                        EventFlow.postEvent(FlowEvent.StatisticsEvent())
                    }
                }
                dialog.show()
                return true
            }
            return super.onOptionsItemSelected(item)
        }
        private fun setTimeFilter(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long) {
            this.includeMarkedAsPlayed = includeMarkedAsPlayed
            this.timeFilterFrom = timeFilterFrom
            this.timeFilterTo = timeFilterTo
        }
        private fun loadStatistics() {
            val statsToday = getStatistics(true, LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), Long.MAX_VALUE)
            for (item in statsToday.statsItems) {
                timePlayedToday += item.timePlayed
                timeSpentToday += item.timeSpent
            }
            val includeMarkedAsPlayed = prefs!!.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
            val timeFilterFrom = prefs!!.getLong(PREF_FILTER_FROM, 0)
            val timeFilterTo = prefs!!.getLong(PREF_FILTER_TO, Long.MAX_VALUE)
            try {
                statisticsData = getStatistics(includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
                statisticsData.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem -> item2.timePlayed.compareTo(item1.timePlayed) }
                val dataValues = MutableList(statisticsData.statsItems.size){0f}
                for (i in statisticsData.statsItems.indices) {
                    val item = statisticsData.statsItems[i]
                    dataValues[i] = item.timePlayed.toFloat()
                    timeSpentSum += item.timeSpent
                }
                lineChartData = LineChartData(dataValues)
                // When "from" is "today", set it to today
                setTimeFilter(includeMarkedAsPlayed,
                    max(min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), statisticsData.oldestDate.toDouble()).toLong(),
                    min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
            } catch (error: Throwable) { Log.e(TAG, Log.getStackTraceString(error)) }
        }
    }

    class MonthlyStatisticsFragment : Fragment() {
        private lateinit var monthlyStats: List<MonthlyStatisticsItem>
        private var maxDataValue = 1f
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            loadStatistics()
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    CustomTheme(requireContext()) {
                        Column {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp)) { BarChart() }
                            Spacer(Modifier.height(20.dp))
                            MonthList()
                        }
                    }
                }
            }
            return composeView
        }
        override fun onStart() {
            super.onStart()
            procFlowEvents()
        }
        override fun onStop() {
            super.onStop()
            cancelFlowEvents()
        }
        private var eventSink: Job?     = null
        private fun cancelFlowEvents() {
            eventSink?.cancel()
            eventSink = null
        }
        private fun procFlowEvents() {
            if (eventSink != null) return
            eventSink = lifecycleScope.launch {
                EventFlow.events.collectLatest { event ->
                    Logd(TAG, "Received event: ${event.TAG}")
                    when (event) {
                        is FlowEvent.StatisticsEvent -> loadStatistics()
                        else -> {}
                    }
                }
            }
        }
        @Composable
        fun BarChart() {
            val barWidth = 40f
            val spaceBetweenBars = 16f
            Canvas(modifier = Modifier.width((monthlyStats.size * (barWidth + spaceBetweenBars)).dp).height(150.dp)) {
//                val canvasWidth = size.width
                val canvasHeight = size.height
                for (index in monthlyStats.indices) {
                    val barHeight = (monthlyStats[index].timePlayed / maxDataValue) * canvasHeight // Normalize height
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
                itemsIndexed(monthlyStats) { index, item ->
                    Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        Column {
                            val monthString = String.format(Locale.getDefault(), "%d-%d", monthlyStats[index].year, monthlyStats[index].month)
                            Text(monthString, color = textColor, style = MaterialTheme.typography.bodyLarge.merge())
                            val hoursString = stringResource(R.string.duration) + ": " + String.format(Locale.getDefault(), "%.1f ", monthlyStats[index].timePlayed / 3600000.0f) + stringResource(R.string.time_hours) +
                                    "\t" + stringResource(R.string.spent) + ": " + String.format(Locale.getDefault(), "%.1f ", monthlyStats[index].timeSpent / 3600000.0f) + stringResource(R.string.time_hours)
                            Text(hoursString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onPrepareOptionsMenu(menu: Menu) {
            super.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.statistics_reset).setVisible(true)
            menu.findItem(R.id.statistics_filter).setVisible(false)
        }
        private fun loadStatistics() {
            try {
                monthlyStats = getMonthlyTimeStatistics()
                for (item in monthlyStats) maxDataValue = max(maxDataValue.toDouble(), item.timePlayed.toDouble()).toFloat()
                Logd(TAG, "maxDataValue: $maxDataValue")
            } catch (error: Throwable) { Log.e(TAG, Log.getStackTraceString(error)) }
        }
        private fun getMonthlyTimeStatistics(): List<MonthlyStatisticsItem> {
            Logd(TAG, "getMonthlyTimeStatistics called")
            val includeMarkedAsPlayed = prefs!!.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)

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
            return months
        }
    }

    class DownloadStatisticsFragment : Fragment() {
        private lateinit var statisticsData: StatisticsResult
        private lateinit var lineChartData: LineChartData

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            loadStatistics()
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    CustomTheme(requireContext()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.total_size_downloaded_podcasts), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                            Text(Formatter.formatShortFileSize(context, lineChartData.sum.toLong()), color = MaterialTheme.colorScheme.onSurface)
                            HorizontalLineChart(lineChartData)
                            StatsList(statisticsData, lineChartData) { item ->
                                ("${Formatter.formatShortFileSize(context, item!!.totalDownloadSize)} • "
                                        + String.format(Locale.getDefault(), "%d%s", item.episodesDownloadCount, context.getString(R.string.episodes_suffix)))
                            }
                        }
                    }
                }
            }
            return composeView
        }

        @Deprecated("Deprecated in Java")
        override fun onPrepareOptionsMenu(menu: Menu) {
            super.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.statistics_reset).setVisible(false)
            menu.findItem(R.id.statistics_filter).setVisible(false)
        }

        private fun loadStatistics() {
            statisticsData = getStatistics(false, 0, Long.MAX_VALUE)
            statisticsData.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem -> item2.totalDownloadSize.compareTo(item1.totalDownloadSize) }
            val dataValues = MutableList(statisticsData.statsItems.size) { 0f }
            for (i in statisticsData.statsItems.indices) {
                val item = statisticsData.statsItems[i]
                dataValues[i] = item.totalDownloadSize.toFloat()
            }
            lineChartData = LineChartData(dataValues)
        }
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
            return getPercentageOfItem(index) > 0.04
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

        private const val POS_SUBSCRIPTIONS = 0
        private const val POS_YEARS = 1
        private const val POS_SPACE_TAKEN = 2
        private const val TOTAL_COUNT = 3

        var prefs: SharedPreferences? = null

        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
                    Logd(TAG, "index: $index segmentWidth: $segmentWidth")
                    drawRect(color = lineChartData.getComposeColorOfItem(index),
                        topLeft = androidx.compose.ui.geometry.Offset(startX, lineY - 10),
                        size = androidx.compose.ui.geometry.Size(segmentWidth, 20f))
                    startX += segmentWidth
                }
            }
        }

        @Composable
        fun StatsList(statisticsData: StatisticsResult, lineChartData: LineChartData, infoCB: (StatisticsItem?)->String) {
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
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
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
