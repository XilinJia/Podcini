package de.danoeh.antennapod.ui.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat
import java.io.ByteArrayInputStream
import java.io.InputStream

// see https://github.com/bumptech/glide/issues/699
internal class AudioCoverFetcher(private val path: String, private val context: Context) : DataFetcher<InputStream?> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream?>) {
        try {
            MediaMetadataRetrieverCompat().use { retriever ->
                if (path.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    retriever.setDataSource(context, Uri.parse(path))
                } else {
                    retriever.setDataSource(path)
                }
                val picture = retriever.embeddedPicture
                if (picture != null) {
                    callback.onDataReady(ByteArrayInputStream(picture))
                }
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // nothing to clean up
    }

    override fun cancel() {
        // cannot cancel
    }

    override fun getDataClass(): Class<InputStream?> {
        return InputStream::class.java as Class<InputStream?>
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }
}
