package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.QuickFeedDiscoveryBinding
import ac.mdiq.podcini.net.discovery.ItunesTopListLoader
import ac.mdiq.podcini.net.discovery.PodcastSearchResult
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.FeedDiscoverAdapter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class QuickFeedDiscoveryFragment : Fragment(), AdapterView.OnItemClickListener {
    private var _binding: QuickFeedDiscoveryBinding? = null
    private val binding get() = _binding!!

//    private var disposable: Disposable? = null
//    val scope = CoroutineScope(Dispatchers.Main)

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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        
//        scope.cancel()
//        disposable?.dispose()
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
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
        val prefs: SharedPreferences = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
        val countryCode: String = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            errorTextView.setText(R.string.discover_is_hidden)
            errorView.visibility = View.VISIBLE
            discoverGridLayout.visibility = View.GONE
            errorRetry.visibility = View.GONE
            poweredByTextView.visibility = View.GONE
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)) {
            errorTextView.text = ""
            errorView.visibility = View.VISIBLE
            discoverGridLayout.visibility = View.VISIBLE
            errorRetry.visibility = View.VISIBLE
            errorRetry.setText(R.string.discover_confirm)
            poweredByTextView.visibility = View.VISIBLE
            errorRetry.setOnClickListener {
                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                loadToplist()
            }
            return
        }

//        disposable = Observable.fromCallable {
//            loader.loadToplist(countryCode, NUM_SUGGESTIONS, DBReader.getFeedList())
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(
//                { podcasts: List<PodcastSearchResult> ->
//                    errorView.visibility = View.GONE
//                    if (podcasts.isEmpty()) {
//                        errorTextView.text = resources.getText(R.string.search_status_no_results)
//                        errorView.visibility = View.VISIBLE
//                        discoverGridLayout.visibility = View.INVISIBLE
//                    } else {
//                        discoverGridLayout.visibility = View.VISIBLE
//                        adapter.updateData(podcasts)
//                    }
//                }, { error: Throwable ->
//                    Log.e(TAG, Log.getStackTraceString(error))
//                    errorTextView.text = error.localizedMessage
//                    errorView.visibility = View.VISIBLE
//                    discoverGridLayout.visibility = View.INVISIBLE
//                    errorRetry.visibility = View.VISIBLE
//                    errorRetry.setOnClickListener { loadToplist() }
//                })

        lifecycleScope.launch {
            try {
                val podcasts = withContext(Dispatchers.IO) {
                    loader.loadToplist(countryCode, NUM_SUGGESTIONS, DBReader.getFeedList())
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

    companion object {
        private const val TAG = "FeedDiscoveryFragment"
        private const val NUM_SUGGESTIONS = 12
    }
}
