package ac.mdiq.podvinci.adapter

import ac.mdiq.podvinci.activity.MainActivity
import android.view.ContextMenu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.util.FeedItemUtil
import ac.mdiq.podvinci.fragment.ItemPagerFragment
import ac.mdiq.podvinci.menuhandler.FeedItemMenuHandler
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.view.viewholder.HorizontalItemViewHolder
import org.apache.commons.lang3.ArrayUtils
import java.lang.ref.WeakReference

open class HorizontalItemListAdapter(mainActivity: MainActivity) : RecyclerView.Adapter<HorizontalItemViewHolder?>(),
    View.OnCreateContextMenuListener {
    private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    private var data: List<FeedItem> = ArrayList()
    var longPressedItem: FeedItem? = null
    private var dummyViews = 0

    init {
        setHasStableIds(true)
    }

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
    }

    fun updateData(newData: List<FeedItem>) {
        data = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalItemViewHolder {
        return HorizontalItemViewHolder(mainActivityRef.get()!!, parent)
    }

    @UnstableApi override fun onBindViewHolder(holder: HorizontalItemViewHolder, position: Int) {
        if (position >= data.size) {
            holder.bindDummy()
            return
        }

        val item: FeedItem = data[position]
        holder.bind(item)

        holder.card.setOnCreateContextMenuListener(this)
        holder.card.setOnLongClickListener { v: View? ->
            longPressedItem = item
            false
        }
        holder.secondaryActionIcon.setOnCreateContextMenuListener(this)
        holder.secondaryActionIcon.setOnLongClickListener { v: View? ->
            longPressedItem = item
            false
        }
        holder.card.setOnClickListener { v: View? ->
            val activity: MainActivity? = mainActivityRef.get()
            if (activity != null) {
                val ids: LongArray = FeedItemUtil.getIds(data)
                val clickPosition = ArrayUtils.indexOf(ids, item.id)
                activity.loadChildFragment(ItemPagerFragment.newInstance(ids, clickPosition))
            }
        }
    }

    override fun getItemId(position: Int): Long {
        if (position >= data.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        return data[position].id
    }

    override fun getItemCount(): Int {
        return dummyViews + data.size
    }

    override fun onViewRecycled(holder: HorizontalItemViewHolder) {
        super.onViewRecycled(holder)
        // Set all listeners to null. This is required to prevent leaking fragments that have set a listener.
        // Activity -> recycledViewPool -> ViewHolder -> Listener -> Fragment (can not be garbage collected)
        holder.card.setOnClickListener(null)
        holder.card.setOnCreateContextMenuListener(null)
        holder.card.setOnLongClickListener(null)
        holder.secondaryActionIcon.setOnClickListener(null)
        holder.secondaryActionIcon.setOnCreateContextMenuListener(null)
        holder.secondaryActionIcon.setOnLongClickListener(null)
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

    @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val inflater: MenuInflater = mainActivityRef.get()!!.menuInflater
        if (longPressedItem == null) {
            return
        }
        menu.clear()
        inflater.inflate(R.menu.feeditemlist_context, menu)
        menu.setHeaderTitle(longPressedItem!!.title)
        FeedItemMenuHandler.onPrepareMenu(menu, longPressedItem, R.id.skip_episode_item)
    }
}
