package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.EpisodeInfoFragment
import ac.mdiq.podcini.ui.fragment.FeedInfoFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.util.Logd
import android.R.color
import android.app.Activity
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import java.lang.ref.WeakReference

/**
 * List adapter for the list of new episodes.
 */
open class EpisodesAdapter(mainActivity: MainActivity, var refreshFragPosCallback: ((Int, Episode) -> Unit)? = null)
    : SelectableAdapter<EpisodeViewHolder?>(mainActivity) {

    private val TAG: String = this::class.simpleName ?: "Anonymous"

    val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    protected val activity: Activity?
        get() = mainActivityRef.get()

    private var episodes: MutableList<Episode> = ArrayList()
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

    @UnstableApi
    fun refreshPosCallback(pos: Int, episode: Episode) {
        Logd(TAG, "refreshPosCallback: $pos ${episode.title}")
        if (pos >= 0 && pos < episodes.size) episodes[pos] = episode
//        notifyItemChanged(pos, "foo")
        refreshFragPosCallback?.invoke(pos, episode)
    }

    fun clearData() {
        episodes = mutableListOf()
        feed = null
        notifyDataSetChanged()
    }

//    fun setDummyViews(dummyViews: Int) {
//        this.dummyViews = dummyViews
//        notifyDataSetChanged()
//    }

    fun updateItems(items: MutableList<Episode>, feed_: Feed? = null) {
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

    @UnstableApi
    override fun onBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
        if (pos >= episodes.size || pos < 0) {
            beforeBindViewHolder(holder, pos)
            holder.bindDummy()
            afterBindViewHolder(holder, pos)
            holder.hideSeparatorIfNecessary()
            return
        }

        holder.refreshAdapterPosCallback = ::refreshPosCallback
        holder.setPosIndex(pos)

        // Reset state of recycled views
        holder.coverHolder.visibility = View.VISIBLE
        holder.dragHandle.visibility = View.GONE

        beforeBindViewHolder(holder, pos)

        val item: Episode = episodes[pos]
        item.feed = feed ?: episodes[pos].feed
        holder.bind(item)

        holder.infoCard.setOnLongClickListener {
            longPressedItem = holder.episode
            longPressedPosition = holder.bindingAdapterPosition
            startSelectMode(longPressedPosition)
            true
        }
        holder.infoCard.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode() && holder.episode != null) activity?.loadChildFragment(EpisodeInfoFragment.newInstance(holder.episode!!))
            else toggleSelection(holder.bindingAdapterPosition)
        }
        holder.coverHolder.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode() && holder.episode?.feed != null) activity?.loadChildFragment(FeedInfoFragment.newInstance(holder.episode!!.feed!!))
            else toggleSelection(holder.bindingAdapterPosition)
        }
        holder.itemView.setOnTouchListener(View.OnTouchListener { _: View?, e: MotionEvent ->
            if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                longPressedItem = holder.episode
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

    @UnstableApi
    override fun onBindViewHolder(holder: EpisodeViewHolder, pos: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(holder, pos)
        else {
            val payload = payloads[0]
            when {
                payload is String && payload == "foo" -> onBindViewHolder(holder, pos)
                payload is Bundle && !payload.getString("PositionUpdate").isNullOrEmpty() -> holder.updatePlaybackPositionNew(episodes[pos])
            }
        }
    }

    protected open fun beforeBindViewHolder(holder: EpisodeViewHolder, pos: Int) {}

    protected open fun afterBindViewHolder(holder: EpisodeViewHolder, pos: Int) {}

    @UnstableApi override fun onViewRecycled(holder: EpisodeViewHolder) {
        super.onViewRecycled(holder)
        holder.refreshAdapterPosCallback = null
        holder.unbind()
    }

    /**
     * [.notifyItemChanged] is final, so we can not override.
     * Calling [.notifyItemChanged] may bind the item to a new ViewHolder and execute a transition.
     * This causes flickering and breaks the download animation that stores the old progress in the View.
     * Instead, we tell the adapter to use partial binding by calling [.notifyItemChanged].
     * We actually ignore the payload and always do a full bind but calling the partial bind method ensures
     * that ViewHolders are always re-used.
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
}
