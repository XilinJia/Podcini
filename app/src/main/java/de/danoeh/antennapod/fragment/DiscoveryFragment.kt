package de.danoeh.antennapod.fragment

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
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
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.OnlineFeedViewActivity
import de.danoeh.antennapod.adapter.itunes.ItunesAdapter
import de.danoeh.antennapod.core.BuildConfig
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.event.DiscoveryDefaultUpdateEvent
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader
import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.util.*

/**
 * Searches iTunes store for top podcasts and displays results in a list.
 */
class DiscoveryFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var prefs: SharedPreferences? = null

    /**
     * Adapter responsible with the search results.
     */
    private var adapter: ItunesAdapter? = null
    private var gridView: GridView? = null
    private var progressBar: ProgressBar? = null
    private var txtvError: TextView? = null
    private var butRetry: Button? = null
    private var txtvEmpty: TextView? = null

    /**
     * List of podcasts retreived from the search.
     */
    private var searchResults: List<PodcastSearchResult>? = null
    private var topList: List<PodcastSearchResult>? = null
    private var disposable: Disposable? = null
    private var countryCode: String? = "US"
    private var hidden = false
    private var needsConfirm = false
    private var toolbar: MaterialToolbar? = null

    /**
     * Replace adapter data with provided search results from SearchTask.
     *
     * @param result List of Podcast objects containing search results
     */
    private fun updateData(result: List<PodcastSearchResult>?) {
        this.searchResults = result
        adapter!!.clear()
        if (result != null && result.size > 0) {
            gridView!!.visibility = View.VISIBLE
            txtvEmpty!!.visibility = View.GONE
            for (p in result) {
                adapter!!.add(p)
            }
            adapter!!.notifyDataSetInvalidated()
        } else {
            gridView!!.visibility = View.GONE
            txtvEmpty!!.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
        if (prefs != null) {
            countryCode = prefs!!.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)
            hidden = prefs!!.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)
            needsConfirm = prefs!!.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_itunes_search, container, false)
        gridView = root.findViewById(R.id.gridView)
        adapter = ItunesAdapter(requireActivity(), ArrayList())
        gridView?.setAdapter(adapter)

        toolbar = root.findViewById(R.id.toolbar)
        toolbar?.setNavigationOnClickListener(View.OnClickListener { v: View? -> parentFragmentManager.popBackStack() })
        toolbar?.inflateMenu(R.menu.countries_menu)
        if (toolbar != null) {
            val discoverHideItem = toolbar!!.getMenu().findItem(R.id.discover_hide_item)
            discoverHideItem.setChecked(hidden)
        }
        toolbar?.setOnMenuItemClickListener(this)

        //Show information about the podcast when the list item is clicked
        gridView?.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, view1: View?, position: Int, id: Long ->
            val podcast = searchResults!![position]
            if (podcast.feedUrl == null) {
                return@OnItemClickListener
            }
            val intent = Intent(activity, OnlineFeedViewActivity::class.java)
            intent.putExtra(OnlineFeedViewActivity.ARG_FEEDURL, podcast.feedUrl)
            startActivity(intent)
        })

        progressBar = root.findViewById(R.id.progressBar)
        txtvError = root.findViewById(R.id.txtvError)
        butRetry = root.findViewById(R.id.butRetry)
        txtvEmpty = root.findViewById(android.R.id.empty)

        loadToplist(countryCode)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
        adapter = null
    }

    private fun loadToplist(country: String?) {
        if (disposable != null) {
            disposable!!.dispose()
        }

        gridView!!.visibility = View.GONE
        txtvError!!.visibility = View.GONE
        butRetry!!.visibility = View.GONE
        butRetry!!.setText(R.string.retry_label)
        txtvEmpty!!.visibility = View.GONE
        progressBar!!.visibility = View.VISIBLE

        if (hidden) {
            gridView!!.visibility = View.GONE
            txtvError!!.visibility = View.VISIBLE
            txtvError!!.text = resources.getString(R.string.discover_is_hidden)
            butRetry!!.visibility = View.GONE
            txtvEmpty!!.visibility = View.GONE
            progressBar!!.visibility = View.GONE
            return
        }
        if (BuildConfig.FLAVOR == "free" && needsConfirm) {
            txtvError!!.visibility = View.VISIBLE
            txtvError!!.text = ""
            butRetry!!.visibility = View.VISIBLE
            butRetry!!.setText(R.string.discover_confirm)
            butRetry!!.setOnClickListener { v: View? ->
                prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                needsConfirm = false
                loadToplist(country)
            }
            txtvEmpty!!.visibility = View.GONE
            progressBar!!.visibility = View.GONE
            return
        }

        val loader = ItunesTopListLoader(requireContext())
        disposable =
            Observable.fromCallable { loader.loadToplist(country?:"", NUM_OF_TOP_PODCASTS, DBReader.getFeedList()) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { podcasts: List<PodcastSearchResult>? ->
                        progressBar!!.visibility = View.GONE
                        topList = podcasts
                        updateData(topList)
                    }, { error: Throwable ->
                        Log.e(TAG, Log.getStackTraceString(error))
                        progressBar!!.visibility = View.GONE
                        txtvError!!.text = error.message
                        txtvError!!.visibility = View.VISIBLE
                        butRetry!!.setOnClickListener { v: View? -> loadToplist(country) }
                        butRetry!!.visibility = View.VISIBLE
                    })
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.discover_hide_item) {
            item.setChecked(!item.isChecked)
            hidden = item.isChecked
            prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()

            EventBus.getDefault().post(DiscoveryDefaultUpdateEvent())
            loadToplist(countryCode)
            return true
        } else if (itemId == R.id.discover_countries_item) {
            val inflater = layoutInflater
            val selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null)
            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setView(selectCountryDialogView)

            val countryCodeArray: List<String> = ArrayList(Arrays.asList(*Locale.getISOCountries()))
            val countryCodeNames: MutableMap<String?, String> = HashMap()
            val countryNameCodes: MutableMap<String, String> = HashMap()
            for (code in countryCodeArray) {
                val locale = Locale("", code)
                val countryName = locale.displayCountry
                countryCodeNames[code] = countryName
                countryNameCodes[countryName] = code
            }

            val countryNamesSort: List<String> = ArrayList(countryCodeNames.values)
            Collections.sort(countryNamesSort)

            val dataAdapter =
                ArrayAdapter(this.requireContext(), android.R.layout.simple_list_item_1, countryNamesSort)
            val textInput = selectCountryDialogView.findViewById<TextInputLayout>(R.id.country_text_input)
            val editText = textInput.editText as MaterialAutoCompleteTextView?
            editText!!.setAdapter(dataAdapter)
            editText.setText(countryCodeNames[countryCode])
            editText.setOnClickListener { view: View? ->
                if (editText.text.length != 0) {
                    editText.setText("")
                    editText.postDelayed({ editText.showDropDown() }, 100)
                }
            }
            editText.onFocusChangeListener = OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                if (hasFocus) {
                    editText.setText("")
                    editText.postDelayed({ editText.showDropDown() }, 100)
                }
            }

            builder.setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                val countryName = editText.text.toString()
                if (countryNameCodes.containsKey(countryName)) {
                    countryCode = countryNameCodes[countryName]
                    val discoverHideItem = toolbar!!.menu.findItem(R.id.discover_hide_item)
                    discoverHideItem.setChecked(false)
                    hidden = false
                }

                prefs!!.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()
                prefs!!.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode).apply()

                EventBus.getDefault().post(DiscoveryDefaultUpdateEvent())
                loadToplist(countryCode)
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.show()
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "ItunesSearchFragment"
        private const val NUM_OF_TOP_PODCASTS = 25
    }
}
