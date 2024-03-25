package ac.mdiq.podcini.ui.glide

import ac.mdiq.podcini.storage.model.feed.EmbeddedChapterImage
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * {@see com.bumptech.glide.integration.okhttp.OkHttpGlideModule}
 */
@GlideModule
class ApGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(RequestOptions()
            .format(DecodeFormat.PREFER_ARGB_8888)
            .diskCacheStrategy(DiskCacheStrategy.ALL))
        builder.setLogLevel(Log.WARN)
        @SuppressLint("UsableSpace") val spaceAvailable = context.cacheDir.usableSpace
        val imageCacheSize = if ((spaceAvailable > 2 * GIGABYTES)) (250 * MEGABYTES) else (50 * MEGABYTES)
        Log.d(TAG, "Free space on cache dir: $spaceAvailable, using image cache size: $imageCacheSize")
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, imageCacheSize))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(String::class.java, InputStream::class.java, MetadataRetrieverLoader.Factory(context))
        registry.append(String::class.java, InputStream::class.java, GenerativePlaceholderImageModelLoader.Factory())
        registry.append(String::class.java, InputStream::class.java, ApOkHttpUrlLoader.Factory())
        registry.append(String::class.java, InputStream::class.java, NoHttpStringLoader.StreamFactory())

        registry.append(EmbeddedChapterImage::class.java, ByteBuffer::class.java, ChapterImageModelLoader.Factory())
    }

    companion object {
        private const val TAG = "ApGlideModule"
        private const val MEGABYTES = (1024 * 1024).toLong()
        private const val GIGABYTES = (1024 * 1024 * 1024).toLong()
    }
}
