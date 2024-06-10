package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FragmentItunesSearchBinding
import ac.mdiq.podcini.databinding.SelectCountryDialogBinding
import ac.mdiq.podcini.net.discovery.ItunesTopListLoader
import ac.mdiq.podcini.net.discovery.ItunesTopListLoader.Companion.prefs
import ac.mdiq.podcini.net.discovery.PodcastSearchResult
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.OnlineFeedsAdapter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.*
import java.util.*

/**
 * Searches iTunes store for top podcasts and displays results in a list.
 */
class DiscoveryFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: FragmentItunesSearchBinding? = null
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

//    val scope = CoroutineScope(Dispatchers.Main)
//    private var disposable: Disposable? = null
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
        _binding = FragmentItunesSearchBinding.inflate(inflater)
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
        gridView.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            val podcast = searchResults!![position]
            if (podcast.feedUrl == null) return@OnItemClickListener

            val fragment: Fragment = OnlineFeedViewFragment.newInstance(podcast.feedUrl)
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
        super.onDestroy()
        _binding = null
//        scope.cancel()
//        disposable?.dispose()

        adapter = null
    }

    private fun loadToplist(country: String?) {
//        disposable?.dispose()

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
                    loader.loadToplist(country?:"", NUM_OF_TOP_PODCASTS, DBReader.getFeedList())
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
                editText.onFocusChangeListener = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
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
        private const val TAG = "ItunesSearchFragment"
        private const val NUM_OF_TOP_PODCASTS = 25
    }
}
