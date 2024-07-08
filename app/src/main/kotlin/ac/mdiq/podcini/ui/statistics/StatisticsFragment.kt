package ac.mdiq.podcini.ui.statistics


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.storage.database.LogsAndStats.getStatistics
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.update
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.DatesFilterDialog
import ac.mdiq.podcini.ui.statistics.PieChartView.PieChartData
import ac.mdiq.podcini.util.Converter.shortLocalizedDuration
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class StatisticsFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var toolbar: MaterialToolbar

    private var _binding: PagerFragmentBinding? = null
    private val binding get() = _binding!!

    @OptIn(UnstableApi::class) override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
                POS_YEARS -> tab.setText(R.string.years_statistics_label)
                POS_SPACE_TAKEN -> tab.setText(R.string.downloads_label)
                else -> {}
            }
        }.attach()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Deprecated("Deprecated in Java")
    @UnstableApi override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.statistics_reset) {
            confirmResetStatistics()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Invalidate the toolbar menu if the current child fragment is visible.
     * @param child The fragment to invalidate
     */
    fun invalidateOptionsMenuIfActive(child: Fragment) {
        val visibleChild = childFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
        if (visibleChild === child) visibleChild.onPrepareOptionsMenu(toolbar.menu)
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

    @UnstableApi private fun confirmResetStatistics() {
        val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(),
            R.string.statistics_reset_data, R.string.statistics_reset_data_msg) {
            override fun onConfirmButtonPressed(dialog: DialogInterface) {
                dialog.dismiss()
                doResetStatistics()
            }
        }
        conDialog.createNewDialog().show()
    }

    @UnstableApi private fun doResetStatistics() {
        prefs!!.edit()
            .putBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
            .putLong(PREF_FILTER_FROM, 0)
            .putLong(PREF_FILTER_TO, Long.MAX_VALUE)
            .apply()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    resetStatistics()
                }
                // This runs on the Main thread
                EventFlow.postEvent(FlowEvent.StatisticsEvent())
            } catch (error: Throwable) {
                // This also runs on the Main thread
                Log.e(TAG, Log.getStackTraceString(error))
            }
        }
    }

    private fun resetStatistics(): Job {
        return runOnIOScope {
            val mediaAll = realm.query(EpisodeMedia::class).find()
            for (m in mediaAll) {
                update(m) { m.playedDuration = 0 }
            }
        }
    }

    private class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_SUBSCRIPTIONS -> SubscriptionStatisticsFragment()
                POS_YEARS -> YearsStatisticsFragment()
                POS_SPACE_TAKEN -> DownloadStatisticsFragment()
                else -> DownloadStatisticsFragment()
            }
        }
        override fun getItemCount(): Int {
            return TOTAL_COUNT
        }
    }

    class SubscriptionStatisticsFragment : Fragment() {
        private var _binding: StatisticsFragmentBinding? = null
        private val binding get() = _binding!!
        private var statisticsResult: StatisticsResult? = null
        private lateinit var feedStatisticsList: RecyclerView
        private lateinit var progressBar: ProgressBar
        private lateinit var listAdapter: ListAdapter

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setHasOptionsMenu(true)
        }
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = StatisticsFragmentBinding.inflate(inflater)
            feedStatisticsList = binding.statisticsList
            progressBar = binding.progressBar
            listAdapter = ListAdapter(this)
            feedStatisticsList.layoutManager = LinearLayoutManager(context)
            feedStatisticsList.adapter = listAdapter
            refreshStatistics()
            return binding.root
        }
        override fun onStart() {
            super.onStart()
            procFlowEvents()
        }
        override fun onStop() {
            super.onStop()
            cancelFlowEvents()
        }
        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
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
                        is FlowEvent.StatisticsEvent -> refreshStatistics()
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
                if (statisticsResult != null) {
                    val dialog = object: DatesFilterDialog(requireContext(), statisticsResult!!.oldestDate) {
                        override fun initParams() {
                            prefs = StatisticsFragment.prefs
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
                }
                return true
            }
            return super.onOptionsItemSelected(item)
        }
        private fun refreshStatistics() {
            progressBar.visibility = View.VISIBLE
            feedStatisticsList.visibility = View.GONE
            loadStatistics()
        }
        private fun loadStatistics() {
            val includeMarkedAsPlayed = prefs!!.getBoolean(PREF_INCLUDE_MARKED_PLAYED, false)
            val timeFilterFrom = prefs!!.getLong(PREF_FILTER_FROM, 0)
            val timeFilterTo = prefs!!.getLong(PREF_FILTER_TO, Long.MAX_VALUE)
            lifecycleScope.launch {
                try {
                    val statisticsData = withContext(Dispatchers.IO) {
                        val data = getStatistics(includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
                        data.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                            item2.timePlayed.compareTo(item1.timePlayed)
                        }
                        data
                    }
                    statisticsResult = statisticsData
                    // When "from" is "today", set it to today
                    listAdapter.setTimeFilter(includeMarkedAsPlayed,
                        max(min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), statisticsData.oldestDate.toDouble()).toLong(),
                        min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
                    listAdapter.update(statisticsData.statsItems)
                    progressBar.visibility = View.GONE
                    feedStatisticsList.visibility = View.VISIBLE
                } catch (error: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(error))
                }
            }
        }

        private class ListAdapter(private val fragment: Fragment) : StatisticsListAdapter(fragment.requireContext()) {
            private var timeFilterFrom: Long = 0
            private var timeFilterTo = Long.MAX_VALUE
            private var includeMarkedAsPlayed = false

            override val headerCaption: String
                get() {
                    if (includeMarkedAsPlayed) return context.getString(R.string.statistics_counting_total)
                    val skeleton = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM yyyy")
                    val dateFormat = SimpleDateFormat(skeleton, Locale.getDefault())
                    val dateFrom = dateFormat.format(Date(timeFilterFrom))
                    // FilterTo is first day of next month => Subtract one day
                    val dateTo = dateFormat.format(Date(timeFilterTo - 24L * 3600000L))
                    return context.getString(R.string.statistics_counting_range, dateFrom, dateTo)
                }
            override val headerValue: String
                get() = shortLocalizedDuration(context, pieChartData!!.sum.toLong())

            fun setTimeFilter(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long) {
                this.includeMarkedAsPlayed = includeMarkedAsPlayed
                this.timeFilterFrom = timeFilterFrom
                this.timeFilterTo = timeFilterTo
            }
            override fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData {
                val dataValues = FloatArray(statisticsData!!.size)
                for (i in statisticsData.indices) {
                    val item = statisticsData[i]
                    dataValues[i] = item.timePlayed.toFloat()
                }
                return PieChartData(dataValues)
            }
            override fun onBindFeedViewHolder(holder: StatisticsHolder?, item: StatisticsItem?) {
                val time = item!!.timePlayed
                holder!!.value.text = shortLocalizedDuration(context, time)
                holder.itemView.setOnClickListener {
                    val yourDialogFragment = StatisticsDialogFragment.newInstance(item.feed.id, item.feed.title)
                    yourDialogFragment.show(fragment.childFragmentManager.beginTransaction(), "DialogFragment")
                }
            }
        }
    }

    class YearsStatisticsFragment : Fragment() {
        private var _binding: StatisticsFragmentBinding? = null
        private val binding get() = _binding!!
        private lateinit var yearStatisticsList: RecyclerView
        private lateinit var progressBar: ProgressBar
        private lateinit var listAdapter: ListAdapter

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = StatisticsFragmentBinding.inflate(inflater)
            yearStatisticsList = binding.statisticsList
            progressBar = binding.progressBar
            listAdapter = ListAdapter(requireContext())
            yearStatisticsList.layoutManager = LinearLayoutManager(context)
            yearStatisticsList.adapter = listAdapter
            refreshStatistics()
            return binding.root
        }
        override fun onStart() {
            super.onStart()
            procFlowEvents()
        }
        override fun onStop() {
            super.onStop()
            cancelFlowEvents()
        }
        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
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
                        is FlowEvent.StatisticsEvent -> refreshStatistics()
                        else -> {}
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
        private fun refreshStatistics() {
            progressBar.visibility = View.VISIBLE
            yearStatisticsList.visibility = View.GONE
            loadStatistics()
        }
        private fun loadStatistics() {
            lifecycleScope.launch {
                try {
                    val result: List<MonthlyStatisticsItem> = withContext(Dispatchers.IO) {
                        getMonthlyTimeStatistics()
                    }
                    listAdapter.update(result)
                    progressBar.visibility = View.GONE
                    yearStatisticsList.visibility = View.VISIBLE
                } catch (error: Throwable) {
                    // This also runs on the Main thread
                    Log.e(TAG, Log.getStackTraceString(error))
                }
            }
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
                for (m in medias_) {
                    if (m.playedDuration > 0) dur += m.playedDuration
                    else {
//                        progress import does not include playedDuration
                        if (includeMarkedAsPlayed) {
                            if (m.playbackCompletionTime > 0 || m.episode?.playState == Episode.PLAYED)
                                dur += m.duration
                            else if (m.position > 0) dur += m.position
                        } else dur += m.position
                    }
                }
                mItem.timePlayed = dur
                months.add(mItem)
            }
            return months
        }

        /**
         * Adapter for the yearly playback statistics list.
         */
        private class ListAdapter(val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            private val statisticsData: MutableList<MonthlyStatisticsItem> = ArrayList()
            private val yearlyAggregate: MutableList<MonthlyStatisticsItem?> = ArrayList()

            override fun getItemCount(): Int {
                return yearlyAggregate.size + 1
            }
            override fun getItemViewType(position: Int): Int {
                return if (position == 0) TYPE_HEADER else TYPE_FEED
            }
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val inflater = LayoutInflater.from(context)
                if (viewType == TYPE_HEADER) return HeaderHolder(inflater.inflate(R.layout.statistics_listitem_barchart, parent, false))
                return StatisticsHolder(inflater.inflate(R.layout.statistics_year_listitem, parent, false))
            }

            @SuppressLint("SetTextI18n")
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
                if (getItemViewType(position) == TYPE_HEADER) {
                    val holder = h as HeaderHolder
                    holder.barChart.setData(statisticsData)
                } else {
                    val holder = h as StatisticsHolder
                    val statsItem = yearlyAggregate[position - 1]
                    holder.year.text = String.format(Locale.getDefault(), "%d ", statsItem!!.year)
                    holder.hours.text = String.format(Locale.getDefault(),
                        "%.1f ",
                        statsItem.timePlayed / 3600000.0f) + context.getString(R.string.time_hours)
                }
            }
            @SuppressLint("NotifyDataSetChanged")
            fun update(statistics: List<MonthlyStatisticsItem>) {
                var lastYear = if (statistics.isNotEmpty()) statistics[0].year else 0
                var lastDataPoint = if (statistics.isNotEmpty()) (statistics[0].month - 1) + lastYear * 12 else 0
                var yearSum: Long = 0
                yearlyAggregate.clear()
                statisticsData.clear()
                for (statistic in statistics) {
                    if (statistic.year != lastYear) {
                        val yearAggregate = MonthlyStatisticsItem()
                        yearAggregate.year = lastYear
                        yearAggregate.timePlayed = yearSum
                        yearlyAggregate.add(yearAggregate)
                        yearSum = 0
                        lastYear = statistic.year
                    }
                    yearSum += statistic.timePlayed
                    while (lastDataPoint + 1 < (statistic.month - 1) + statistic.year * 12) {
                        lastDataPoint++
                        val item = MonthlyStatisticsItem()
                        item.year = lastDataPoint / 12
                        item.month = lastDataPoint % 12 + 1
                        statisticsData.add(item) // Compensate for months without playback
                    }
                    statisticsData.add(statistic)
                    lastDataPoint = (statistic.month - 1) + statistic.year * 12
                }
                val yearAggregate = MonthlyStatisticsItem()
                yearAggregate.year = lastYear
                yearAggregate.timePlayed = yearSum
                yearlyAggregate.add(yearAggregate)
                yearlyAggregate.reverse()
                notifyDataSetChanged()
            }

            private class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                val binding = StatisticsListitemBarchartBinding.bind(itemView)
                var barChart: BarChartView = binding.barChart
            }

            private class StatisticsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                val binding = StatisticsYearListitemBinding.bind(itemView)
                var year: TextView = binding.yearLabel
                var hours: TextView = binding.hoursLabel
            }

            companion object {
                private const val TYPE_HEADER = 0
                private const val TYPE_FEED = 1
            }
        }
    }

    class DownloadStatisticsFragment : Fragment() {
        private var _binding: StatisticsFragmentBinding? = null
        private val binding get() = _binding!!
        private lateinit var downloadStatisticsList: RecyclerView
        private lateinit var progressBar: ProgressBar
        private lateinit var listAdapter: ListAdapter

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = StatisticsFragmentBinding.inflate(inflater)
            downloadStatisticsList = binding.statisticsList
            progressBar = binding.progressBar
            listAdapter = ListAdapter(requireContext(), this)
            downloadStatisticsList.layoutManager = LinearLayoutManager(context)
            downloadStatisticsList.adapter = listAdapter
            refreshDownloadStatistics()

            return binding.root
        }
        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }
        @Deprecated("Deprecated in Java")
        override fun onPrepareOptionsMenu(menu: Menu) {
            super.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.statistics_reset).setVisible(false)
            menu.findItem(R.id.statistics_filter).setVisible(false)
        }
        private fun refreshDownloadStatistics() {
            progressBar.visibility = View.VISIBLE
            downloadStatisticsList.visibility = View.GONE
            loadStatistics()
        }
        private fun loadStatistics() {
            lifecycleScope.launch {
                try {
                    val statisticsData = withContext(Dispatchers.IO) {
                        val data = getStatistics(false, 0, Long.MAX_VALUE)
                        data.statsItems.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                            item2.totalDownloadSize.compareTo(item1.totalDownloadSize)
                        }
                        data
                    }
                    listAdapter.update(statisticsData.statsItems)
                    progressBar.visibility = View.GONE
                    downloadStatisticsList.visibility = View.VISIBLE
                } catch (error: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(error))
                }
            }
        }

        private class ListAdapter(context: Context, private val fragment: Fragment) : StatisticsListAdapter(context) {
            override val headerCaption: String
                get() = context.getString(R.string.total_size_downloaded_podcasts)
            override val headerValue: String
                get() = Formatter.formatShortFileSize(context, pieChartData!!.sum.toLong())

            override fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData {
                val dataValues = FloatArray(statisticsData!!.size)
                for (i in statisticsData.indices) {
                    val item = statisticsData[i]
                    dataValues[i] = item.totalDownloadSize.toFloat()
                }
                return PieChartData(dataValues)
            }
            @SuppressLint("SetTextI18n")
            override fun onBindFeedViewHolder(holder: StatisticsHolder?, item: StatisticsItem?) {
                holder!!.value.text = ("${Formatter.formatShortFileSize(context, item!!.totalDownloadSize)} • "
                        + String.format(Locale.getDefault(), "%d%s", item.episodesDownloadCount, context.getString(R.string.episodes_suffix)))
                holder.itemView.setOnClickListener {
                    val yourDialogFragment = StatisticsDialogFragment.newInstance(item.feed.id, item.feed.title)
                    yourDialogFragment.show(fragment.childFragmentManager.beginTransaction(), "DialogFragment")
                }
            }
        }
    }

    class StatisticsDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setPositiveButton(android.R.string.ok, null)
            dialog.setNeutralButton(R.string.open_podcast) { _: DialogInterface?, _: Int ->
                val feedId = requireArguments().getLong(EXTRA_FEED_ID)
                MainActivityStarter(requireContext()).withOpenFeed(feedId).withAddToBackStack().start()
            }
            dialog.setTitle(requireArguments().getString(EXTRA_FEED_TITLE))
            dialog.setView(R.layout.feed_statistics_dialog)
            return dialog.create()
        }
        override fun onStart() {
            super.onStart()
            val feedId = requireArguments().getLong(EXTRA_FEED_ID)
            childFragmentManager.beginTransaction().replace(R.id.statisticsContainer,
                FeedStatisticsFragment.newInstance(feedId, true), "feed_statistics_fragment")
                .commitAllowingStateLoss()
        }

        companion object {
            private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"
            private const val EXTRA_FEED_TITLE = "ac.mdiq.podcini.extra.feedTitle"

            fun newInstance(feedId: Long, feedTitle: String?): StatisticsDialogFragment {
                val fragment = StatisticsDialogFragment()
                val arguments = Bundle()
                arguments.putLong(EXTRA_FEED_ID, feedId)
                arguments.putString(EXTRA_FEED_TITLE, feedTitle)
                fragment.arguments = arguments
                return fragment
            }
        }
    }

    /**
     * Parent Adapter for the playback and download statistics list.
     */
    private abstract class StatisticsListAdapter protected constructor(@JvmField protected val context: Context) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var statisticsData: List<StatisticsItem>? = null
        @JvmField
        protected var pieChartData: PieChartData? = null
        protected abstract val headerCaption: String?
        protected abstract val headerValue: String?

        override fun getItemCount(): Int {
            return statisticsData!!.size + 1
        }
        override fun getItemViewType(position: Int): Int {
            return if (position == 0) TYPE_HEADER else TYPE_FEED
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(context)
            if (viewType == TYPE_HEADER) return HeaderHolder(inflater.inflate(R.layout.statistics_listitem_total, parent, false))
            return StatisticsHolder(inflater.inflate(R.layout.statistics_listitem, parent, false))
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_HEADER) {
                val holder = h as HeaderHolder
                holder.pieChart.setData(pieChartData)
                holder.totalTime.text = headerValue
                holder.totalText.text = headerCaption
            } else {
                val holder = h as StatisticsHolder
                val statsItem = statisticsData!![position - 1]
                holder.image.load(statsItem.feed.imageUrl) {
                    placeholder(R.color.light_gray)
                    error(R.mipmap.ic_launcher)
                }
                holder.title.text = statsItem.feed.title
                holder.chip.setTextColor(pieChartData!!.getColorOfItem(position - 1))
                onBindFeedViewHolder(holder, statsItem)
            }
        }
        @SuppressLint("NotifyDataSetChanged")
        fun update(statistics: List<StatisticsItem>?) {
            statisticsData = statistics
            pieChartData = generateChartData(statistics)
            notifyDataSetChanged()
        }

        class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = StatisticsListitemTotalBinding.bind(itemView)
            var totalTime: TextView = binding.totalTime
            var pieChart: PieChartView = binding.pieChart
            var totalText: TextView = binding.totalDescription
        }

        class StatisticsHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = StatisticsListitemBinding.bind(itemView)
            var image: ImageView = binding.imgvCover
            var title: TextView = binding.txtvTitle
            @JvmField
            var value: TextView = binding.txtvValue
            var chip: TextView = binding.chip
        }

        protected abstract fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData?
        protected abstract fun onBindFeedViewHolder(holder: StatisticsHolder?, item: StatisticsItem?)

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_FEED = 1
        }
    }

    companion object {
        val TAG = StatisticsFragment::class.simpleName ?: "Anonymous"

        const val PREF_NAME: String = "StatisticsActivityPrefs"
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
    }
}
