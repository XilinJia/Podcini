package ac.mdiq.podvinci.ui.glide

import android.content.ContentResolver
import android.text.TextUtils
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import ac.mdiq.podvinci.core.service.download.PodVinciHttpClient.newBuilder
import ac.mdiq.podvinci.core.util.NetworkUtils.isImageAllowed
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedMedia
import okhttp3.*
import okhttp3.Interceptor.Chain
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.Volatile

/**
 * {@see com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader}.
 */
internal class ApOkHttpUrlLoader private constructor(private val client: OkHttpClient?) :
    ModelLoader<String, InputStream> {
    /**
     * The default factory for [ApOkHttpUrlLoader]s.
     */
    class Factory internal constructor() : ModelLoaderFactory<String, InputStream> {
        private val client: OkHttpClient? = internalClient

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return ApOkHttpUrlLoader(client)
        }

        override fun teardown() {
            // Do nothing, this instance doesn't own the client.
        }

        companion object {
            @Volatile
            private var internalClient: OkHttpClient? = null
                get() {
                    if (field == null) {
                        synchronized(Factory::class.java) {
                            if (field == null) {
                                val builder: OkHttpClient.Builder = newBuilder()
                                builder.interceptors().add(NetworkAllowanceInterceptor())
                                builder.cache(null) // Handled by Glide
                                field = builder.build()
                            }
                        }
                    }
                    return field
                }
        }
    }

    override fun buildLoadData(model: String,
                               width: Int,
                               height: Int,
                               options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(ObjectKey(model), ResizingOkHttpStreamFetcher(client, GlideUrl(model)))
    }

    override fun handles(model: String): Boolean {
        return (!TextUtils.isEmpty(model) // If the other loaders fail, do not attempt to load as web resource
                && !model.startsWith(Feed.PREFIX_GENERATIVE_COVER)
                && !model.startsWith(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER) // Leave content URIs to Glide's default loaders
                && !model.startsWith(ContentResolver.SCHEME_CONTENT)
                && !model.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE))
    }

    private class NetworkAllowanceInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Chain): Response {
            return if (isImageAllowed) {
                chain.proceed(chain.request())
            } else {
                Response.Builder()
                    .protocol(Protocol.HTTP_2)
                    .code(420)
                    .message("Policy Not Fulfilled")
                    .body(ResponseBody.create(null, ByteArray(0)))
                    .request(chain.request())
                    .build()
            }
        }
    }
}