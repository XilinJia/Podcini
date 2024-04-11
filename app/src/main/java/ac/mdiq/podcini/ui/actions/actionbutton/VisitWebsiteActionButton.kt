package ac.mdiq.podcini.ui.actions.actionbutton

import android.content.Context
import android.view.View
import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.storage.model.feed.FeedItem

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
        get() = if (item.link == null) View.INVISIBLE else View.VISIBLE
}
