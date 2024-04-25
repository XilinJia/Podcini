package ac.mdiq.podcini.glide

import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.storage.model.feed.EmbeddedChapterImage
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import okhttp3.Request.Builder
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

class ChapterImageModelLoader : ModelLoader<EmbeddedChapterImage?, ByteBuffer?> {
    class Factory : ModelLoaderFactory<EmbeddedChapterImage?, ByteBuffer?> {
        override fun build(unused: MultiModelLoaderFactory): ModelLoader<EmbeddedChapterImage?, ByteBuffer?> {
            return ChapterImageModelLoader()
        }

        override fun teardown() {
            // Do nothing.
        }
    }

    override fun buildLoadData(model: EmbeddedChapterImage, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer?> {
        return ModelLoader.LoadData(ObjectKey(model), EmbeddedImageFetcher(model))
    }

    override fun handles(model: EmbeddedChapterImage): Boolean {
        return true
    }

    internal class EmbeddedImageFetcher(private val image: EmbeddedChapterImage) : DataFetcher<ByteBuffer?> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer?>) {
            var stream: BufferedInputStream? = null
            try {
                if (image.media.localFileAvailable()) {
                    val localFile = File(image.media.getLocalMediaUrl())
                    stream = BufferedInputStream(FileInputStream(localFile))
                    IOUtils.skip(stream, image.position.toLong())
                    val imageContent = ByteArray(image.length)
                    IOUtils.read(stream, imageContent, 0, image.length)
                    callback.onDataReady(ByteBuffer.wrap(imageContent))
                } else {
                    val httpReq = Builder()
                    // Skipping would download the whole file
                    httpReq.header("Range", "bytes=" + image.position + "-" + (image.position + image.length))
                    val url = image.media.getStreamUrl()
                    if (url != null) httpReq.url(url)
                    val response = getHttpClient()!!.newCall(httpReq.build()).execute()
                    if (!response.isSuccessful || response.body == null) throw IOException("Invalid response: " + response.code + " " + response.message)

                    callback.onDataReady(ByteBuffer.wrap(response.body!!.bytes()))
                }
            } catch (e: IOException) {
                callback.onLoadFailed(e)
            } finally {
                IOUtils.closeQuietly(stream)
            }
        }

        override fun cleanup() {
            // nothing to clean up
        }

        override fun cancel() {
            // cannot cancel
        }

        override fun getDataClass(): Class<ByteBuffer?> {
            return ByteBuffer::class.java as Class<ByteBuffer?>
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }
    }
}
