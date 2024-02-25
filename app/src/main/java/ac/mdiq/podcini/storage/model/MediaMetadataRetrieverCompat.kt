package ac.mdiq.podcini.storage.model

import android.media.MediaMetadataRetriever
import java.io.IOException

/**
 * On SDK<29, this class does not have a close method yet, so the app crashes when using try-with-resources.
 */
class MediaMetadataRetrieverCompat : MediaMetadataRetriever(), AutoCloseable {
    override fun close() {
        try {
            release()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
