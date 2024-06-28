package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.unmanagedCopy
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.actions.menuhandler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.EpisodeInfoFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.viewholder.EpisodeViewHolder
import android.R.color
import android.app.Activity
import android.view.*
import androidx.media3.common.util.UnstableApi

import java.lang.ref.WeakReference

/**
 * List adapter for the list of new episodes.
 */
open class EpisodesAdapter(mainActivity: MainActivity)
    : SelectableAdapter<EpisodeViewHolder?>(mainActivity), View.OnCreateContextMenuListener {

    private val TAG: String = this::class.simpleName ?: "Anonymous"

    val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    protected val activity: Activity?
        get() = mainActivityRef.get()

    private var episodes: List<Episode> = ArrayList()
    private var feed: Feed? = null
    var longPressedItem: Episode? = null
    private var longPressedPosition: Int = 0 // used to init actionMode
    private var dummyViews = 0

    val selectedItems: List<Any>
        get() {
            val items: MutableList<Episode> = ArrayList()
            for (i in 0 until itemCount) {
                if (i < episodes.size && isSelected(i)) {
                    val item = getItem(i)
                    if (item != null) items.add(item)
                }
            }
            return items
        }

    init {
        setHasStableIds(true)
    }

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
        notifyDataSetChanged()
    }

    fun updateItems(items: List<Episode>, feed_: Feed? = null) {
        episodes = items
        feed = feed_
        notifyDataSetChanged()
        updateTitle()
    }

    override fun getItemViewType(position: Int): Int {
        return R.id.view_type_episode_item
    }

    @UnstableApi override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
//  TODO: the Invalid resource ID 0x00000000 on Android 14 occurs after this and before onBindViewHolder,
//        somehow, only on the first time EpisodeItemListAdapter is called
        return EpisodeViewHolder(mainActivityRef.get()!!, parent)
    }

    @UnstableApi override fun onBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
        if (pos >= episodes.size || pos < 0) {
            beforeBindViewHolder(holder, pos)
            holder.bindDummy()
            afterBindViewHolder(holder, pos)
            holder.hideSeparatorIfNecessary()
            return
        }

        // Reset state of recycled views
        holder.coverHolder.visibility = View.VISIBLE
        holder.dragHandle.visibility = View.GONE

        beforeBindViewHolder(holder, pos)

        val item: Episode = unmanagedCopy(episodes[pos])
        if (feed != null) item.feed = feed
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
            if (!inActionMode()) activity?.loadChildFragment(EpisodeInfoFragment.newInstance(episodes[pos]))
            else toggleSelection(holder.bindingAdapterPosition)
        }
        holder.coverHolder.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode()) activity?.loadChildFragment(EpisodeInfoFragment.newInstance(episodes[pos]))
            else toggleSelection(holder.bindingAdapterPosition)
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
            if (isSelected(pos))
                holder.itemView.setBackgroundColor(-0x78000000 + (0xffffff and ThemeUtils.getColorFromAttr(mainActivityRef.get()!!, androidx.appcompat.R.attr.colorAccent)))
            else holder.itemView.setBackgroundResource(color.transparent)
        }

        afterBindViewHolder(holder, pos)
        holder.hideSeparatorIfNecessary()
    }

    protected open fun beforeBindViewHolder(holder: EpisodeViewHolder, pos: Int) {}

    protected open fun afterBindViewHolder(holder: EpisodeViewHolder, pos: Int) {}

    @UnstableApi override fun onViewRecycled(holder: EpisodeViewHolder) {
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
        return getItem(position)?.id ?: 0L
    }

    override fun getItemCount(): Int {
        return dummyViews + episodes.size
    }

    protected fun getItem(index: Int): Episode? {
        val item = if (index in episodes.indices) episodes[index] else null
        return item
    }

    @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater: MenuInflater = activity!!.menuInflater
        if (inActionMode()) {
//            inflater.inflate(R.menu.multi_select_context_popup, menu)
        } else {
            if (longPressedItem == null) return

            inflater.inflate(R.menu.feeditemlist_context, menu)
            menu.setHeaderTitle(longPressedItem!!.title)
            EpisodeMenuHandler.onPrepareMenu(menu, longPressedItem, R.id.skip_episode_item)
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
}
