package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.NavListitemBinding
import ac.mdiq.podcini.databinding.NavSectionItemBinding
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.storage.NavDrawerData.FeedDrawerItem
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.util.Logd
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnCreateContextMenuListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
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
    var showSubscriptionList: Boolean = false

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
        return subscriptionOffset
    }

    override fun getItemId(position: Int): Long {
        val viewType = getItemViewType(position)
        return when (viewType) {
            VIEW_TYPE_NAV -> (-abs(fragmentTags[position].hashCode().toLong().toDouble()) - 1).toLong() // Folder IDs are >0
            else -> 0
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            0 <= position && position < fragmentTags.size -> VIEW_TYPE_NAV
            position < subscriptionOffset -> VIEW_TYPE_SECTION_DIVIDER
            else -> 0
        }
    }

    val subscriptionOffset: Int
        get() = if (fragmentTags.size > 0) fragmentTags.size + 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(activity.get())
        return when (viewType) {
            VIEW_TYPE_NAV -> NavHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
            else -> DividerHolder(inflater.inflate(R.layout.nav_section_item, parent, false))
        }
    }

    @UnstableApi override fun onBindViewHolder(holder: Holder, position: Int) {
        val viewType = getItemViewType(position)

        holder.itemView.setOnCreateContextMenuListener(null)
        when (viewType) {
            VIEW_TYPE_NAV -> bindNavView(getLabel(fragmentTags[position]), position, holder as NavHolder)
            else -> bindSectionDivider(holder as DividerHolder)
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
        when (tag) {
            SubscriptionFragment.TAG -> {
                val sum = itemAccess.numberOfFeeds
                if (sum > 0) {
                    holder.count.text = NumberFormat.getInstance().format(sum.toLong())
                    holder.count.visibility = View.VISIBLE
                }
            }
            QueueFragment.TAG -> {
                val queueSize = itemAccess.queueSize
                if (queueSize > 0) {
                    holder.count.text = NumberFormat.getInstance().format(queueSize.toLong())
                    holder.count.visibility = View.VISIBLE
                }
            }
            AllEpisodesFragment.TAG -> {
                val numEpisodes = itemAccess.numberOfItems
                if (numEpisodes > 0) {
                    holder.count.text = NumberFormat.getInstance().format(numEpisodes.toLong())
                    holder.count.visibility = View.VISIBLE
                }
            }
            DownloadsFragment.TAG -> {
                val epCacheSize = episodeCacheSize
                // don't count episodes that can be reclaimed
                val spaceUsed = (itemAccess.numberOfDownloadedItems - itemAccess.reclaimableItems)
                holder.count.text = NumberFormat.getInstance().format(spaceUsed.toLong())
                holder.count.visibility = View.VISIBLE
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

        Logd("NavListAdapter", "bindNavView getting drawable for: ${fragmentTags[position]}")
        holder.image.setImageResource(getDrawable(fragmentTags[position]))
    }

    private fun bindSectionDivider(holder: DividerHolder) {
        holder.itemView.isEnabled = false
        holder.feedsFilteredMsg.visibility = View.GONE
    }

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

    interface ItemAccess : OnCreateContextMenuListener {
        val count: Int

        fun getItem(position: Int): FeedDrawerItem?

        fun isSelected(position: Int): Boolean

        val queueSize: Int

        val numberOfNewItems: Int

        val numberOfItems: Int

        val numberOfDownloadedItems: Int

        val reclaimableItems: Int

        val feedCounterSum: Int

        val numberOfFeeds: Int

        fun onItemClick(position: Int)

        fun onItemLongClick(position: Int): Boolean

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?)
    }

    companion object {
        const val VIEW_TYPE_NAV: Int = 0
        const val VIEW_TYPE_SECTION_DIVIDER: Int = 1
    }
}
