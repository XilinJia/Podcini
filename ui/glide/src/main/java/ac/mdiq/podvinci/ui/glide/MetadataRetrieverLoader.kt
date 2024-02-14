package ac.mdiq.podvinci.ui.glide

import android.content.Context
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import ac.mdiq.podvinci.model.feed.FeedMedia
import java.io.InputStream

internal class MetadataRetrieverLoader private constructor(private val context: Context) :
    ModelLoader<String, InputStream> {
    /**
     * The default factory for [MetadataRetrieverLoader]s.
     */
    class Factory internal constructor(private val context: Context) : ModelLoaderFactory<String, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return MetadataRetrieverLoader(context)
        }

        override fun teardown() {
            // Do nothing, this instance doesn't own the client.
        }
    }

    override fun buildLoadData(model: String,
                               width: Int, height: Int, options: Options
    ): ModelLoader.LoadData<InputStream?> {
        return ModelLoader.LoadData(ObjectKey(model),
            AudioCoverFetcher(model.replace(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER, ""), context))
    }

    override fun handles(model: String): Boolean {
        return model.startsWith(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER)
    }
}
