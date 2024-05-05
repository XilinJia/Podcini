package ac.mdiq.podcini.net.download

import android.util.Log
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.util.NetworkUtils.isEpisodeHeadDownloadAllowed
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.Request.Builder
import java.io.File
import java.io.IOException

object MediaSizeLoader {
    private const val TAG = "MediaSizeLoader"

    @UnstableApi @JvmStatic
    fun getFeedMediaSizeObservable(media: FeedMedia): Single<Long?> {
        return Single.create<Long?>(SingleOnSubscribe<Long?> { emitter: SingleEmitter<Long?> ->
            if (!isEpisodeHeadDownloadAllowed) {
                emitter.onSuccess(0L)
                return@SingleOnSubscribe
            }
            var size = Int.MIN_VALUE.toLong()
            when {
                media.isDownloaded() -> {
                    val mediaFile = File(media.getLocalMediaUrl())
                    if (mediaFile.exists()) size = mediaFile.length()
                }
                !media.checkedOnSizeButUnknown() -> {
                    // only query the network if we haven't already checked

                    val url = media.download_url
                    if (url.isNullOrEmpty()) {
                        emitter.onSuccess(0L)
                        return@SingleOnSubscribe
                    }

                    val client = getHttpClient()
                    val httpReq: Builder = Builder()
                        .url(url)
                        .header("Accept-Encoding", "identity")
                        .head()
                    try {
                        val response = client.newCall(httpReq.build()).execute()
                        if (response.isSuccessful) {
                            val contentLength = response.header("Content-Length")?:"0"
                            try {
                                size = contentLength.toInt().toLong()
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, Log.getStackTraceString(e))
                            }
                        }
                    } catch (e: IOException) {
                        emitter.onSuccess(0L)
                        Log.e(TAG, Log.getStackTraceString(e))
                        return@SingleOnSubscribe  // better luck next time
                    }
                }
            }
            Log.d(TAG, "new size: $size")
            // they didn't tell us the size, but we don't want to keep querying on it
            if (size <= 0) media.setCheckedOnSizeButUnknown()
            else media.size = size

            emitter.onSuccess(size)
            DBWriter.persistFeedMedia(media)
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
}
