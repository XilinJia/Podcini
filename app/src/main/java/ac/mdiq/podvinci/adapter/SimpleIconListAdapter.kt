package ac.mdiq.podvinci.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import ac.mdiq.podvinci.R

/**
 * Displays a list of items that have a subtitle and an icon.
 */
class SimpleIconListAdapter<T : SimpleIconListAdapter.ListItem>(private val context: Context,
                                                                private val listItems: List<T>
) : ArrayAdapter<T>(
    context, R.layout.simple_icon_list_item, listItems) {
    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var view = view
        if (view == null) {
            view = View.inflate(context, R.layout.simple_icon_list_item, null)
        }

        val item: ListItem = listItems[position]
        (view!!.findViewById<View>(R.id.title) as TextView).text = item.title
        (view.findViewById<View>(R.id.subtitle) as TextView).text = item.subtitle
        Glide.with(context)
            .load(item.imageUrl)
            .apply(RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .fitCenter()
                .dontAnimate())
            .into(((view.findViewById<View>(R.id.icon) as ImageView)))
        return view
    }

    open class ListItem(val title: String, val subtitle: String, val imageUrl: String)
}
