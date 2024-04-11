package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.fragment.EpisodeInfoFragment
import ac.mdiq.podcini.ui.actions.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import android.R.color
import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference


/**
 * List adapter for the list of new episodes.
 */
open class EpisodeItemListAdapter(mainActivity: MainActivity) :
    SelectableAdapter<EpisodeItemViewHolder?>(mainActivity), View.OnCreateContextMenuListener {

    private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    private var episodes: List<FeedItem> = ArrayList()
    var longPressedItem: FeedItem? = null
    private var longPressedPosition: Int = 0 // used to init actionMode
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
        if (pos >= episodes.size || pos < 0) {
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

//        holder.infoCard.setOnCreateContextMenuListener(this)
        holder.infoCard.setOnLongClickListener {
            longPressedItem = item
            longPressedPosition = holder.bindingAdapterPosition
            startSelectMode(longPressedPosition)
            true
        }
        holder.infoCard.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode()) {
//                val ids: LongArray = FeedItemUtil.getIds(episodes)
//                val position = ArrayUtils.indexOf(ids, item.id)
                activity?.loadChildFragment(EpisodeInfoFragment.newInstance(episodes[pos]))
                Log.d("infoCard", "setOnClickListener starting EpisodeInfoFragment")
            } else {
                toggleSelection(holder.bindingAdapterPosition)
            }
        }

        holder.coverHolder.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode()) {
//                val ids: LongArray = FeedItemUtil.getIds(episodes)
//                val position = ArrayUtils.indexOf(ids, item.id)
                activity?.loadChildFragment(EpisodeInfoFragment.newInstance(episodes[pos]))
            } else {
                toggleSelection(holder.bindingAdapterPosition)
            }
        }
        holder.itemView.setOnTouchListener(View.OnTouchListener { _: View?, e: MotionEvent ->
            if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                longPressedItem = item
                longPressedPosition = holder.bindingAdapterPosition
                return@OnTouchListener false
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
        return getItem(position)?.id ?: RecyclerView.NO_ID
    }

    override fun getItemCount(): Int {
        return dummyViews + episodes.size
    }

    protected fun getItem(index: Int): FeedItem? {
        return if (index in episodes.indices) episodes[index] else null
    }

    protected val activity: Activity?
        get() = mainActivityRef.get()

    @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater: MenuInflater = activity!!.menuInflater
        if (inActionMode()) {
//            inflater.inflate(R.menu.multi_select_context_popup, menu)
        } else {
            if (longPressedItem == null) return

            inflater.inflate(R.menu.feeditemlist_context, menu)
            menu.setHeaderTitle(longPressedItem!!.title)
            FeedItemMenuHandler.onPrepareMenu(menu, longPressedItem, R.id.skip_episode_item)
        }
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.multi_select -> {
                startSelectMode(longPressedPosition)
                return true
            }
            R.id.select_all_above -> {
                setSelected(0, longPressedPosition, true)
                return true
            }
            R.id.select_all_below -> {
                shouldSelectLazyLoadedItems = true
                setSelected(longPressedPosition + 1, itemCount, true)
                return true
            }
            else -> return false
        }
    }

    val selectedItems: List<Any>
        get() {
            val items: MutableList<FeedItem> = ArrayList()
            for (i in 0 until itemCount) {
                if (i < episodes.size && isSelected(i)) {
                    val item = getItem(i)
                    if (item != null) items.add(item)
                }
            }
            return items
        }
}
