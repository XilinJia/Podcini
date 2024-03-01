package ac.mdiq.podcini.net.sync.nextcloud

import ac.mdiq.podcini.net.sync.HostnameParser
import ac.mdiq.podcini.net.sync.gpoddernet.mapper.ResponseMapper
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetUploadChangesResponse
import ac.mdiq.podcini.net.sync.model.*
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import kotlin.math.min

class NextcloudSyncService(private val httpClient: OkHttpClient, baseHosturl: String?,
                           private val username: String, private val password: String
) : ISyncService {
    private val hostname = HostnameParser(baseHosturl)

    override fun login() {
    }

    @Throws(SyncServiceException::class)
    override fun getSubscriptionChanges(lastSync: Long): SubscriptionChanges {
        try {
            val url: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/subscriptions")
            url.addQueryParameter("since", "" + lastSync)
            val responseString = performRequest(url, "GET", null)
            val json = JSONObject(responseString)
            return ResponseMapper.readSubscriptionChangesFromJsonObject(json)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: Exception) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    @Throws(NextcloudSynchronizationServiceException::class)
    override fun uploadSubscriptionChanges(addedFeeds: List<String?>?,
                                           removedFeeds: List<String?>?
    ): UploadChangesResponse {
        try {
            val url: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/subscription_change/create")
            val requestObject = JSONObject()
            requestObject.put("add", JSONArray(addedFeeds))
            requestObject.put("remove", JSONArray(removedFeeds))
            val requestBody = RequestBody.create("application/json".toMediaType(), requestObject.toString())
            performRequest(url, "POST", requestBody)
        } catch (e: Exception) {
            e.printStackTrace()
            throw NextcloudSynchronizationServiceException(e)
        }

        return GpodnetUploadChangesResponse(System.currentTimeMillis() / 1000, HashMap())
    }

    @Throws(SyncServiceException::class)
    override fun getEpisodeActionChanges(timestamp: Long): EpisodeActionChanges {
        try {
            val uri: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/episode_action")
            uri.addQueryParameter("since", "" + timestamp)
            val responseString = performRequest(uri, "GET", null)
            val json = JSONObject(responseString)
            return ResponseMapper.readEpisodeActionsFromJsonObject(json)
        } catch (e: JSONException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            throw SyncServiceException(e)
        } catch (e: Exception) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    @Throws(NextcloudSynchronizationServiceException::class)
    override fun uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction?>?): UploadChangesResponse {
        var i = 0
        while (i < queuedEpisodeActions!!.size) {
            uploadEpisodeActionsPartial(queuedEpisodeActions,
                i, min(queuedEpisodeActions.size.toDouble(), (i + UPLOAD_BULK_SIZE).toDouble()).toInt())
            i += UPLOAD_BULK_SIZE
        }
        return NextcloudGpodderEpisodeActionPostResponse(System.currentTimeMillis() / 1000)
    }

    @Throws(NextcloudSynchronizationServiceException::class)
    private fun uploadEpisodeActionsPartial(queuedEpisodeActions: List<EpisodeAction?>?, from: Int, to: Int) {
        try {
            val list = JSONArray()
            for (i in from until to) {
                val episodeAction = queuedEpisodeActions!![i]
                val obj = episodeAction!!.writeToJsonObject()
                if (obj != null) {
                    list.put(obj)
                }
            }
            val url: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/episode_action/create")
            val requestBody = RequestBody.create("application/json".toMediaType(), list.toString())
            performRequest(url, "POST", requestBody)
        } catch (e: Exception) {
            e.printStackTrace()
            throw NextcloudSynchronizationServiceException(e)
        }
    }

    @Throws(IOException::class)
    private fun performRequest(url: HttpUrl.Builder, method: String, body: RequestBody?): String {
        val request: Request = Request.Builder()
            .url(url.build())
            .header("Authorization", basic(username, password))
            .header("Accept", "application/json")
            .method(method, body)
            .build()
        val response = httpClient.newCall(request).execute()
        if (response.code != 200) {
            throw IOException("Response code: " + response.code)
        }
        return response.body!!.string()
    }

    private fun makeUrl(path: String): HttpUrl.Builder {
        val builder = HttpUrl.Builder()
        if (hostname.scheme != null) builder.scheme(hostname.scheme!!)
        if (hostname.host != null) builder.host(hostname.host!!)
        return builder.port(hostname.port)
            .addPathSegments(hostname.subfolder + path)
    }

    override fun logout() {
    }

    private class NextcloudGpodderEpisodeActionPostResponse(epochSecond: Long) : UploadChangesResponse(epochSecond)
    companion object {
        private const val UPLOAD_BULK_SIZE = 30
    }
}

