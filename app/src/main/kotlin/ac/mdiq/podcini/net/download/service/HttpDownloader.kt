package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.parser.utils.DateUtils.parse
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.net.utils.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.storage.utils.StorageUtils.freeSpaceAvailable
import ac.mdiq.podcini.net.utils.URIUtil.getURIFromRequestUrl
import android.util.Log
import okhttp3.*
import okhttp3.internal.http.StatusLine
import org.apache.commons.io.IOUtils
import java.io.*
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*

class HttpDownloader(request: DownloadRequest) : Downloader(request) {

    override fun download() {
        Logd(TAG, "starting download()")
        if (downloadRequest.source == null || downloadRequest.destination == null) return

        val destination = File(downloadRequest.destination)
        val fileExists = destination.exists()

        var out: RandomAccessFile? = null
        val connection: InputStream
        var responseBody: ResponseBody? = null

        try {
            val uri = getURIFromRequestUrl(downloadRequest.source)
            val httpReq: Request.Builder = Request.Builder().url(uri.toURL())
            httpReq.tag(downloadRequest)
            httpReq.cacheControl(CacheControl.Builder().noStore().build())

            Logd(TAG, "starting download: " + downloadRequest.feedfileType + " " + uri.scheme)
            if (downloadRequest.feedfileType == EpisodeMedia.FEEDFILETYPE_FEEDMEDIA) {
                // set header explicitly so that okhttp doesn't do transparent gzip
                Logd(TAG, "addHeader(\"Accept-Encoding\", \"identity\")")
                httpReq.addHeader("Accept-Encoding", "identity")
                httpReq.cacheControl(CacheControl.Builder().noCache().build()) // noStore breaks CDNs
            }

            if (uri.scheme == "http") httpReq.addHeader("Upgrade-Insecure-Requests", "1")

            if (!downloadRequest.lastModified.isNullOrEmpty()) {
                val lastModified = downloadRequest.lastModified
                val lastModifiedDate = parse(lastModified)
                if (lastModifiedDate != null) {
                    val threeDaysAgo = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 3
                    if (lastModifiedDate.time > threeDaysAgo) {
                        Logd(TAG, "addHeader(\"If-Modified-Since\", \"$lastModified\")")
                        httpReq.addHeader("If-Modified-Since", lastModified?:"")
                    }
                } else {
                    Logd(TAG, "addHeader(\"If-None-Match\", \"$lastModified\")")
                    httpReq.addHeader("If-None-Match", lastModified?:"")
                }
            }

            // add range header if necessary
            if (fileExists && destination.length() > 0) {
                downloadRequest.soFar = destination.length()
                httpReq.addHeader("Range", "bytes=" + downloadRequest.soFar + "-")
                Logd(TAG, "Adding range header: " + downloadRequest.soFar)
            }

            val response = newCall(httpReq)
            responseBody = response.body
            val contentEncodingHeader = response.header("Content-Encoding")
            var isGzip = false
            if (!contentEncodingHeader.isNullOrEmpty()) isGzip = (contentEncodingHeader.lowercase(Locale.getDefault()) == "gzip")

            Logd(TAG, "Response code is " + response.code)// check if size specified in the response header is the same as the size of the
            // written file. This check cannot be made if compression was used
            //                    Log.d(TAG,"buffer: $buffer")
            when {
                !response.isSuccessful && response.code == HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    Logd(TAG, "Feed '" + downloadRequest.source + "' not modified since last update, Download canceled")
                    onCancelled()
                    return
                }
                !response.isSuccessful || response.body == null -> {
                    callOnFailByResponseCode(response)
                    return
                }
                downloadRequest.feedfileType == EpisodeMedia.FEEDFILETYPE_FEEDMEDIA && isContentTypeTextAndSmallerThan100kb(response) -> {
                    onFail(DownloadError.ERROR_FILE_TYPE, null)
                    return
                }
                else -> {
                    checkIfRedirect(response)
                    connection = BufferedInputStream(responseBody!!.byteStream())

                    val contentRangeHeader = if (fileExists) response.header("Content-Range") else null
                    if (fileExists && response.code == HttpURLConnection.HTTP_PARTIAL && !contentRangeHeader.isNullOrEmpty()) {
                        val start = contentRangeHeader.substring("bytes ".length, contentRangeHeader.indexOf("-"))
                        downloadRequest.soFar = start.toLong()
                        Logd(TAG, "Starting download at position " + downloadRequest.soFar)

                        out = RandomAccessFile(destination, "rw")
                        out.seek(downloadRequest.soFar)
                    } else {
                        Logd(TAG, "destination path: ${destination.absolutePath}")
                        var success = destination.delete()
                        success = success or destination.createNewFile()
                        if (!success) throw IOException("Unable to recreate partially downloaded file ${destination.absolutePath}")

                        out = RandomAccessFile(destination, "rw")
                    }

                    val buffer = ByteArray(BUFFER_SIZE)
                    var count = 0
                    downloadRequest.setStatusMsg(R.string.download_running)
                    Logd(TAG, "Getting size of download")
                    downloadRequest.size = responseBody.contentLength() + downloadRequest.soFar
                    Logd(TAG, "downloadRequest size is " + downloadRequest.size)
                    if (downloadRequest.size < 0) downloadRequest.size = DownloadResult.SIZE_UNKNOWN.toLong()

                    val freeSpace = freeSpaceAvailable
                    Logd(TAG, "Free space is $freeSpace")
                    if (downloadRequest.size != DownloadResult.SIZE_UNKNOWN.toLong() && downloadRequest.size > freeSpace) {
                        onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null)
                        return
                    }

                    Logd(TAG, "Starting download")
                    try {
                        while (!cancelled && (connection.read(buffer).also { count = it }) != -1) {
                            //                    Log.d(TAG,"buffer: $buffer")
                            out.write(buffer, 0, count)
                            downloadRequest.soFar += count
                            val progressPercent = (100.0 * downloadRequest.soFar / downloadRequest.size).toInt()
                            downloadRequest.progressPercent = progressPercent
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    }
                    if (cancelled) onCancelled()
                    else {
                        // check if size specified in the response header is the same as the size of the
                        // written file. This check cannot be made if compression was used
                        when {
                            !isGzip && downloadRequest.size != DownloadResult.SIZE_UNKNOWN.toLong() && downloadRequest.soFar != downloadRequest.size -> {
                                onFail(DownloadError.ERROR_IO_WRONG_SIZE, "Download completed but size: ${downloadRequest.soFar} does not equal expected size ${downloadRequest.size}")
                                return
                            }
                            downloadRequest.size > 0 && downloadRequest.soFar == 0L -> {
                                onFail(DownloadError.ERROR_IO_ERROR, "Download completed, but nothing was read")
                                return
                            }
                            else -> {
                                val lastModified = response.header("Last-Modified")
                                if (lastModified != null) downloadRequest.setLastModified(lastModified)
                                else downloadRequest.setLastModified(response.header("ETag"))
                                onSuccess()
                            }
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            onFail(DownloadError.ERROR_MALFORMED_URL, e.message)
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            onFail(DownloadError.ERROR_CONNECTION_ERROR, e.message)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            onFail(DownloadError.ERROR_UNKNOWN_HOST, e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            if (wasDownloadBlocked(e)) {
                onFail(DownloadError.ERROR_IO_BLOCKED, e.message)
                return
            }
            val message = e.message
            if (message != null && message.contains("Trust anchor for certification path not found")) {
                onFail(DownloadError.ERROR_CERTIFICATE, e.message)
                return
            }
            onFail(DownloadError.ERROR_IO_ERROR, e.message)
        } catch (e: NullPointerException) {
            // might be thrown by connection.getInputStream()
            e.printStackTrace()
            onFail(DownloadError.ERROR_CONNECTION_ERROR, downloadRequest.source)
        } finally {
            IOUtils.closeQuietly(out)
            IOUtils.closeQuietly(responseBody)
        }
    }

    @Throws(IOException::class)
    private fun newCall(httpReq: Request.Builder): Response {
        var httpClient = getHttpClient()
        try {
            return httpClient.newCall(httpReq.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            if (e.message != null && e.message!!.contains("PROTOCOL_ERROR")) {
                // Apparently some servers announce they support SPDY but then actually don't.
                httpClient = httpClient.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).build()
                return httpClient.newCall(httpReq.build()).execute()
            } else {
                throw e
            }
        }
    }

    private fun isContentTypeTextAndSmallerThan100kb(response: Response): Boolean {
        var contentLength = -1
        val contentLen = response.header("Content-Length")
        if (contentLen != null) {
            try {
                contentLength = contentLen.toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        }
        Logd(TAG, "content length: $contentLength")
        val contentType = response.header("Content-Type")
        Logd(TAG, "content type: $contentType")
        return contentType != null && contentType.startsWith("text/") && contentLength < 100 * 1024
    }

    private fun callOnFailByResponseCode(response: Response) {
        val error: DownloadError
        val details: String
        when (response.code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                error = DownloadError.ERROR_UNAUTHORIZED
                details = response.code.toString()
            }
            HttpURLConnection.HTTP_FORBIDDEN -> {
                error = DownloadError.ERROR_FORBIDDEN
                details = response.code.toString()
            }
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> {
                error = DownloadError.ERROR_NOT_FOUND
                details = response.code.toString()
            }
            else -> {
                error = DownloadError.ERROR_HTTP_DATA_ERROR
                details = response.code.toString()
            }
        }
        onFail(error, details)
    }

    private fun checkIfRedirect(response0: Response) {
        // detect 301 Moved permanently and 308 Permanent Redirect
        var response: Response? = response0
        val responses = ArrayList<Response?>()
        while (response != null) {
            responses.add(response)
            response = response.priorResponse
        }
        if (responses.size < 2) return

        responses.reverse()
        val firstCode = responses[0]!!.code
        val firstUrl = responses[0]!!.request.url.toString()
        val secondUrl = responses[1]!!.request.url.toString()
        when {
            firstCode == HttpURLConnection.HTTP_MOVED_PERM || firstCode == StatusLine.HTTP_PERM_REDIRECT -> {
                Logd(TAG, "Detected permanent redirect from " + downloadRequest.source + " to " + secondUrl)
                permanentRedirectUrl = secondUrl
            }
            secondUrl == firstUrl.replace("http://", "https://") -> {
                Logd(TAG, "Treating http->https non-permanent redirect as permanent: $firstUrl")
                permanentRedirectUrl = secondUrl
            }
        }
    }

    private fun onSuccess() {
        Logd(TAG, "Download was successful")
        result.setSuccessful()
    }

    private fun onFail(reason: DownloadError, reasonDetailed: String?) {
        Logd(TAG, "onFail() called with: reason = [$reason], reasonDetailed = [$reasonDetailed]")
        result.setFailed(reason, reasonDetailed?:"")
    }

    private fun onCancelled() {
        Logd(TAG, "Download was cancelled")
        result.setCancelled()
        cancelled = true
    }

    companion object {
        private val TAG: String = HttpDownloader::class.simpleName ?: "Anonymous"
        private const val BUFFER_SIZE = 8 * 1024
    }
}
