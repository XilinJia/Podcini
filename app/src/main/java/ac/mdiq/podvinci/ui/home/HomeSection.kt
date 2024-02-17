package ac.mdiq.podvinci.ui.home

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnCreateContextMenuListener
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import ac.mdiq.podvinci.adapter.EpisodeItemListAdapter
import ac.mdiq.podvinci.adapter.HorizontalFeedListAdapter
import ac.mdiq.podvinci.adapter.HorizontalItemListAdapter
import ac.mdiq.podvinci.databinding.HomeSectionBinding
import ac.mdiq.podvinci.menuhandler.FeedItemMenuHandler
import ac.mdiq.podvinci.menuhandler.FeedMenuHandler
import ac.mdiq.podvinci.model.feed.FeedItem
import org.greenrobot.eventbus.EventBus
import java.util.*

/**
 * Section on the HomeFragment
 */
abstract class HomeSection : Fragment(), OnCreateContextMenuListener {
    protected lateinit var viewBinding: HomeSectionBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = HomeSectionBinding.inflate(inflater)
        viewBinding.titleLabel.text = sectionTitle
        if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR) {
            viewBinding.moreButton.text = "$moreLinkTitle\u00A0»"
        } else {
            viewBinding.moreButton.text = "«\u00A0$moreLinkTitle"
        }
        viewBinding.moreButton.setOnClickListener { view: View? -> handleMoreClick() }
        if (TextUtils.isEmpty(moreLinkTitle)) {
            viewBinding.moreButton.visibility = View.INVISIBLE
        }
        // Dummies are necessary to ensure height, but do not animate them
        viewBinding.recyclerView.itemAnimator = null
        viewBinding.recyclerView.postDelayed(
            { viewBinding.recyclerView.itemAnimator = DefaultItemAnimator() }, 500)
        return viewBinding.root
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (!userVisibleHint || !isVisible || !isMenuVisible) {
            // The method is called on all fragments in a ViewPager, so this needs to be ignored in invisible ones.
            // Apparently, none of the visibility check method works reliably on its own, so we just use all.
            return false
        }
        if (viewBinding.recyclerView.adapter is HorizontalFeedListAdapter) {
            val adapter = viewBinding.recyclerView.adapter as? HorizontalFeedListAdapter
            val selectedFeed = adapter?.longPressedItem
            return (selectedFeed != null && FeedMenuHandler.onMenuItemClicked(this, item.itemId, selectedFeed) {})
        }

        var longPressedItem: FeedItem? = null
        if (viewBinding.recyclerView.adapter is EpisodeItemListAdapter) {
            val adapter = viewBinding.recyclerView.adapter as? EpisodeItemListAdapter
            longPressedItem = adapter?.longPressedItem
        } else if (viewBinding.recyclerView.adapter is HorizontalItemListAdapter) {
            val adapter = viewBinding.recyclerView.adapter as HorizontalItemListAdapter?
            longPressedItem = adapter?.longPressedItem
        } else {
            return false
        }

        if (longPressedItem == null) {
            Log.i(TAG, "Selected item or listAdapter was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, longPressedItem)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        registerForContextMenu(viewBinding.recyclerView)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        unregisterForContextMenu(viewBinding.recyclerView)
    }

    protected abstract val sectionTitle: String?

    protected abstract val moreLinkTitle: String

    protected abstract fun handleMoreClick()

    companion object {
        const val TAG: String = "HomeSection"
    }
}
