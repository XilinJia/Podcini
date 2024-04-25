package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.NavListitemBinding
import ac.mdiq.podcini.databinding.NavSectionItemBinding
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.preferences.UserPreferences.subscriptionsFilter
import ac.mdiq.podcini.storage.NavDrawerData.FeedDrawerItem
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnCreateContextMenuListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.apache.commons.lang3.ArrayUtils
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

/**
 * BaseAdapter for the navigation drawer
 */
@OptIn(UnstableApi::class)
class NavListAdapter(private val itemAccess: ItemAccess, context: Activity) :
    RecyclerView.Adapter<NavListAdapter.Holder>(), OnSharedPreferenceChangeListener {

    private val fragmentTags: MutableList<String?> = ArrayList()
    private val titles: Array<String> = context.resources.getStringArray(R.array.nav_drawer_titles)
    private val activity = WeakReference(context)
    @JvmField
    var showSubscriptionList: Boolean = true

    init {
        loadItems()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (UserPreferences.PREF_HIDDEN_DRAWER_ITEMS == key) loadItems()
    }

    @OptIn(UnstableApi::class) private fun loadItems() {
        val newTags: MutableList<String?> = ArrayList(listOf(*NavDrawerFragment.NAV_DRAWER_TAGS))
        val hiddenFragments = hiddenDrawerItems
        newTags.removeAll(hiddenFragments)

        if (newTags.contains(SUBSCRIPTION_LIST_TAG)) {
            // we never want SUBSCRIPTION_LIST_TAG to be in 'tags'
            // since it doesn't actually correspond to a position in the list, but is
            // a placeholder that indicates if we should show the subscription list in the
            // nav drawer at all.
            showSubscriptionList = true
            newTags.remove(SUBSCRIPTION_LIST_TAG)
        } else showSubscriptionList = false

        fragmentTags.clear()
        fragmentTags.addAll(newTags)
        notifyDataSetChanged()
    }

    fun getLabel(tag: String?): String {
        val index = ArrayUtils.indexOf(NavDrawerFragment.NAV_DRAWER_TAGS, tag)
        return titles[index]
    }

    @UnstableApi @DrawableRes
    private fun getDrawable(tag: String?): Int {
        return when (tag) {
            QueueFragment.TAG -> R.drawable.ic_playlist_play
            AllEpisodesFragment.TAG -> R.drawable.ic_feed
            DownloadsFragment.TAG -> R.drawable.ic_download
            PlaybackHistoryFragment.TAG -> R.drawable.ic_history
            SubscriptionFragment.TAG -> R.drawable.ic_subscriptions
            StatisticsFragment.TAG -> R.drawable.ic_chart_box
            AddFeedFragment.TAG -> R.drawable.ic_add
            else -> 0
        }
    }

    fun getFragmentTags(): List<String?> {
        return Collections.unmodifiableList(fragmentTags)
    }

    override fun getItemCount(): Int {
        var baseCount = subscriptionOffset
        if (showSubscriptionList) baseCount += itemAccess.count
        return baseCount
    }

    override fun getItemId(position: Int): Long {
        val viewType = getItemViewType(position)
        return when (viewType) {
            VIEW_TYPE_SUBSCRIPTION -> itemAccess.getItem(position - subscriptionOffset)?.id?:0
            VIEW_TYPE_NAV -> (-abs(fragmentTags[position].hashCode().toLong().toDouble()) - 1).toLong() // Folder IDs are >0
            else -> 0
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            0 <= position && position < fragmentTags.size -> VIEW_TYPE_NAV
            position < subscriptionOffset -> VIEW_TYPE_SECTION_DIVIDER
            else -> VIEW_TYPE_SUBSCRIPTION
        }
    }

    val subscriptionOffset: Int
        get() = if (fragmentTags.size > 0) fragmentTags.size + 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(activity.get())
        return when (viewType) {
            VIEW_TYPE_NAV -> NavHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
            VIEW_TYPE_SECTION_DIVIDER -> DividerHolder(inflater.inflate(R.layout.nav_section_item, parent, false))
            else -> FeedHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
        }
    }

    @UnstableApi override fun onBindViewHolder(holder: Holder, position: Int) {
        val viewType = getItemViewType(position)

        holder.itemView.setOnCreateContextMenuListener(null)
        when (viewType) {
            VIEW_TYPE_NAV -> bindNavView(getLabel(fragmentTags[position]), position, holder as NavHolder)
            VIEW_TYPE_SECTION_DIVIDER -> bindSectionDivider(holder as DividerHolder)
            else -> {
                val itemPos = position - subscriptionOffset
                val item = itemAccess.getItem(itemPos)
                if (item != null) {
                    bindListItem(item, holder as FeedHolder)
                    bindFeedView(item, holder)
                }
                holder.itemView.setOnCreateContextMenuListener(itemAccess)
            }
        }
        if (viewType != VIEW_TYPE_SECTION_DIVIDER) {
            holder.itemView.isSelected = itemAccess.isSelected(position)
            holder.itemView.setOnClickListener { itemAccess.onItemClick(position) }
            holder.itemView.setOnLongClickListener { itemAccess.onItemLongClick(position) }
            holder.itemView.setOnTouchListener { _: View?, e: MotionEvent ->
                if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    itemAccess.onItemLongClick(position)
                    return@setOnTouchListener false
                }
                false
            }
        }
    }

    @UnstableApi private fun bindNavView(title: String, position: Int, holder: NavHolder) {
        val context = activity.get() ?: return
        holder.title.text = title

        // reset for re-use
        holder.count.visibility = View.GONE
        holder.count.setOnClickListener(null)
        holder.count.isClickable = false

        val tag = fragmentTags[position]
        when {
            tag == QueueFragment.TAG -> {
                val queueSize = itemAccess.queueSize
                if (queueSize > 0) {
                    holder.count.text = NumberFormat.getInstance().format(queueSize.toLong())
                    holder.count.visibility = View.VISIBLE
                }
            }
//            tag == InboxFragment.TAG -> {
//                val unreadItems = itemAccess.numberOfNewItems
//                if (unreadItems > 0) {
//                    holder.count.text = NumberFormat.getInstance().format(unreadItems.toLong())
//                    holder.count.visibility = View.VISIBLE
//                }
//            }
            tag == SubscriptionFragment.TAG -> {
                val sum = itemAccess.feedCounterSum
                if (sum > 0) {
                    holder.count.text = NumberFormat.getInstance().format(sum.toLong())
                    holder.count.visibility = View.VISIBLE
                }
            }
            tag == DownloadsFragment.TAG && isEnableAutodownload -> {
                val epCacheSize = episodeCacheSize
                // don't count episodes that can be reclaimed
                val spaceUsed = (itemAccess.numberOfDownloadedItems - itemAccess.reclaimableItems)
                if (epCacheSize in 1..spaceUsed) {
                    holder.count.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_disc_alert, 0)
                    holder.count.visibility = View.VISIBLE
                    holder.count.setOnClickListener {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.episode_cache_full_title)
                            .setMessage(R.string.episode_cache_full_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.open_autodownload_settings) { _: DialogInterface?, _: Int ->
                                val intent = Intent(context, PreferenceActivity::class.java)
                                intent.putExtra(PreferenceActivity.OPEN_AUTO_DOWNLOAD_SETTINGS, true)
                                context.startActivity(intent)
                            }
                            .show()
                    }
                }
            }
        }

        holder.image.setImageResource(getDrawable(fragmentTags[position]))
    }

    private fun bindSectionDivider(holder: DividerHolder) {
//        val context = activity.get() ?: return

        if (subscriptionsFilter.isEnabled && showSubscriptionList) {
            holder.itemView.isEnabled = true
            holder.feedsFilteredMsg.visibility = View.VISIBLE
        } else {
            holder.itemView.isEnabled = false
            holder.feedsFilteredMsg.visibility = View.GONE
        }
    }

    private fun bindListItem(item: FeedDrawerItem, holder: FeedHolder) {
        if (item.counter > 0) {
            holder.count.visibility = View.VISIBLE
            holder.count.text = NumberFormat.getInstance().format(item.counter.toLong())
        } else holder.count.visibility = View.GONE

        holder.title.text = item.title
        val padding = (activity.get()!!.resources.getDimension(R.dimen.thumbnail_length_navlist) / 2).toInt()
        holder.itemView.setPadding(item.layer * padding, 0, 0, 0)
    }

    private fun bindFeedView(drawerItem: FeedDrawerItem, holder: FeedHolder) {
        val feed = drawerItem.feed
        val context = activity.get() ?: return

        if (!feed.imageUrl.isNullOrBlank()) Glide.with(context)
            .load(feed.imageUrl)
            .apply(RequestOptions()
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .transform(FitCenter(),
                    RoundedCorners((4 * context.resources.displayMetrics.density).toInt()))
                .dontAnimate())
            .into(holder.image)

        if (feed.hasLastUpdateFailed()) {
            val p = holder.title.layoutParams as RelativeLayout.LayoutParams
            p.addRule(RelativeLayout.LEFT_OF, R.id.itxtvFailure)
            holder.failure.visibility = View.VISIBLE
        } else {
            val p = holder.title.layoutParams as RelativeLayout.LayoutParams
            p.addRule(RelativeLayout.LEFT_OF, R.id.txtvCount)
            holder.failure.visibility = View.GONE
        }
    }

//    private fun bindTagView(tag: TagDrawerItem, holder: FeedHolder) {
//        val context = activity.get() ?: return
//        if (tag.isOpen) {
//            holder.count.visibility = View.GONE
//        }
//        Glide.with(context).clear(holder.image)
//        holder.image.setImageResource(R.drawable.ic_tag)
//        holder.failure.visibility = View.GONE
//    }

    open class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    internal class DividerHolder(itemView: View) : Holder(itemView) {
        val binding = NavSectionItemBinding.bind(itemView)
        val feedsFilteredMsg: LinearLayout = binding.navFeedsFilteredMessage
    }

    internal class NavHolder(itemView: View) : Holder(itemView) {
        val binding = NavListitemBinding.bind(itemView)
        val image: ImageView = binding.imgvCover
        val title: TextView = binding.txtvTitle
        val count: TextView = binding.txtvCount
    }

    internal class FeedHolder(itemView: View) : Holder(itemView) {
        val binding = NavListitemBinding.bind(itemView)
        val image: ImageView = binding.imgvCover
        val title: TextView = binding.txtvTitle
        val failure: ImageView = binding.itxtvFailure
        val count: TextView = binding.txtvCount
    }

    interface ItemAccess : OnCreateContextMenuListener {
        val count: Int

        fun getItem(position: Int): FeedDrawerItem?

        fun isSelected(position: Int): Boolean

        val queueSize: Int

        val numberOfNewItems: Int

        val numberOfDownloadedItems: Int

        val reclaimableItems: Int

        val feedCounterSum: Int

        fun onItemClick(position: Int)

        fun onItemLongClick(position: Int): Boolean

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?)
    }

    companion object {
        const val VIEW_TYPE_NAV: Int = 0
        const val VIEW_TYPE_SECTION_DIVIDER: Int = 1
        private const val VIEW_TYPE_SUBSCRIPTION = 2

        /**
         * a tag used as a placeholder to indicate if the subscription list should be displayed or not
         * This tag doesn't correspond to any specific activity.
         */
        const val SUBSCRIPTION_LIST_TAG: String = "SubscriptionList"
    }
}
