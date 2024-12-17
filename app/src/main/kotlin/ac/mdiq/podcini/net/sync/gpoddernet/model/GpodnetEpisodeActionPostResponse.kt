package ac.mdiq.podcini.net.sync.gpoddernet.model

import androidx.collection.ArrayMap
import ac.mdiq.podcini.net.sync.model.UploadChangesResponse
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.json.JSONException
import org.json.JSONObject

/**
 * URLs that should be updated. The key of the map is the original URL, the value of the map
 * is the sanitized URL.
 */
class GpodnetEpisodeActionPostResponse private constructor(timestamp: Long, private val updatedUrls: Map<String, String>) : UploadChangesResponse(timestamp) {

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    companion object {
        /**
         * Creates a new GpodnetUploadChangesResponse-object from a JSON object that was
         * returned by an uploadChanges call.
         *
         * @throws org.json.JSONException If the method could not parse the JSONObject.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSONObject(objectString: String?): GpodnetEpisodeActionPostResponse {
//            val `object` = JSONObject(objectString)
//            val timestamp = `object`.getLong("timestamp")
//            val urls = `object`.getJSONArray("update_urls")
//            val updatedUrls: MutableMap<String, String> = ArrayMap(urls.length())
//            for (i in 0 until urls.length()) {
//                val urlPair = urls.getJSONArray(i)
//                updatedUrls[urlPair.getString(0)] = urlPair.getString(1)
//            }
            val (timestamp, updatedUrls) = UploadChangesResponse.fromJSONObject(objectString)
            return GpodnetEpisodeActionPostResponse(timestamp, updatedUrls)
        }
    }
}

