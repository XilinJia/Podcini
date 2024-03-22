package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeeditemPageFragmentBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.util.event.FeedItemEvent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max

/**
 * Displays information about a list of FeedItems.
 */
class ItemPageFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private lateinit var page: View
    private lateinit var toolbar: MaterialToolbar

    private lateinit var itemFragment: ItemFragment

    private var feedItems: LongArray? = null
    private var item: FeedItem? = null
    private var disposable: Disposable? = null
    
    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val binding = FeeditemPageFragmentBinding.inflate(inflater)

        Log.d(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.feeditem_options)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)

        feedItems = requireArguments().getLongArray(ARG_FEEDITEMS)
        val feedItemPos = max(0.0, requireArguments().getInt(ARG_FEEDITEM_POS).toDouble()).toInt()

        page = binding.fragmentView
        loadItem(feedItems!![feedItemPos])
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        itemFragment = ItemFragment.newInstance(if (feedItems!= null) feedItems!![feedItemPos] else 0L)
        transaction.replace(R.id.fragment_view, itemFragment)
        transaction.commit()

        EventBus.getDefault().register(this)
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGER_ID, page.id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
    }

    @UnstableApi private fun loadItem(itemId: Long) {
        disposable?.dispose()

        disposable = Observable.fromCallable { DBReader.getFeedItem(itemId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: FeedItem? ->
                item = result
                refreshToolbarState()
            }, { obj: Throwable -> obj.printStackTrace() })
    }

    @UnstableApi fun refreshToolbarState() {
        if (item == null) return

        if (item!!.hasMedia()) {
            FeedItemMenuHandler.onPrepareMenu(toolbar.menu, item)
        } else {
            // these are already available via button1 and button2
            FeedItemMenuHandler.onPrepareMenu(toolbar.menu, item,
                R.id.mark_read_item, R.id.visit_website_item)
        }
    }

    @UnstableApi override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.open_podcast -> {
                openPodcast()
                return true
            }
            R.id.share_notes -> {
                if (item == null) return false
                val bundle = itemFragment.arguments
                val notes = bundle?.getString("description", "")
                if (!notes.isNullOrEmpty()) {
                    val shareText = if (Build.VERSION.SDK_INT >= 24) Html.fromHtml(notes, Html.FROM_HTML_MODE_LEGACY).toString()
                    else Html.fromHtml(notes).toString()
                    val context = requireContext()
                    val intent = ShareCompat.IntentBuilder(context)
                        .setType("text/plain")
                        .setText(shareText)
                        .setChooserTitle(R.string.share_notes_label)
                        .createChooserIntent()
                    context.startActivity(intent)
                }
                return true
            }
            else -> {
                if (item == null) return false
                return FeedItemMenuHandler.onMenuItemClicked(this, menuItem.itemId, item!!)
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        for (item in event.items) {
            if (this.item != null && this.item!!.id == item.id) {
                this.item = item
                refreshToolbarState()
                return
            }
        }
    }

    @UnstableApi private fun openPodcast() {
        if (item == null) return

        val fragment: Fragment = FeedItemlistFragment.newInstance(item!!.feedId)
        (activity as MainActivity).loadChildFragment(fragment)
    }

    companion object {
        const val TAG: String = "ItemPageFragment"
        private const val ARG_FEEDITEMS = "feeditems"
        private const val ARG_FEEDITEM_POS = "feeditem_pos"
        private const val KEY_PAGER_ID = "pager_id"

        /**
         * Creates a new instance of an ItemPageFragment.
         *
         * @param feeditems   The IDs of the FeedItems that belong to the same list
         * @param feedItemPos The position of the FeedItem that is currently shown
         * @return The ItemFragment instance
         */
        fun newInstance(feeditems: LongArray?, feedItemPos: Int): ItemPageFragment {
            val fragment = ItemPageFragment()
            val args = Bundle()
            if (feeditems != null) args.putLongArray(ARG_FEEDITEMS, feeditems)
            args.putInt(ARG_FEEDITEM_POS, max(0.0, feedItemPos.toDouble()).toInt())
            fragment.arguments = args
            return fragment
        }
    }
}