package ac.mdiq.podcini.net.download.serviceinterface

import android.content.Context
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia

// only used in tests
class DownloadServiceInterfaceTestStub : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: Episode, ignoreConstraints: Boolean) {}

    override fun download(context: Context, item: Episode) {}

    override fun cancel(context: Context, media: EpisodeMedia) {}

    override fun cancelAll(context: Context) {}
}
