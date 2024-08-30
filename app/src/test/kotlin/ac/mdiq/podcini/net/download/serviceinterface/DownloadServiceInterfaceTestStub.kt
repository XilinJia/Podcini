package ac.mdiq.podcini.net.download.serviceinterface

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import android.content.Context

class DownloadServiceInterfaceTestStub : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: Episode, ignoreConstraints: Boolean) {}

    override fun download(context: Context, item: Episode) {}

    override fun cancel(context: Context, media: EpisodeMedia) {}

    override fun cancelAll(context: Context) {}
}