package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SubscriptionItemBinding
import ac.mdiq.podcini.storage.NavDrawerData
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedItemlistFragment
import ac.mdiq.podcini.ui.fragment.SubscriptionFragment
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.elevation.SurfaceColors
import java.lang.ref.WeakReference
import java.text.NumberFormat

/**
 * Adapter for subscriptions
 */
open class SubscriptionsRecyclerAdapter(mainActivity: MainActivity) :
    SelectableAdapter<SubscriptionsRecyclerAdapter.SubscriptionViewHolder?>(mainActivity),
    View.OnCreateContextMenuListener {

    private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    private var listItems: List<NavDrawerData.DrawerItem>
    private var selectedItem: NavDrawerData.DrawerItem? = null
    private var longPressedPosition: Int = 0 // used to init actionMode

    init {
        this.listItems = ArrayList()
        setHasStableIds(true)
    }

    fun getItem(position: Int): Any {
        return listItems[position]
    }

    fun getSelectedItem(): NavDrawerData.DrawerItem? {
        return selectedItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val itemView: View = LayoutInflater.from(mainActivityRef.get()).inflate(R.layout.subscription_item, parent, false)
        return SubscriptionViewHolder(itemView)
    }

    @UnstableApi override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val drawerItem: NavDrawerData.DrawerItem = listItems[position]
        val isFeed = drawerItem.type == NavDrawerData.DrawerItem.Type.FEED
        holder.bind(drawerItem)
        holder.itemView.setOnCreateContextMenuListener(this)
        if (inActionMode()) {
            if (isFeed) {
                holder.selectCheckbox.visibility = View.VISIBLE
                holder.selectView.visibility = View.VISIBLE
            }
            holder.selectCheckbox.setChecked((isSelected(position)))
            holder.selectCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                setSelected(holder.bindingAdapterPosition,
                    isChecked)
            }
            holder.coverImage.alpha = 0.6f
            holder.count.visibility = View.GONE
        } else {
            holder.selectView.visibility = View.GONE
            holder.coverImage.alpha = 1.0f
        }

        holder.itemView.setOnLongClickListener {
            if (!inActionMode()) {
                if (isFeed) {
                    longPressedPosition = holder.bindingAdapterPosition
                }
                selectedItem = drawerItem
            }
            false
        }

        holder.itemView.setOnTouchListener { _: View?, e: MotionEvent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (e.isFromSource(InputDevice.SOURCE_MOUSE)
                        && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    if (!inActionMode()) {
                        if (isFeed) {
                            longPressedPosition = holder.bindingAdapterPosition
                        }
                        selectedItem = drawerItem
                    }
                }
            }
            false
        }
        holder.itemView.setOnClickListener {
            if (isFeed) {
                if (inActionMode()) {
                    holder.selectCheckbox.setChecked(!isSelected(holder.bindingAdapterPosition))
                } else {
                    val fragment: Fragment = FeedItemlistFragment
                        .newInstance((drawerItem as NavDrawerData.FeedDrawerItem).feed.id)
                    mainActivityRef.get()?.loadChildFragment(fragment)
                }
            } else if (!inActionMode()) {
                val fragment: Fragment = SubscriptionFragment.newInstance(drawerItem.title)
                mainActivityRef.get()?.loadChildFragment(fragment)
            }
        }
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun getItemId(position: Int): Long {
        if (position >= listItems.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        return listItems[position].id
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        if (inActionMode() || selectedItem == null) {
            return
        }
        val inflater: MenuInflater = mainActivityRef.get()!!.menuInflater
        if (selectedItem?.type == NavDrawerData.DrawerItem.Type.FEED) {
            inflater.inflate(R.menu.nav_feed_context, menu)
            menu.findItem(R.id.multi_select).setVisible(true)
        } else {
            inflater.inflate(R.menu.nav_folder_context, menu)
        }
        menu.setHeaderTitle(selectedItem?.title)
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.multi_select) {
            startSelectMode(longPressedPosition)
            return true
        }
        return false
    }

    val selectedItems: List<Any>
        get() {
            val items = ArrayList<Feed>()
            for (i in 0 until itemCount) {
                if (isSelected(i)) {
                    val drawerItem: NavDrawerData.DrawerItem = listItems[i]
                    if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
                        val feed: Feed = (drawerItem as NavDrawerData.FeedDrawerItem).feed
                        items.add(feed)
                    }
                }
            }
            return items
        }

    fun setItems(listItems: List<NavDrawerData.DrawerItem>) {
        this.listItems = listItems
        notifyDataSetChanged()
    }

    inner class SubscriptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = SubscriptionItemBinding.bind(itemView)
        private val title = binding.titleLabel
        private val producer = binding.producerLabel
        val count: TextView = binding.countLabel

        val coverImage: ImageView = binding.coverImage
        val selectView: FrameLayout = binding.selectContainer
        val selectCheckbox: CheckBox = binding.selectCheckBox
        private val card: CardView = binding.outerContainer

        private val errorIcon: View = binding.errorIcon

        fun bind(drawerItem: NavDrawerData.DrawerItem) {
            val drawable: Drawable? = AppCompatResources.getDrawable(selectView.context, R.drawable.ic_checkbox_background)
            selectView.background = drawable // Setting this in XML crashes API <= 21
            title.text = drawerItem.title
            producer.text = drawerItem.producer
            coverImage.contentDescription = drawerItem.title
            if (drawerItem.counter > 0) {
                count.text = NumberFormat.getInstance().format(drawerItem.counter.toLong()) + " episodes"
                count.visibility = View.VISIBLE
            } else {
                count.visibility = View.GONE
            }

            val coverLoader = CoverLoader(mainActivityRef.get()!!)
            if (drawerItem.type == NavDrawerData.DrawerItem.Type.FEED) {
                val feed: Feed = (drawerItem as NavDrawerData.FeedDrawerItem).feed
                coverLoader.withUri(feed.imageUrl)
                errorIcon.visibility = if (feed.hasLastUpdateFailed()) View.VISIBLE else View.GONE
            } else {
                coverLoader.withResource(R.drawable.ic_tag)
                errorIcon.visibility = View.GONE
            }
            coverLoader.withCoverView(coverImage)
            coverLoader.load()

            val density: Float = mainActivityRef.get()!!.resources.displayMetrics.density
            card.setCardBackgroundColor(SurfaceColors.getColorForElevation(mainActivityRef.get()!!, 1 * density))

            val textHPadding = 20
            val textVPadding = 5
            title.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)
            producer.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)
            count.setPadding(textHPadding, textVPadding, textHPadding, textVPadding)

            val textSize = 14
            title.textSize = textSize.toFloat()
        }
    }

    class GridDividerItemDecorator : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect,
                                    view: View,
                                    parent: RecyclerView,
                                    state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            val context = parent.context
            val insetOffset = convertDpToPixel(context, 1f).toInt()
            outRect[insetOffset, insetOffset, insetOffset] = insetOffset
        }
    }

    companion object {
        fun convertDpToPixel(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }
    }
}
