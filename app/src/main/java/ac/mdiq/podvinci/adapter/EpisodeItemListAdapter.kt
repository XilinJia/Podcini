package ac.mdiq.podvinci.adapter

import ac.mdiq.podvinci.activity.MainActivity
import android.R.color
import android.app.Activity
import android.os.Build
import android.view.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.util.FeedItemUtil
import ac.mdiq.podvinci.fragment.ItemPagerFragment
import ac.mdiq.podvinci.menuhandler.FeedItemMenuHandler
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.ui.common.ThemeUtils
import ac.mdiq.podvinci.view.viewholder.EpisodeItemViewHolder
import org.apache.commons.lang3.ArrayUtils
import java.lang.ref.WeakReference

/**
 * List adapter for the list of new episodes.
 */
open class EpisodeItemListAdapter(mainActivity: MainActivity) : SelectableAdapter<EpisodeItemViewHolder?>(mainActivity),
    View.OnCreateContextMenuListener {
    private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    private var episodes: List<FeedItem> = ArrayList()
    var longPressedItem: FeedItem? = null
    var longPressedPosition: Int = 0 // used to init actionMode
    private var dummyViews = 0

    init {
        setHasStableIds(true)
    }

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
        notifyDataSetChanged()
    }

    fun updateItems(items: List<FeedItem>) {
        episodes = items
        notifyDataSetChanged()
        updateTitle()
    }

    override fun getItemViewType(position: Int): Int {
        return R.id.view_type_episode_item
    }

    @UnstableApi override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeItemViewHolder {
        return EpisodeItemViewHolder(mainActivityRef.get()!!, parent)
    }

    @UnstableApi override fun onBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
        if (pos >= episodes.size) {
            beforeBindViewHolder(holder, pos)
            holder.bindDummy()
            afterBindViewHolder(holder, pos)
            holder.hideSeparatorIfNecessary()
            return
        }

        // Reset state of recycled views
        holder.coverHolder.visibility = View.VISIBLE
        holder.dragHandle.setVisibility(View.GONE)

        beforeBindViewHolder(holder, pos)

        val item: FeedItem = episodes[pos]
        holder.bind(item)

        holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
            val activity: MainActivity? = mainActivityRef.get()
            if (activity != null && !inActionMode()) {
                val ids: LongArray = FeedItemUtil.getIds(episodes)
                val position = ArrayUtils.indexOf(ids, item.id)
                activity.loadChildFragment(ItemPagerFragment.newInstance(ids, position))
            } else {
                toggleSelection(holder.bindingAdapterPosition)
            }
        })
        holder.itemView.setOnCreateContextMenuListener(this)
        holder.itemView.setOnLongClickListener(View.OnLongClickListener { v: View? ->
            longPressedItem = item
            longPressedPosition = holder.bindingAdapterPosition
            false
        })
        holder.itemView.setOnTouchListener(View.OnTouchListener { v: View?, e: MotionEvent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (e.isFromSource(InputDevice.SOURCE_MOUSE)
                        && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    longPressedItem = item
                    longPressedPosition = holder.bindingAdapterPosition
                    return@OnTouchListener false
                }
            }
            false
        })

        if (inActionMode()) {
            holder.secondaryActionButton.setOnClickListener(null)
            if (isSelected(pos)) {
                holder.itemView.setBackgroundColor(-0x78000000
                        + (0xffffff and ThemeUtils.getColorFromAttr(mainActivityRef.get()!!, R.attr.colorAccent)))
            } else {
                holder.itemView.setBackgroundResource(color.transparent)
            }
        }

        afterBindViewHolder(holder, pos)
        holder.hideSeparatorIfNecessary()
    }

    protected open fun beforeBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
    }

    protected open fun afterBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
    }

    @UnstableApi override fun onViewRecycled(holder: EpisodeItemViewHolder) {
        super.onViewRecycled(holder)
        // Set all listeners to null. This is required to prevent leaking fragments that have set a listener.
        // Activity -> recycledViewPool -> EpisodeItemViewHolder -> Listener -> Fragment (can not be garbage collected)
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnCreateContextMenuListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnTouchListener(null)
        holder.secondaryActionButton.setOnClickListener(null)
        holder.dragHandle.setOnTouchListener(null)
        holder.coverHolder.setOnTouchListener(null)
    }

    /**
     * [.notifyItemChanged] is final, so we can not override.
     * Calling [.notifyItemChanged] may bind the item to a new ViewHolder and execute a transition.
     * This causes flickering and breaks the download animation that stores the old progress in the View.
     * Instead, we tell the adapter to use partial binding by calling [.notifyItemChanged].
     * We actually ignore the payload and always do a full bind but calling the partial bind method ensures
     * that ViewHolders are always re-used.
     *
     * @param position Position of the item that has changed
     */
    fun notifyItemChangedCompat(position: Int) {
        notifyItemChanged(position, "foo")
    }

    override fun getItemId(position: Int): Long {
        if (position >= episodes.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        val item: FeedItem = episodes[position]
        return item.id
    }

    override fun getItemCount(): Int {
        return dummyViews + episodes.size
    }

    protected fun getItem(index: Int): FeedItem {
        return episodes[index]
    }

    protected val activity: Activity?
        get() = mainActivityRef.get()

    @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val inflater: MenuInflater = mainActivityRef.get()!!.menuInflater
        if (inActionMode()) {
            inflater.inflate(R.menu.multi_select_context_popup, menu)
        } else {
            if (longPressedItem == null) {
                return
            }
            inflater.inflate(R.menu.feeditemlist_context, menu)
            menu.setHeaderTitle(longPressedItem!!.title)
            FeedItemMenuHandler.onPrepareMenu(menu, longPressedItem, R.id.skip_episode_item)
        }
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.multi_select) {
            startSelectMode(longPressedPosition)
            return true
        } else if (item.itemId == R.id.select_all_above) {
            setSelected(0, longPressedPosition, true)
            return true
        } else if (item.itemId == R.id.select_all_below) {
            shouldSelectLazyLoadedItems = true
            setSelected(longPressedPosition + 1, itemCount, true)
            return true
        }
        return false
    }

    val selectedItems: List<Any>
        get() {
            val items: MutableList<FeedItem> = ArrayList<FeedItem>()
            for (i in 0 until itemCount) {
                if (isSelected(i)) {
                    items.add(getItem(i))
                }
            }
            return items
        }
}
