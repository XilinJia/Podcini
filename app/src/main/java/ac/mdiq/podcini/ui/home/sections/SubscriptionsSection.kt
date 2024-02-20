package ac.mdiq.podcini.ui.home.sections

import ac.mdiq.podcini.activity.MainActivity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podcini.R
import ac.mdiq.podcini.adapter.HorizontalFeedListAdapter
import ac.mdiq.podcini.core.menuhandler.MenuItemUtils
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.core.storage.StatisticsItem
import ac.mdiq.podcini.event.FeedListUpdateEvent
import ac.mdiq.podcini.fragment.SubscriptionFragment
import ac.mdiq.podcini.model.feed.Feed
import ac.mdiq.podcini.ui.home.HomeSection
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SubscriptionsSection : HomeSection() {
    private var listAdapter: HorizontalFeedListAdapter? = null
    private var disposable: Disposable? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater,
                                           container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = super.onCreateView(inflater, container, savedInstanceState)
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
        listAdapter = object : HorizontalFeedListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?
            ) {
                super.onCreateContextMenu(contextMenu, view, contextMenuInfo)
                MenuItemUtils.setOnClickListeners(contextMenu
                ) { item: MenuItem ->
                    this@SubscriptionsSection.onContextItemSelected(item)
                }
            }
        }
        listAdapter?.setDummyViews(NUM_FEEDS)
        viewBinding.recyclerView.adapter = listAdapter
        val paddingHorizontal: Int = (12 * resources.displayMetrics.density).toInt()
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    @UnstableApi override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(SubscriptionFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        loadItems()
    }

    override val sectionTitle: String
        get() = getString(R.string.home_classics_title)

    override val moreLinkTitle: String
        get() = getString(R.string.subscriptions_label)

    private fun loadItems() {
        disposable?.dispose()

        val prefs: SharedPreferences =
            requireContext().getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val includeMarkedAsPlayed: Boolean = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        disposable = Observable.fromCallable<List<StatisticsItem>>
        { DBReader.getStatistics(includeMarkedAsPlayed, 0, Long.MAX_VALUE).feedTime }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ statisticsData: List<StatisticsItem> ->
                val stats = statisticsData.toMutableList()
                stats.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                    item2.timePlayed.compareTo(item1.timePlayed)
                }
                val feeds: MutableList<Feed> = ArrayList()
                var i = 0
                while (i < stats.size && i < NUM_FEEDS) {
                    feeds.add(stats[i].feed)
                    i++
                }
                listAdapter?.setDummyViews(0)
                listAdapter?.updateData(feeds)
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG: String = "SubscriptionsSection"
        private const val NUM_FEEDS = 8
    }
}
