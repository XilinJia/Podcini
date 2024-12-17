package ac.mdiq.podcini.net.sync.gpoddernet.model

import ac.mdiq.podcini.net.sync.model.UploadChangesResponse
import org.json.JSONException

/**
 * Object returned by [GpodnetService] in uploadChanges method.
 */
/**
 * URLs that should be updated. The key of the map is the original URL, the value of the map
 * is the sanitized URL.
 */
class GpodnetUploadChangesResponse(timestamp: Long, val updatedUrls: Map<String, String>) : UploadChangesResponse(timestamp) {

    override fun toString(): String {
        return "GpodnetUploadChangesResponse{timestamp=$timestamp, updatedUrls=$updatedUrls}"
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
        fun fromJSONObject(objectString: String?): GpodnetUploadChangesResponse {
//            val `object` = JSONObject(objectString)
//            val timestamp = `object`.getLong("timestamp")
//            val updatedUrls: MutableMap<String, String> = ArrayMap()
//            val urls = `object`.getJSONArray("update_urls")
//            for (i in 0 until urls.length()) {
//                val urlPair = urls.getJSONArray(i)
//                updatedUrls[urlPair.getString(0)] = urlPair.getString(1)
//            }
            val (timestamp, updatedUrls) = UploadChangesResponse.fromJSONObject(objectString)
            return GpodnetUploadChangesResponse(timestamp, updatedUrls)
        }
    }
}
