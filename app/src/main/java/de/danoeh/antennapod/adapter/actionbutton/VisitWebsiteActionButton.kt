package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import android.view.View
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.util.IntentUtils.openInBrowser
import de.danoeh.antennapod.model.feed.FeedItem

class VisitWebsiteActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.visit_website_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_web
    }
    override fun onClick(context: Context) {
        if (item.link!= null) openInBrowser(context, item.link!!)
    }

    override val visibility: Int
        get() = if ((item.link == null)) View.INVISIBLE else View.VISIBLE
}
