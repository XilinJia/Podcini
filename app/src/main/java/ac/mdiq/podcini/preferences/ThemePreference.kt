package ac.mdiq.podcini.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.cardview.widget.CardView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.elevation.SurfaceColors
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ThemePreferenceBinding

class ThemePreference : Preference {
    var viewBinding: ThemePreferenceBinding? = null

    constructor(context: Context) : super(context!!) {
        layoutResource = R.layout.theme_preference
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context!!, attrs) {
        layoutResource = R.layout.theme_preference
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        viewBinding = ThemePreferenceBinding.bind(holder.itemView)
        updateUi()
    }

    fun updateThemeCard(card: CardView, theme: UserPreferences.ThemePreference) {
        val density = context.resources.displayMetrics.density
        val surfaceColor = SurfaceColors.getColorForElevation(context, 1 * density)
        val surfaceColorActive = SurfaceColors.getColorForElevation(context, 32 * density)
        val activeTheme = UserPreferences.theme
        card.setCardBackgroundColor(if (theme == activeTheme) surfaceColorActive else surfaceColor)
        card.setOnClickListener { v: View? ->
            UserPreferences.theme = theme
            if (onPreferenceChangeListener != null) {
                onPreferenceChangeListener!!.onPreferenceChange(this, UserPreferences.theme)
            }
            updateUi()
        }
    }

    fun updateUi() {
        updateThemeCard(viewBinding!!.themeSystemCard, UserPreferences.ThemePreference.SYSTEM)
        updateThemeCard(viewBinding!!.themeLightCard, UserPreferences.ThemePreference.LIGHT)
        updateThemeCard(viewBinding!!.themeDarkCard, UserPreferences.ThemePreference.DARK)
    }
}
