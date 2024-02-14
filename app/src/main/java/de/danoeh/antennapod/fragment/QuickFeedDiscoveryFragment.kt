package de.danoeh.antennapod.fragment

import de.danoeh.antennapod.activity.MainActivity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.OnlineFeedViewActivity
import de.danoeh.antennapod.adapter.FeedDiscoverAdapter
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.event.DiscoveryDefaultUpdateEvent
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader
import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class QuickFeedDiscoveryFragment : Fragment(), AdapterView.OnItemClickListener {
    private var disposable: Disposable? = null
    private var adapter: FeedDiscoverAdapter? = null
    private var discoverGridLayout: GridView? = null
    private var errorTextView: TextView? = null
    private var poweredByTextView: TextView? = null
    private var errorView: LinearLayout? = null
    private var errorRetry: Button? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root: View = inflater.inflate(R.layout.quick_feed_discovery, container, false)
        val discoverMore = root.findViewById<View>(R.id.discover_more)
        discoverMore.setOnClickListener { v: View? -> (activity as MainActivity).loadChildFragment(DiscoveryFragment()) }

        discoverGridLayout = root.findViewById(R.id.discover_grid)
        errorView = root.findViewById(R.id.discover_error)
        errorTextView = root.findViewById(R.id.discover_error_txtV)
        errorRetry = root.findViewById(R.id.discover_error_retry_btn)
        poweredByTextView = root.findViewById(R.id.discover_powered_by_itunes)

        adapter = FeedDiscoverAdapter(activity as MainActivity)
        discoverGridLayout?.setAdapter(adapter)
        discoverGridLayout?.onItemClickListener = this

        val displayMetrics: DisplayMetrics = requireContext().resources.displayMetrics
        val screenWidthDp: Float = displayMetrics.widthPixels / displayMetrics.density
        if (screenWidthDp > 600) {
            discoverGridLayout?.numColumns = 6
        } else {
            discoverGridLayout?.numColumns = 4
        }

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        val dummies: MutableList<PodcastSearchResult> = ArrayList<PodcastSearchResult>()
        for (i in 0 until NUM_SUGGESTIONS) {
            dummies.add(PodcastSearchResult.dummy())
        }

        adapter?.updateData(dummies)
        loadToplist()

        EventBus.getDefault().register(this)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun onDiscoveryDefaultUpdateEvent(event: DiscoveryDefaultUpdateEvent?) {
        loadToplist()
    }

    private fun loadToplist() {
        errorView?.visibility = View.GONE
        errorRetry!!.visibility = View.INVISIBLE
        errorRetry?.setText(R.string.retry_label)
        poweredByTextView?.visibility = View.VISIBLE

        val loader = ItunesTopListLoader(requireContext())
        val prefs: SharedPreferences = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
        val countryCode: String = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE,
            Locale.getDefault().country)!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            errorTextView?.setText(R.string.discover_is_hidden)
            errorView?.visibility = View.VISIBLE
            discoverGridLayout?.visibility = View.GONE
            errorRetry!!.visibility = View.GONE
            poweredByTextView?.visibility = View.GONE
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)) {
            errorTextView?.text = ""
            errorView?.visibility = View.VISIBLE
            discoverGridLayout?.visibility = View.VISIBLE
            errorRetry!!.visibility = View.VISIBLE
            errorRetry?.setText(R.string.discover_confirm)
            poweredByTextView?.visibility = View.VISIBLE
            errorRetry!!.setOnClickListener { v: View? ->
                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                loadToplist()
            }
            return
        }

        disposable = Observable.fromCallable {
            loader.loadToplist(countryCode,
                NUM_SUGGESTIONS,
                DBReader.getFeedList())
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { podcasts: List<PodcastSearchResult> ->
                    errorView?.visibility = View.GONE
                    if (podcasts.isEmpty()) {
                        errorTextView?.text = resources.getText(R.string.search_status_no_results)
                        errorView?.visibility = View.VISIBLE
                        discoverGridLayout?.visibility = View.INVISIBLE
                    } else {
                        discoverGridLayout?.visibility = View.VISIBLE
                        adapter?.updateData(podcasts)
                    }
                }, { error: Throwable ->
                    Log.e(TAG, Log.getStackTraceString(error))
                    errorTextView?.text = error.localizedMessage
                    errorView?.visibility = View.VISIBLE
                    discoverGridLayout?.visibility = View.INVISIBLE
                    errorRetry!!.visibility = View.VISIBLE
                    errorRetry?.setOnClickListener { v: View? -> loadToplist() }
                })
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val podcast: PodcastSearchResult = adapter!!.getItem(position)
        if (TextUtils.isEmpty(podcast.feedUrl)) {
            return
        }
        val intent = Intent(activity, OnlineFeedViewActivity::class.java)
        intent.putExtra(OnlineFeedViewActivity.ARG_FEEDURL, podcast.feedUrl)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "FeedDiscoveryFragment"
        private const val NUM_SUGGESTIONS = 12
    }
}
