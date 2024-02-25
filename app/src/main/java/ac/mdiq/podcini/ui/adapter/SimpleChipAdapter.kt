package ac.mdiq.podcini.ui.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import ac.mdiq.podcini.R

abstract class SimpleChipAdapter(private val context: Context) : RecyclerView.Adapter<SimpleChipAdapter.ViewHolder>() {
    init {
        setHasStableIds(true)
    }

    protected abstract fun getChips(): List<String>

    protected abstract fun onRemoveClicked(position: Int)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val chip = Chip(context)
        chip.isCloseIconVisible = true
        chip.setCloseIconResource(R.drawable.ic_delete)
        return ViewHolder(chip)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.chip.text = getChips()[position]
        holder.chip.setOnCloseIconClickListener { onRemoveClicked(position) }
    }

    override fun getItemCount(): Int {
        return getChips().size
    }

    override fun getItemId(position: Int): Long {
        return getChips()[position].hashCode().toLong()
    }

    class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(chip)
}