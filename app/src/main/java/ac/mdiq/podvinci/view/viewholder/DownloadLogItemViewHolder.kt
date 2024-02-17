package ac.mdiq.podvinci.view.viewholder

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
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.ui.common.CircularProgressBar

class DownloadLogItemViewHolder(context: Context?, parent: ViewGroup?) :
    RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.downloadlog_item, parent, false)) {

    @JvmField
    val secondaryActionButton: View = itemView.findViewById(R.id.secondaryActionButton)
    @JvmField
    val secondaryActionIcon: ImageView = itemView.findViewById(R.id.secondaryActionIcon)
    val secondaryActionProgress: CircularProgressBar = itemView.findViewById(R.id.secondaryActionProgress)
    @JvmField
    val icon: IconTextView = itemView.findViewById(R.id.txtvIcon)
    @JvmField
    val title: TextView = itemView.findViewById(R.id.txtvTitle)
    @JvmField
    val status: TextView = itemView.findViewById(R.id.status)
    @JvmField
    val reason: TextView = itemView.findViewById(R.id.txtvReason)
    @JvmField
    val tapForDetails: TextView = itemView.findViewById(R.id.txtvTapForDetails)

    init {
        if (Build.VERSION.SDK_INT >= 23) {
            title.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL
        }
        itemView.tag = this
    }
}
