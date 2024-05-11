package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.HorizontalFeedItemBinding
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedItemlistFragment
import ac.mdiq.podcini.ui.view.SquareImageView
import android.view.ContextMenu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.StringRes
import androidx.cardview.widget.CardView
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import java.lang.ref.WeakReference

open class HorizontalFeedListAdapter(mainActivity: MainActivity) :
    RecyclerView.Adapter<HorizontalFeedListAdapter.Holder>(), View.OnCreateContextMenuListener {

    private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    private val data: MutableList<Feed> = ArrayList()
    private var dummyViews = 0
    var longPressedItem: Feed? = null

    @StringRes
    private var endButtonText = 0
    private var endButtonAction: Runnable? = null

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
    }

    fun updateData(newData: List<Feed>?) {
        data.clear()
        data.addAll(newData!!)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val convertView = View.inflate(mainActivityRef.get(), R.layout.horizontal_feed_item, null)
        return Holder(convertView)
    }

    @UnstableApi override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position == itemCount - 1 && endButtonAction != null) {
            holder.cardView.visibility = View.GONE
            holder.actionButton.visibility = View.VISIBLE
            holder.actionButton.setText(endButtonText)
            holder.actionButton.setOnClickListener { endButtonAction!!.run() }
            return
        }
        holder.cardView.visibility = View.VISIBLE
        holder.actionButton.visibility = View.GONE
        if (position >= data.size) {
            holder.itemView.alpha = 0.1f
//            Glide.with(mainActivityRef.get()!!).clear(holder.imageView)
            val imageLoader = ImageLoader.Builder(mainActivityRef.get()!!).build()
            imageLoader.enqueue(ImageRequest.Builder(mainActivityRef.get()!!).data(null).target(holder.imageView).build())
            holder.imageView.setImageResource(R.color.medium_gray)
            return
        }

        holder.itemView.alpha = 1.0f
        val podcast: Feed = data[position]
        holder.imageView.setContentDescription(podcast.title)
        holder.imageView.setOnClickListener {
            mainActivityRef.get()?.loadChildFragment(FeedItemlistFragment.newInstance(podcast.id))
        }

        holder.imageView.setOnCreateContextMenuListener(this)
        holder.imageView.setOnLongClickListener {
            val currentItemPosition = holder.bindingAdapterPosition
            longPressedItem = data[currentItemPosition]
            false
        }

//        if (!podcast.imageUrl.isNullOrBlank()) Glide.with(mainActivityRef.get()!!)
//            .load(podcast.imageUrl)
//            .apply(RequestOptions()
//                .placeholder(R.color.light_gray)
//                .fitCenter()
//                .dontAnimate())
//            .into(holder.imageView)
        holder.imageView.load(podcast.imageUrl) {
            placeholder(R.color.light_gray)
            error(R.mipmap.ic_launcher)
        }
    }

    override fun getItemId(position: Int): Long {
        if (position >= data.size) return RecyclerView.NO_ID // Dummy views
        return data[position].id
    }

    override fun getItemCount(): Int {
        return dummyViews + data.size + (if ((endButtonAction == null)) 0 else 1)
    }

    override fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater: MenuInflater = mainActivityRef.get()!!.menuInflater
        if (longPressedItem == null) return
        inflater.inflate(R.menu.nav_feed_context, contextMenu)
        contextMenu.setHeaderTitle(longPressedItem!!.title)
    }

    fun setEndButton(@StringRes text: Int, action: Runnable?) {
        endButtonAction = action
        endButtonText = text
        notifyDataSetChanged()
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = HorizontalFeedItemBinding.bind(itemView)
        var imageView: SquareImageView = binding.discoveryCover
        var cardView: CardView
        var actionButton: Button

        init {
            imageView.setDirection(SquareImageView.DIRECTION_HEIGHT)
            actionButton = binding.actionButton
            cardView = binding.cardView
        }
    }
}
