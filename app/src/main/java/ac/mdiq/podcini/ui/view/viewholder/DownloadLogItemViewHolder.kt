package ac.mdiq.podcini.ui.view.viewholder

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.DownloadlogItemBinding
import android.content.Context
import android.os.Build
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.joanzapata.iconify.widget.IconTextView

class DownloadLogItemViewHolder(context: Context, parent: ViewGroup?) :
    RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.downloadlog_item, parent, false)) {
    val binding = DownloadlogItemBinding.bind(itemView)
    @JvmField
    val secondaryActionButton: View = binding.secondaryActionLayout.secondaryAction
    @JvmField
    val secondaryActionIcon: ImageView = binding.secondaryActionLayout.secondaryActionIcon
//    val secondaryActionProgress: CircularProgressBar = binding.secondaryAction.secondaryActionProgress
    @JvmField
    val icon: IconTextView = binding.txtvIcon
    @JvmField
    val title: TextView = binding.txtvTitle
    @JvmField
    val status: TextView = binding.status
    @JvmField
    val reason: TextView = binding.txtvReason
    @JvmField
    val tapForDetails: TextView = binding.txtvTapForDetails

    init {
        title.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL
        itemView.tag = this
    }
}
