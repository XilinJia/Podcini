package ac.mdiq.podcini.preferences

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.common.ThemeUtils.getColorFromAttr

class MasterSwitchPreference : SwitchPreferenceCompat {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context!!, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?) : super(context!!, attrs)

    constructor(context: Context) : super(context!!)


    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setBackgroundColor(getColorFromAttr(context, R.attr.colorSurfaceVariant))
        val title = holder.findViewById(android.R.id.title) as? TextView
        title?.setTypeface(title.typeface, Typeface.BOLD)
    }
}