package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SimpleIconListItemBinding
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

/**
 * Displays a list of items that have a subtitle and an icon.
 */
class SimpleIconListAdapter<T : SimpleIconListAdapter.ListItem>(private val context: Context, private val listItems: List<T>)
    : ArrayAdapter<T>(context, R.layout.simple_icon_list_item, listItems) {

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var view = view
        if (view == null) view = View.inflate(context, R.layout.simple_icon_list_item, null)

        val item: ListItem = listItems[position]
        val binding = SimpleIconListItemBinding.bind(view!!)
        binding.title.text = item.title
        binding.subtitle.text = item.subtitle
        if (item.imageUrl.isNotBlank()) Glide.with(context)
            .load(item.imageUrl)
            .apply(RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .fitCenter()
                .dontAnimate())
            .into(binding.icon)
        return view
    }

    open class ListItem(val title: String, val subtitle: String, val imageUrl: String)
}
