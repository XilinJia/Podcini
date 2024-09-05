package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FragmentSearchResultsBinding
import ac.mdiq.podcini.databinding.QuickFeedDiscoveryBinding
import ac.mdiq.podcini.databinding.QuickFeedDiscoveryItemBinding
import ac.mdiq.podcini.databinding.SelectCountryDialogBinding
import ac.mdiq.podcini.net.feed.discovery.ItunesTopListLoader
import ac.mdiq.podcini.net.feed.discovery.ItunesTopListLoader.Companion.prefs
import ac.mdiq.podcini.net.feed.discovery.PodcastSearchResult
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.OnlineFeedsAdapter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.content.DialogInterface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
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
        _binding = null
        super.onDestroy()
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

        val fragment: Fragment = OnlineFeedFragment.newInstance(podcast!!.feedUrl!!)
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

    /**
     * Searches iTunes store for top podcasts and displays results in a list.
     */
    class DiscoveryFragment : Fragment(), Toolbar.OnMenuItemClickListener {
        private var _binding: FragmentSearchResultsBinding? = null
        private val binding get() = _binding!!

        //    private lateinit var prefs: SharedPreferences
        private lateinit var gridView: GridView
        private lateinit var progressBar: ProgressBar
        private lateinit var txtvError: TextView
        private lateinit var butRetry: Button
        private lateinit var txtvEmpty: TextView
        private lateinit var toolbar: MaterialToolbar

        /**
         * Adapter responsible with the search results.
         */
        private var adapter: OnlineFeedsAdapter? = null

        /**
         * List of podcasts retreived from the search.
         */
        private var searchResults: List<PodcastSearchResult>? = null
        private var topList: List<PodcastSearchResult>? = null

        private var countryCode: String? = "US"
        private var hidden = false
        private var needsConfirm = false

        /**
         * Replace adapter data with provided search results from SearchTask.
         *
         * @param result List of Podcast objects containing search results
         */
        private fun updateData(result: List<PodcastSearchResult>?) {
            this.searchResults = result
            adapter?.clear()
            if (!result.isNullOrEmpty()) {
                gridView.visibility = View.VISIBLE
                txtvEmpty.visibility = View.GONE
                for (p in result) {
                    adapter!!.add(p)
                }
                adapter?.notifyDataSetInvalidated()
            } else {
                gridView.visibility = View.GONE
                txtvEmpty.visibility = View.VISIBLE
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
//        prefs = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
            countryCode = prefs!!.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)
            hidden = prefs!!.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)
            needsConfirm = prefs!!.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
        }

        @OptIn(UnstableApi::class) override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            // Inflate the layout for this fragment
            _binding = FragmentSearchResultsBinding.inflate(inflater)
//        val root = inflater.inflate(R.layout.fragment_itunes_search, container, false)

            Logd(TAG, "fragment onCreateView")
            gridView = binding.gridView
            adapter = OnlineFeedsAdapter(requireActivity(), ArrayList())
            gridView.setAdapter(adapter)

            toolbar = binding.toolbar
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
            toolbar.inflateMenu(R.menu.countries_menu)
            val discoverHideItem = toolbar.menu.findItem(R.id.discover_hide_item)
            discoverHideItem.setChecked(hidden)

            toolbar.setOnMenuItemClickListener(this)

            //Show information about the podcast when the list item is clicked
            gridView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val podcast = searchResults!![position]
                if (podcast.feedUrl == null) return@OnItemClickListener

                val fragment: Fragment = OnlineFeedFragment.newInstance(podcast.feedUrl)
                (activity as MainActivity).loadChildFragment(fragment)
            }

            progressBar = binding.progressBar
            txtvError = binding.txtvError
            butRetry = binding.butRetry
            txtvEmpty = binding.empty

            loadToplist(countryCode)
            return binding.root
        }

        override fun onDestroy() {
            _binding = null
            adapter = null
            searchResults = null
            topList = null
            super.onDestroy()
        }

        private fun loadToplist(country: String?) {
            gridView.visibility = View.GONE
            txtvError.visibility = View.GONE
            butRetry.visibility = View.GONE
            butRetry.setText(R.string.retry_label)
            txtvEmpty.visibility = View.GONE
            progressBar.visibility = View.VISIBLE

            if (hidden) {
                gridView.visibility = View.GONE
                txtvError.visibility = View.VISIBLE
                txtvError.text = resources.getString(R.string.discover_is_hidden)
                butRetry.visibility = View.GONE
                txtvEmpty.visibility = View.GONE
                progressBar.visibility = View.GONE
                return
            }
            if (BuildConfig.FLAVOR == "free" && needsConfirm) {
                txtvError.visibility = View.VISIBLE
                txtvError.text = ""
                butRetry.visibility = View.VISIBLE
                butRetry.setText(R.string.discover_confirm)
                butRetry.setOnClickListener {
                    prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                    needsConfirm = false
                    loadToplist(country)
                }
                txtvEmpty.visibility = View.GONE
                progressBar.visibility = View.GONE
                return
            }

            val loader = ItunesTopListLoader(requireContext())
            lifecycleScope.launch {
                try {
                    val podcasts = withContext(Dispatchers.IO) {
                        loader.loadToplist(country?:"", NUM_OF_TOP_PODCASTS, getFeedList())
                    }
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        topList = podcasts
                        updateData(topList)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    progressBar.visibility = View.GONE
                    txtvError.text = e.message
                    txtvError.visibility = View.VISIBLE
                    butRetry.setOnClickListener { loadToplist(country) }
                    butRetry.visibility = View.VISIBLE
                }
            }

        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            if (super.onOptionsItemSelected(item)) return true

            val itemId = item.itemId
            when (itemId) {
                R.id.discover_hide_item -> {
                    item.setChecked(!item.isChecked)
                    hidden = item.isChecked
                    prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()

                    EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                    loadToplist(countryCode)
                    return true
                }
                R.id.discover_countries_item -> {
                    val inflater = layoutInflater
                    val selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null)
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setView(selectCountryDialogView)

                    val countryCodeArray: List<String> = listOf(*Locale.getISOCountries())
                    val countryCodeNames: MutableMap<String?, String> = HashMap()
                    val countryNameCodes: MutableMap<String, String> = HashMap()
                    for (code in countryCodeArray) {
                        val locale = Locale("", code)
                        val countryName = locale.displayCountry
                        countryCodeNames[code] = countryName
                        countryNameCodes[countryName] = code
                    }

                    val countryNamesSort: MutableList<String> = ArrayList(countryCodeNames.values)
                    countryNamesSort.sort()

                    val dataAdapter = ArrayAdapter(this.requireContext(), android.R.layout.simple_list_item_1, countryNamesSort)
                    val scBinding = SelectCountryDialogBinding.bind(selectCountryDialogView)
                    val textInput = scBinding.countryTextInput
                    val editText = textInput.editText as? MaterialAutoCompleteTextView
                    editText!!.setAdapter(dataAdapter)
                    editText.setText(countryCodeNames[countryCode])
                    editText.setOnClickListener {
                        if (editText.text.isNotEmpty()) {
                            editText.setText("")
                            editText.postDelayed({ editText.showDropDown() }, 100)
                        }
                    }
                    editText.onFocusChangeListener = View.OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                        if (hasFocus) {
                            editText.setText("")
                            editText.postDelayed({ editText.showDropDown() }, 100)
                        }
                    }

                    builder.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        val countryName = editText.text.toString()
                        if (countryNameCodes.containsKey(countryName)) {
                            countryCode = countryNameCodes[countryName]
                            val discoverHideItem = toolbar.menu.findItem(R.id.discover_hide_item)
                            discoverHideItem.setChecked(false)
                            hidden = false
                        }

                        prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()
                        prefs!!.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode).apply()

                        EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                        loadToplist(countryCode)
                    }
                    builder.setNegativeButton(R.string.cancel_label, null)
                    builder.show()
                    return true
                }
                else -> return false
            }
        }

        companion object {
            private val TAG: String = DiscoveryFragment::class.simpleName ?: "Anonymous"
            private const val NUM_OF_TOP_PODCASTS = 25
        }
    }

    companion object {
        private val TAG: String = QuickDiscoveryFragment::class.simpleName ?: "Anonymous"
        private const val NUM_SUGGESTIONS = 12
    }
}
