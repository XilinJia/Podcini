package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FragmentOnlineSearchBinding
import ac.mdiq.podcini.net.feed.discovery.PodcastSearchResult
import ac.mdiq.podcini.net.feed.discovery.PodcastSearcher
import ac.mdiq.podcini.net.feed.discovery.PodcastSearcherRegistry
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.OnlineFeedsAdapter
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineSearchFragment : Fragment() {

    private var _binding: FragmentOnlineSearchBinding? = null
    private val binding get() = _binding!!

    private var adapter: OnlineFeedsAdapter? = null
    private var searchProvider: PodcastSearcher? = null
    
    private lateinit var gridView: GridView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtvError: TextView
    private lateinit var butRetry: Button
    private lateinit var txtvEmpty: TextView

    /**
     * List of podcasts retreived from the search
     */
    private var searchResults: MutableList<PodcastSearchResult>? = null
//    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        for (info in PodcastSearcherRegistry.searchProviders) {
            Logd(TAG, "searchProvider: $info")
            if (info.searcher.javaClass.getName() == requireArguments().getString(ARG_SEARCHER)) {
                searchProvider = info.searcher
                break
            }
        }
        if (searchProvider == null) Logd(TAG,"Podcast searcher not found")
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnlineSearchBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        gridView = binding.gridView
        adapter = OnlineFeedsAdapter(requireContext(), ArrayList())
        gridView.setAdapter(adapter)

        //Show information about the podcast when the list item is clicked
        gridView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            val podcast = searchResults!![position]
            if (podcast.feedUrl != null) {
                val fragment = OnlineFeedViewFragment.newInstance(podcast.feedUrl)
                fragment.feedSource = podcast.source
                (activity as MainActivity).loadChildFragment(fragment)
            }
        }
        progressBar = binding.progressBar
        txtvError = binding.txtvError
        butRetry = binding.butRetry
        txtvEmpty = binding.empty
        if (searchProvider != null) binding.searchPoweredBy.text = getString(R.string.search_powered_by, searchProvider!!.name)
        setupToolbar(binding.toolbar)

        gridView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
            override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {}
        })
        return binding.root
    }

    override fun onDestroy() {
        _binding = null
        searchResults = null
        adapter = null
        super.onDestroy()
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        toolbar.inflateMenu(R.menu.online_search)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val searchItem: MenuItem = toolbar.menu.findItem(R.id.action_search)
        val sv = searchItem.actionView as? SearchView
        if (sv != null) {
            sv.queryHint = getString(R.string.search_podcast_hint)
            sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    sv.clearFocus()
                    search(s)
                    return true
                }
                override fun onQueryTextChange(s: String): Boolean {
                    return false
                }
            })
            sv.setOnQueryTextFocusChangeListener(View.OnFocusChangeListener { view: View, hasFocus: Boolean ->
                if (hasFocus) showInputMethod(view.findFocus()) })
        }
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                requireActivity().supportFragmentManager.popBackStack()
                return true
            }
        })
        searchItem.expandActionView()
        if (requireArguments().getString(ARG_QUERY, null) != null)
            sv?.setQuery(requireArguments().getString(ARG_QUERY, null), true)
    }

    private fun search(query: String) {
        showOnlyProgressBar()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = searchProvider?.search(query)
                searchResults = result?.toMutableList()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter?.clear()
                    handleSearchResults()
                    txtvEmpty.text = getString(R.string.no_results_for_query) + query
                }
            } catch (e: Exception) { handleSearchError(e, query) }
        }
    }

    private fun handleSearchResults() {
        adapter?.addAll(searchResults!!)
        adapter?.notifyDataSetInvalidated()
        gridView.visibility = if (!searchResults.isNullOrEmpty()) View.VISIBLE else View.GONE
        txtvEmpty.visibility = if (searchResults.isNullOrEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleSearchError(e: Throwable, query: String) {
        Logd(TAG, "exception: ${e.message}")
        progressBar.visibility = View.GONE
        txtvError.text = e.toString()
        txtvError.visibility = View.VISIBLE
        butRetry.setOnClickListener { search(query) }
        butRetry.visibility = View.VISIBLE
    }

    private fun showOnlyProgressBar() {
        gridView.visibility = View.GONE
        txtvError.visibility = View.GONE
        butRetry.visibility = View.GONE
        txtvEmpty.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
    }

    private fun showInputMethod(view: View) {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }

    companion object {
        private val TAG: String = OnlineSearchFragment::class.simpleName ?: "Anonymous"
        private const val ARG_SEARCHER = "searcher"
        private const val ARG_QUERY = "query"

        @JvmOverloads
        fun newInstance(searchProvider: Class<out PodcastSearcher?>, query: String? = null): OnlineSearchFragment {
            val fragment = OnlineSearchFragment()
            val arguments = Bundle()
            arguments.putString(ARG_SEARCHER, searchProvider.name)
            arguments.putString(ARG_QUERY, query)
            fragment.arguments = arguments
            return fragment
        }
    }
}
