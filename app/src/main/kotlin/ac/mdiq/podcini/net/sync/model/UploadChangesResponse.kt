package ac.mdiq.podcini.net.sync.model

import androidx.collection.ArrayMap
import org.json.JSONObject

/**
 * timestamp/ID that can be used for requesting changes since this upload.
 */
abstract class UploadChangesResponse(@JvmField val timestamp: Long) {

    companion object {

        fun fromJSONObject(objectString: String?): Pair<Long,  Map<String, String>> {
            val `object` = JSONObject(objectString)
            val timestamp = `object`.getLong("timestamp")
            val updatedUrls: MutableMap<String, String> = ArrayMap()
            val urls = `object`.getJSONArray("update_urls")
            for (i in 0 until urls.length()) {
                val urlPair = urls.getJSONArray(i)
                updatedUrls[urlPair.getString(0)] = urlPair.getString(1)
            }
            return Pair(timestamp, updatedUrls)
        }
    }
}
