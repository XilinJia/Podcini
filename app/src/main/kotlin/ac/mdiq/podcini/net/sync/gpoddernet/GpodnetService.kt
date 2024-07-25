package ac.mdiq.podcini.net.sync.gpoddernet

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.net.sync.HostnameParser
import ac.mdiq.podcini.net.sync.ResponseMapper.readEpisodeActionsFromJsonObject
import ac.mdiq.podcini.net.sync.ResponseMapper.readSubscriptionChangesFromJsonObject
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetDevice
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetDevice.DeviceType
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetEpisodeActionPostResponse
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetPodcast
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetUploadChangesResponse
import ac.mdiq.podcini.net.sync.model.*
import ac.mdiq.podcini.util.Logd
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request.Builder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.*
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min

/**
 * Communicates with the gpodder.net service.
 */
class GpodnetService(private val httpClient: OkHttpClient, baseHosturl: String?,
                     private val deviceId: String, private var username: String, private var password: String) : ISyncService {
    val TAG = this::class.simpleName ?: "Anonymous"

    private val baseScheme: String?
    private val basePort: Int
    private val baseHost: String?
    private var loggedIn = false

    init {
        val hostname = HostnameParser(baseHosturl ?: DEFAULT_BASE_HOST)
        this.baseHost = hostname.host
        this.basePort = hostname.port
        this.baseScheme = hostname.scheme
    }

    private fun requireLoggedIn() {
        check(loggedIn) { "Not logged in" }
    }

    /**
     * Searches the podcast directory for a given string.
     * @param query          The search query
     * @param scaledLogoSize The size of the logos that are returned by the search query.
     * Must be in range 1..256. If the value is out of range, the
     * default value defined by the gpodder.net API will be used.
     */
    @Throws(GpodnetServiceException::class)
    fun searchPodcasts(query: String?, scaledLogoSize: Int): List<GpodnetPodcast> {
        val parameters = if ((scaledLogoSize in 1..256)) String.format(Locale.US, "q=%s&scale_logo=%d", query, scaledLogoSize) else String.format("q=%s", query)
        try {
            val url = URI(baseScheme, null, baseHost, basePort, "/search.json", parameters, null).toURL()
            val request: Builder = Builder().url(url)
            val response = executeRequest(request)

            val jsonArray = JSONArray(response)
            return readPodcastListFromJsonArray(jsonArray)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw IllegalStateException(e)
        }
    }

    @get:Throws(GpodnetServiceException::class)
    val devices: List<GpodnetDevice>
        /**
         * Returns all devices of a given user.
         * This method requires authentication.
         * @throws GpodnetServiceAuthenticationException If there is an authentication error.
         */
        get() {
            requireLoggedIn()
            try {
                val url = URI(baseScheme, null, baseHost, basePort, String.format("/api/2/devices/%s.json", username), null, null).toURL()
                val request: Builder = Builder().url(url)
                val response = executeRequest(request)
                val devicesArray = JSONArray(response)
                return readDeviceListFromJsonArray(devicesArray)
            } catch (e: JSONException) {
                e.printStackTrace()
                throw GpodnetServiceException(e)
            } catch (e: MalformedURLException) {
                e.printStackTrace()
                throw GpodnetServiceException(e)
            } catch (e: URISyntaxException) {
                e.printStackTrace()
                throw GpodnetServiceException(e)
            }
        }

    /**
     * Configures the device of a given user.
     * This method requires authentication.
     * @param deviceId The ID of the device that should be configured.
     * @throws GpodnetServiceAuthenticationException If there is an authentication error.
     */
    @Throws(GpodnetServiceException::class)
    fun configureDevice(deviceId: String, caption: String?, type: DeviceType?) {
        requireLoggedIn()
        try {
            val url = URI(baseScheme, null, baseHost, basePort, String.format("/api/2/devices/%s/%s.json", username, deviceId), null, null).toURL()
            val content: String
            if (caption != null || type != null) {
                val jsonContent = JSONObject()
                if (caption != null) jsonContent.put("caption", caption)
                if (type != null) jsonContent.put("type", type.toString())
                content = jsonContent.toString()
            } else content = ""

            val body = RequestBody.create(JSON, content)
            val request: Builder = Builder().post(body).url(url)
            executeRequest(request)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    /**
     * Uploads the subscriptions of a specific device.
     * This method requires authentication.
     * @param deviceId      The ID of the device whose subscriptions should be updated.
     * @param subscriptions A list of feed URLs containing all subscriptions of the device.
     * @throws IllegalArgumentException              If username, deviceId or subscriptions is null.
     * @throws GpodnetServiceAuthenticationException If there is an authentication error.
     */
    @Throws(GpodnetServiceException::class)
    fun uploadSubscriptions(deviceId: String, subscriptions: List<String?>) {
        requireLoggedIn()
        try {
            val url = URI(baseScheme, null, baseHost, basePort, String.format("/subscriptions/%s/%s.txt", username, deviceId), null, null).toURL()
            val builder = StringBuilder()
            for (s in subscriptions) {
                builder.append(s)
                builder.append("\n")
            }
            val body = RequestBody.create(TEXT, builder.toString())
            val request: Builder = Builder().put(body).url(url)
            executeRequest(request)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    /**
     * Updates the subscription list of a specific device.
     * This method requires authentication.
     * @param added    Collection of feed URLs of added feeds. This Collection MUST NOT contain any duplicates
     * @param removed  Collection of feed URLs of removed feeds. This Collection MUST NOT contain any duplicates
     * @return a GpodnetUploadChangesResponse. See [GpodnetUploadChangesResponse] for details.
     * @throws GpodnetServiceException  if added or removed contain duplicates or if there is an authentication error.
     */
    @Throws(GpodnetServiceException::class)
    override fun uploadSubscriptionChanges(added: List<String>, removed: List<String>): UploadChangesResponse {
        requireLoggedIn()
        try {
            val url = URI(baseScheme, null, baseHost, basePort, String.format("/api/2/subscriptions/%s/%s.json", username, deviceId), null, null).toURL()

            val requestObject = JSONObject()
            requestObject.put("add", JSONArray(added))
            requestObject.put("remove", JSONArray(removed))

            val body = RequestBody.create(JSON, requestObject.toString())
            val request: Builder = Builder().post(body).url(url)

            val response = executeRequest(request)
            return GpodnetUploadChangesResponse.fromJSONObject(response)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    /**
     * Returns all subscription changes of a specific device. This method requires authentication.
     * @param timestamp A timestamp that can be used to receive all changes since a specific point in time.
     * @throws GpodnetServiceAuthenticationException If there is an authentication error.
     */
    @Throws(GpodnetServiceException::class)
    override fun getSubscriptionChanges(timestamp: Long): SubscriptionChanges {
        requireLoggedIn()
        val params = String.format(Locale.US, "since=%d", timestamp)
        val path = String.format("/api/2/subscriptions/%s/%s.json", username, deviceId)
        try {
            val url = URI(baseScheme, null, baseHost, basePort, path, params, null).toURL()
            val request: Builder = Builder().url(url)

            val response = executeRequest(request)
            val changes = JSONObject(response)
            return readSubscriptionChangesFromJsonObject(changes)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw IllegalStateException(e)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    /**
     * Updates the episode actions. This method requires authentication.
     * @param episodeActions Collection of episode actions.
     * @return a GpodnetUploadChangesResponse. See [GpodnetUploadChangesResponse] for details.
     * @throws GpodnetServiceException  if added or removed contain duplicates or if there is an authentication error.
     */
    @Throws(SyncServiceException::class)
    override fun uploadEpisodeActions(episodeActions: List<EpisodeAction>): UploadChangesResponse? {
        requireLoggedIn()
        var response: UploadChangesResponse? = null
        var i = 0
        while (i < episodeActions.size) {
            response = uploadEpisodeActionsPartial(episodeActions, i, min(episodeActions.size.toDouble(), (i + UPLOAD_BULK_SIZE).toDouble()).toInt())
            i += UPLOAD_BULK_SIZE
        }
        return response
    }

    @Throws(SyncServiceException::class)
    private fun uploadEpisodeActionsPartial(episodeActions: List<EpisodeAction?>?, from: Int, to: Int): UploadChangesResponse {
        try {
            Logd(TAG, "Uploading partial actions " + from + " to " + to + " of " + episodeActions!!.size)
            val url = URI(baseScheme, null, baseHost, basePort,
                String.format("/api/2/episodes/%s.json", username), null, null).toURL()

            val list = JSONArray()
            for (i in from until to) {
                val episodeAction = episodeActions[i]
                val obj = episodeAction!!.writeToJsonObjectForServer()
                if (obj != null) {
                    obj.put("device", deviceId)
                    list.put(obj)
                }
            }

            val body = RequestBody.create(JSON, list.toString())
            val request: Builder = Builder().post(body).url(url)

            val response = executeRequest(request)
            return GpodnetEpisodeActionPostResponse.fromJSONObject(response)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    /**
     * Returns all subscription changes of a specific device. This method requires authentication.
     * @param timestamp A timestamp that can be used to receive all changes since a specific point in time.
     * @throws SyncServiceException If there is an authentication error.
     */
    @Throws(SyncServiceException::class)
    override fun getEpisodeActionChanges(timestamp: Long): EpisodeActionChanges {
        requireLoggedIn()
        val params = String.format(Locale.US, "since=%d", timestamp)
        val path = String.format("/api/2/episodes/%s.json", username)
        try {
            val url = URI(baseScheme, null, baseHost, basePort, path, params, null).toURL()
            val request: Builder = Builder().url(url)

            val response = executeRequest(request)
            val json = JSONObject(response)
            return readEpisodeActionsFromJsonObject(json)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw IllegalStateException(e)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    /**
     * Logs in a specific user. This method must be called if any of the methods that require authentication is used.
     * @throws IllegalArgumentException If username or password is null.
     */
    @Throws(GpodnetServiceException::class)
    override fun login() {
        val url: URL
        try {
            url = URI(baseScheme, null, baseHost, basePort, String.format("/api/2/auth/%s/login.json", username), null, null).toURL()
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
        val requestBody = RequestBody.create(TEXT, "")
        val request: Request = Builder().url(url).post(requestBody).build()
        try {
            val credential = basic(username, password, Charset.forName("UTF-8"))
            val authRequest = request.newBuilder().header("Authorization", credential).build()
            val response = httpClient.newCall(authRequest).execute()
            checkStatusCode(response)
            response.body!!.close()
            this.loggedIn = true
        } catch (e: Exception) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    @Throws(GpodnetServiceException::class)
    private fun executeRequest(requestB: Builder): String {
        val request: Request = requestB.build()
        val responseString: String
        val response: Response
        var body: ResponseBody? = null
        try {
            response = httpClient.newCall(request).execute()
            checkStatusCode(response)
            body = response.body
            responseString = getStringFromResponseBody(body!!)
        } catch (e: IOException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        } finally {
            body?.close()
        }
        return responseString
    }

    @Throws(GpodnetServiceException::class)
    private fun getStringFromResponseBody(body: ResponseBody): String {
        val outputStream: ByteArrayOutputStream
        val contentLength = body.contentLength().toInt()
        outputStream = if (contentLength > 0) ByteArrayOutputStream(contentLength) else ByteArrayOutputStream()
        try {
            val buffer = ByteArray(8 * 1024)
            val inVal = body.byteStream()
            var count: Int
            while ((inVal.read(buffer).also { count = it }) > 0) {
                outputStream.write(buffer, 0, count)
            }
            return outputStream.toString("UTF-8")
        } catch (e: IOException) {
            e.printStackTrace()
            throw GpodnetServiceException(e)
        }
    }

    @Throws(GpodnetServiceException::class)
    private fun checkStatusCode(response: Response) {
        val responseCode = response.code
        if (responseCode != HttpURLConnection.HTTP_OK) {
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw GpodnetServiceAuthenticationException("Wrong username or password")
            } else {
                if (BuildConfig.DEBUG) {
                    try {
                        Logd(TAG, response.body!!.string())
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                if (responseCode >= 500) {
                    throw GpodnetServiceBadStatusCodeException("Gpodder.net is currently unavailable (code " + responseCode + ")", responseCode)
                } else {
                    throw GpodnetServiceBadStatusCodeException("Unable to connect to Gpodder.net (code " + responseCode + ": " + response.message + ")", responseCode)
                }
            }
        }
    }

    @Throws(JSONException::class)
    private fun readPodcastListFromJsonArray(array: JSONArray): List<GpodnetPodcast> {
        val result: MutableList<GpodnetPodcast> = ArrayList(array.length())
        for (i in 0 until array.length()) {
            result.add(readPodcastFromJsonObject(array.getJSONObject(i)))
        }
        return result
    }

    @Throws(JSONException::class)
    private fun readPodcastFromJsonObject(`object`: JSONObject): GpodnetPodcast {
        val url = `object`.getString("url")

        val title: String
        val titleObj = `object`.opt("title")
        title = if (titleObj is String) titleObj else url

        val description: String
        val descriptionObj = `object`.opt("description")
        description = if (descriptionObj is String) descriptionObj else ""

        val subscribers = `object`.getInt("subscribers")

        val logoUrlObj = `object`.opt("logo_url")
        var logoUrl = if ((logoUrlObj is String)) logoUrlObj else null
        if (logoUrl == null) {
            val scaledLogoUrl = `object`.opt("scaled_logo_url")
            if (scaledLogoUrl is String) logoUrl = scaledLogoUrl
        }

        var website: String? = null
        val websiteObj = `object`.opt("website")
        if (websiteObj is String) website = websiteObj
        val mygpoLink = `object`.getString("mygpo_link")

        var author: String? = null
        val authorObj = `object`.opt("author")
        if (authorObj is String) author = authorObj
        return GpodnetPodcast(url, title, description, subscribers, logoUrl!!, website!!, mygpoLink, author!!)
    }

    @Throws(JSONException::class)
    private fun readDeviceListFromJsonArray(array: JSONArray): List<GpodnetDevice> {
        val result: MutableList<GpodnetDevice> = ArrayList(array.length())
        for (i in 0 until array.length()) {
            result.add(readDeviceFromJsonObject(array.getJSONObject(i)))
        }
        return result
    }

    @Throws(JSONException::class)
    private fun readDeviceFromJsonObject(`object`: JSONObject): GpodnetDevice {
        val id = `object`.getString("id")
        val caption = `object`.getString("caption")
        val type = `object`.getString("type")
        val subscriptions = `object`.getInt("subscriptions")
        return GpodnetDevice(id, caption, type, subscriptions)
    }

    override fun logout() {}

    fun setCredentials(username: String, password: String) {
        this.username = username
        this.password = password
    }

    open class GpodnetServiceException : SyncServiceException {
        constructor(message: String?) : super(message)
        constructor(e: Throwable?) : super(e)
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    class GpodnetServiceAuthenticationException(message: String?) : GpodnetServiceException(message) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    class GpodnetServiceBadStatusCodeException(message: String?, private val statusCode: Int) :
        GpodnetServiceException(message) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        private const val DEFAULT_BASE_HOST = "gpodder.net"
        private const val UPLOAD_BULK_SIZE = 30
        private val TEXT: MediaType = "plain/text; charset=utf-8".toMediaType()
        private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
    }
}
