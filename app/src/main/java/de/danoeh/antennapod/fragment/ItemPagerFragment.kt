package de.danoeh.antennapod.fragment

import de.danoeh.antennapod.activity.MainActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler
import de.danoeh.antennapod.model.feed.FeedItem
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
class ItemPagerFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var pager: ViewPager2? = null

    private var feedItems: LongArray? = null
    private var item: FeedItem? = null
    private var disposable: Disposable? = null
    private var toolbar: MaterialToolbar? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val layout: View = inflater.inflate(R.layout.feeditem_pager_fragment, container, false)
        toolbar = layout.findViewById(R.id.toolbar)
        toolbar?.title = ""
        toolbar?.inflateMenu(R.menu.feeditem_options)
        toolbar?.setNavigationOnClickListener { v: View? -> parentFragmentManager.popBackStack() }
        toolbar?.setOnMenuItemClickListener(this)

        feedItems = requireArguments().getLongArray(ARG_FEEDITEMS)
        val feedItemPos = max(0.0, requireArguments().getInt(ARG_FEEDITEM_POS).toDouble())
            .toInt()

        pager = layout.findViewById(R.id.pager)
        // FragmentStatePagerAdapter documentation:
        // > When using FragmentStatePagerAdapter the host ViewPager must have a valid ID set.
        // When opening multiple ItemPagerFragments by clicking "item" -> "visit podcast" -> "item" -> etc,
        // the ID is no longer unique and FragmentStatePagerAdapter does not display any pages.
        var newId = View.generateViewId()
        if (savedInstanceState != null && savedInstanceState.getInt(KEY_PAGER_ID, 0) != 0) {
            // Restore state by using the same ID as before. ID collisions are prevented in MainActivity.
            newId = savedInstanceState.getInt(KEY_PAGER_ID, 0)
        }
        pager?.setId(newId)
        pager?.adapter = ItemPagerAdapter(this)
        pager?.setCurrentItem(feedItemPos, false)
        pager?.offscreenPageLimit = 1
        loadItem(feedItems!![feedItemPos])
        pager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                loadItem(feedItems!![position])
            }
        })

        EventBus.getDefault().register(this)
        return layout
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pager != null) outState.putInt(KEY_PAGER_ID, pager!!.id)
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
        if (item == null || toolbar == null) {
            return
        }
        if (item!!.hasMedia()) {
            FeedItemMenuHandler.onPrepareMenu(toolbar!!.menu, item)
        } else {
            // these are already available via button1 and button2
            FeedItemMenuHandler.onPrepareMenu(toolbar!!.menu, item,
                R.id.mark_read_item, R.id.visit_website_item)
        }
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.open_podcast) {
            openPodcast()
            return true
        }
        if (item == null) return false
        return FeedItemMenuHandler.onMenuItemClicked(this, menuItem.itemId, item!!)
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

    private fun openPodcast() {
        if (item == null) {
            return
        }
        val fragment: Fragment = FeedItemlistFragment.newInstance(item!!.feedId)
        (activity as MainActivity).loadChildFragment(fragment)
    }

    private inner class ItemPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return ItemFragment.newInstance(feedItems!![position])
        }

        override fun getItemCount(): Int {
            return feedItems!!.size
        }
    }

    companion object {
        private const val ARG_FEEDITEMS = "feeditems"
        private const val ARG_FEEDITEM_POS = "feeditem_pos"
        private const val KEY_PAGER_ID = "pager_id"

        /**
         * Creates a new instance of an ItemPagerFragment.
         *
         * @param feeditems   The IDs of the FeedItems that belong to the same list
         * @param feedItemPos The position of the FeedItem that is currently shown
         * @return The ItemFragment instance
         */
        fun newInstance(feeditems: LongArray?, feedItemPos: Int): ItemPagerFragment {
            val fragment = ItemPagerFragment()
            val args = Bundle()
            args.putLongArray(ARG_FEEDITEMS, feeditems)
            args.putInt(ARG_FEEDITEM_POS, max(0.0, feedItemPos.toDouble()).toInt())
            fragment.arguments = args
            return fragment
        }
    }
}
