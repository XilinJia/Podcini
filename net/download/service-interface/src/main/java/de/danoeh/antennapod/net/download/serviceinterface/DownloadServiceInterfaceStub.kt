package de.danoeh.antennapod.net.download.serviceinterface

import android.content.Context
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

class DownloadServiceInterfaceStub : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: FeedItem, ignoreConstraints: Boolean) {
    }

    override fun download(context: Context, item: FeedItem) {
    }

    override fun cancel(context: Context, media: FeedMedia) {
    }

    override fun cancelAll(context: Context) {
    }
}
