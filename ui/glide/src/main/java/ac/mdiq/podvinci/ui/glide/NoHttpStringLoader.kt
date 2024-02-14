package ac.mdiq.podvinci.ui.glide

import android.net.Uri
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.StringLoader
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedMedia
import java.io.InputStream

/**
 * StringLoader that does not handle http/https urls. Used to avoid fallback to StringLoader when
 * PodVinci blocks mobile image loading.
 */
class NoHttpStringLoader(uriLoader: ModelLoader<Uri, InputStream?>?) : StringLoader<InputStream?>(uriLoader) {
    class StreamFactory : ModelLoaderFactory<String, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            val uriLoader_ = multiFactory.build(Uri::class.java, InputStream::class.java)
            return NoHttpStringLoader(uriLoader_) as ModelLoader<String, InputStream>
        }

        override fun teardown() {
            // Do nothing.
        }
    }

    override fun handles(model: String): Boolean {
        return (!model.startsWith("http") // If the custom loaders fail, do not attempt to load with Glide internal loaders
                && !model.startsWith(Feed.PREFIX_GENERATIVE_COVER)
                && !model.startsWith(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER)
                && super.handles(model))
    }
}
