package ac.mdiq.podcini.ui.view

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podcini.R
import android.content.SharedPreferences

class EpisodesRecyclerView : RecyclerView {
    private lateinit var layoutManager: LinearLayoutManager

    val isScrolledToBottom: Boolean
        get() {
            val visibleEpisodeCount = childCount
            val totalEpisodeCount = layoutManager.itemCount
            val firstVisibleEpisode = layoutManager.findFirstVisibleItemPosition()
            return (totalEpisodeCount - visibleEpisodeCount) <= (firstVisibleEpisode + 3)
        }

    constructor(context: Context) : super(ContextThemeWrapper(context, R.style.FastScrollRecyclerView)) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) :
            super(ContextThemeWrapper(context, R.style.FastScrollRecyclerView), attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(ContextThemeWrapper(context, R.style.FastScrollRecyclerView), attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        layoutManager = LinearLayoutManager(context)
        layoutManager.recycleChildrenOnDetach = true
        setLayoutManager(layoutManager)
        setHasFixedSize(true)
        addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
        clipToPadding = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
        setPadding(horizontalSpacing, paddingTop, horizontalSpacing, paddingBottom)
    }

    fun saveScrollPosition(tag: String) {
        val firstItem = layoutManager.findFirstVisibleItemPosition()
        val firstItemView = layoutManager.findViewByPosition(firstItem)
        val topOffset = firstItemView?.top?.toFloat() ?: 0f

        prefs!!.edit()
            .putInt(PREF_PREFIX_SCROLL_POSITION + tag, firstItem)
            .putInt(PREF_PREFIX_SCROLL_OFFSET + tag, topOffset.toInt())
            .apply()
    }

    fun restoreScrollPosition(tag: String) {
        val position = prefs!!.getInt(PREF_PREFIX_SCROLL_POSITION + tag, 0)
        val offset = prefs!!.getInt(PREF_PREFIX_SCROLL_OFFSET + tag, 0)
        if (position > 0 || offset > 0) layoutManager.scrollToPositionWithOffset(position, offset)
    }

    companion object {
        private val TAG: String = EpisodesRecyclerView::class.simpleName ?: "Anonymous"
        private const val PREF_PREFIX_SCROLL_POSITION = "scroll_position_"
        private const val PREF_PREFIX_SCROLL_OFFSET = "scroll_offset_"

        var prefs: SharedPreferences? = null

        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        }
    }
}
