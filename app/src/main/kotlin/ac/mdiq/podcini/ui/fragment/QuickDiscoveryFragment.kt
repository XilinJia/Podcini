package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.QuickFeedDiscoveryBinding
import ac.mdiq.podcini.databinding.QuickFeedDiscoveryItemBinding
import ac.mdiq.podcini.net.feed.discovery.ItunesTopListLoader
import ac.mdiq.podcini.net.feed.discovery.ItunesTopListLoader.Companion.prefs
import ac.mdiq.podcini.net.feed.discovery.PodcastSearchResult
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.*

class QuickDiscoveryFragment : Fragment(), AdapterView.OnItemClickListener {
    private var _binding: QuickFeedDiscoveryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FeedDiscoverAdapter
    private lateinit var discoverGridLayout: GridView
    private lateinit var errorTextView: TextView
    private lateinit var poweredByTextView: TextView
    private lateinit var errorView: LinearLayout
    private lateinit var errorRetry: Button

    @OptIn(UnstableApi::class) override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = QuickFeedDiscoveryBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        val discoverMore = binding.discoverMore
        discoverMore.setOnClickListener { (activity as MainActivity).loadChildFragment(DiscoveryFragment()) }

        discoverGridLayout = binding.discoverGrid
        errorView = binding.discoverError
        errorTextView = binding.discoverErrorTxtV
        errorRetry = binding.discoverErrorRetryBtn
        poweredByTextView = binding.discoverPoweredByItunes

        adapter = FeedDiscoverAdapter(activity as MainActivity)
        discoverGridLayout.setAdapter(adapter)
        discoverGridLayout.onItemClickListener = this

        val displayMetrics: DisplayMetrics = requireContext().resources.displayMetrics
        val screenWidthDp: Float = displayMetrics.widthPixels / displayMetrics.density
        if (screenWidthDp > 600) discoverGridLayout.numColumns = 6
        else discoverGridLayout.numColumns = 4

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        val dummies: MutableList<PodcastSearchResult> = ArrayList<PodcastSearchResult>()
        for (i in 0 until NUM_SUGGESTIONS) {
            dummies.add(PodcastSearchResult.dummy())
        }

        adapter.updateData(dummies)
        loadToplist()

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

    override fun onDestroy() {
        super.onDestroy()
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
                    is FlowEvent.DiscoveryDefaultUpdateEvent -> loadToplist()
                    else -> {}
                }
            }
        }
    }

    private fun loadToplist() {
        errorView.visibility = View.GONE
        errorRetry.visibility = View.INVISIBLE
        errorRetry.setText(R.string.retry_label)
        poweredByTextView.visibility = View.VISIBLE

        val loader = ItunesTopListLoader(requireContext())
//        val prefs: SharedPreferences = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
        val countryCode: String = prefs!!.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
        if (prefs!!.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            errorTextView.setText(R.string.discover_is_hidden)
            errorView.visibility = View.VISIBLE
            discoverGridLayout.visibility = View.GONE
            errorRetry.visibility = View.GONE
            poweredByTextView.visibility = View.GONE
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs!!.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)) {
            errorTextView.text = ""
            errorView.visibility = View.VISIBLE
            discoverGridLayout.visibility = View.VISIBLE
            errorRetry.visibility = View.VISIBLE
            errorRetry.setText(R.string.discover_confirm)
            poweredByTextView.visibility = View.VISIBLE
            errorRetry.setOnClickListener {
                prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                loadToplist()
            }
            return
        }

        lifecycleScope.launch {
            try {
                val podcasts = withContext(Dispatchers.IO) {
                    loader.loadToplist(countryCode, NUM_SUGGESTIONS, getFeedList())
                }
                withContext(Dispatchers.Main) {
                    errorView.visibility = View.GONE
                    if (podcasts.isEmpty()) {
                        errorTextView.text = resources.getText(R.string.search_status_no_results)
                        errorView.visibility = View.VISIBLE
                        discoverGridLayout.visibility = View.INVISIBLE
                    } else {
                        discoverGridLayout.visibility = View.VISIBLE
                        adapter.updateData(podcasts)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                errorTextView.text = e.localizedMessage
                errorView.visibility = View.VISIBLE
                discoverGridLayout.visibility = View.INVISIBLE
                errorRetry.visibility = View.VISIBLE
                errorRetry.setOnClickListener { loadToplist() }
            }
        }
    }

    @OptIn(UnstableApi::class) override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val podcast: PodcastSearchResult? = adapter.getItem(position)
        if (podcast?.feedUrl.isNullOrEmpty()) return

        val fragment: Fragment = OnlineFeedViewFragment.newInstance(podcast!!.feedUrl!!)
        (activity as MainActivity).loadChildFragment(fragment)
    }

    class FeedDiscoverAdapter(mainActivity: MainActivity) : BaseAdapter() {
        private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
        private val data: MutableList<PodcastSearchResult> = ArrayList()

        fun updateData(newData: List<PodcastSearchResult>) {
            data.clear()
            data.addAll(newData)
            notifyDataSetChanged()
        }

        override fun getCount(): Int {
            return data.size
        }

        override fun getItem(position: Int): PodcastSearchResult? {
            return if (position in data.indices) data[position] else null
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val holder: Holder

            if (convertView == null) {
                convertView = View.inflate(mainActivityRef.get(), R.layout.quick_feed_discovery_item, null)
                val binding = QuickFeedDiscoveryItemBinding.bind(convertView)
                holder = Holder()
                holder.imageView = binding.discoveryCover
                convertView.tag = holder
            } else holder = convertView.tag as Holder

            val podcast: PodcastSearchResult? = getItem(position)
            holder.imageView!!.contentDescription = podcast?.title

            holder.imageView?.load(podcast?.imageUrl) {
                placeholder(R.color.light_gray)
                error(R.mipmap.ic_launcher)
            }
            return convertView!!
        }

        internal class Holder {
            var imageView: ImageView? = null
        }
    }

    companion object {
        private val TAG: String = QuickDiscoveryFragment::class.simpleName ?: "Anonymous"
        private const val NUM_SUGGESTIONS = 12
    }
}
