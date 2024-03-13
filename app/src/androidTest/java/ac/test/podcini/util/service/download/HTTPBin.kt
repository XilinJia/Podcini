package de.test.podcini.util.service.download

import ac.mdiq.podcini.BuildConfig
import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.IStatus
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.io.*
import java.net.URLConnection
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * Http server for testing purposes
 *
 *
 * Supported features:
 *
 *
 * /status/code: Returns HTTP response with the given status code
 * /redirect/n:  Redirects n times
 * /delay/n:     Delay response for n seconds
 * /basic-auth/username/password: Basic auth with username and password
 * /gzip/n:      Send gzipped data of size n bytes
 * /files/id:     Accesses the file with the specified ID (this has to be added first via serveFile).
 */
class HTTPBin : NanoHTTPD(0) {
    private val servedFiles: MutableList<File> = ArrayList()

    val baseUrl: String
        get() = "http://127.0.0.1:$listeningPort"

    /**
     * Adds the given file to the server.
     *
     * @return The ID of the file or -1 if the file could not be added to the server.
     */
    @Synchronized
    fun serveFile(file: File?): Int {
        requireNotNull(file) { "file = null" }
        if (!file.exists()) {
            return -1
        }
        for (i in servedFiles.indices) {
            if (servedFiles[i].absolutePath == file.absolutePath) {
                return i
            }
        }
        servedFiles.add(file)
        return servedFiles.size - 1
    }

    @Synchronized
    fun accessFile(id: Int): File? {
        return if (id < 0 || id >= servedFiles.size) {
            null
        } else {
            servedFiles[id]
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (BuildConfig.DEBUG) Log.d(TAG, "Requested url: " + session.uri)

        val segments = session.uri.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (segments.size < 3) {
            Log.w(TAG, String.format(Locale.US, "Invalid number of URI segments: %d %s",
                segments.size, segments.contentToString()))
            get404Error()
        }

        val func = segments[1]
        val param = segments[2]
        val headers = session.headers

        if (func.equals("status", ignoreCase = true)) {
            try {
                val code = param.toInt()
                return Response(getStatus(code), MIME_HTML, "")
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return internalError
            }
        } else if (func.equals("redirect", ignoreCase = true)) {
            try {
                val times = param.toInt()
                if (times < 0) {
                    throw NumberFormatException("times <= 0: $times")
                }

                return getRedirectResponse(times - 1)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return internalError
            }
        } else if (func.equals("delay", ignoreCase = true)) {
            try {
                val sec = param.toInt()
                if (sec <= 0) {
                    throw NumberFormatException("sec <= 0: $sec")
                }

                Thread.sleep(sec * 1000L)
                return oKResponse
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return internalError
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return internalError
            }
        } else if (func.equals("basic-auth", ignoreCase = true)) {
            if (!headers.containsKey("authorization")) {
                Log.w(TAG, "No credentials provided")
                return unauthorizedResponse
            }
            try {
                val credentials = String(Base64.decode(headers["authorization"]!!
                    .split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1], 0), charset("UTF-8"))
                val credentialParts = credentials.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (credentialParts.size != 2) {
                    Log.w(TAG, "Unable to split credentials: " + credentialParts.contentToString())
                    return internalError
                }
                if (credentialParts[0] == segments[2] && credentialParts[1] == segments[3]) {
                    Log.i(TAG, "Credentials accepted")
                    return oKResponse
                } else {
                    Log.w(TAG, String.format("Invalid credentials. Expected %s, %s, but was %s, %s",
                        segments[2], segments[3], credentialParts[0], credentialParts[1]))
                    return unauthorizedResponse
                }
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                return internalError
            }
        } else if (func.equals("gzip", ignoreCase = true)) {
            try {
                val size = param.toInt()
                if (size <= 0) {
                    Log.w(TAG, "Invalid size for gzipped data: $size")
                    throw NumberFormatException()
                }

                return getGzippedResponse(size)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return internalError
            } catch (e: IOException) {
                e.printStackTrace()
                return internalError
            }
        } else if (func.equals("files", ignoreCase = true)) {
            try {
                val id = param.toInt()
                if (id < 0) {
                    Log.w(TAG, "Invalid ID: $id")
                    throw NumberFormatException()
                }
                return getFileAccessResponse(id, headers)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return internalError
            }
        }

        return get404Error()
    }

    @Synchronized
    private fun getFileAccessResponse(id: Int, header: Map<String, String>): Response {
        val file = accessFile(id)
        if (file == null || !file.exists()) {
            Log.w(TAG, "File not found: $id")
            return get404Error()
        }
        var inputStream: InputStream? = null
        var contentRange: String? = null
        val status: Response.Status
        var successful = false
        try {
            inputStream = FileInputStream(file)
            if (header.containsKey("range")) {
                // read range header field
                val value = header["range"]
                val segments = value!!.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (segments.size != 2) {
                    Log.w(TAG, "Invalid segment length: " + segments.contentToString())
                    return internalError
                }
                val type = StringUtils.substringBefore(value, "=")
                if (!type.equals("bytes", ignoreCase = true)) {
                    Log.w(TAG, "Range is not specified in bytes: $value")
                    return internalError
                }
                try {
                    val start = StringUtils.substringBefore(segments[1], "-").toLong()
                    if (start >= file.length()) {
                        return rangeNotSatisfiable
                    }

                    // skip 'start' bytes
                    IOUtils.skipFully(inputStream, start)
                    contentRange = "bytes " + start + (file.length() - 1) + "/" + file.length()
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    return internalError
                } catch (e: IOException) {
                    e.printStackTrace()
                    return internalError
                }

                status = Response.Status.PARTIAL_CONTENT
            } else {
                // request did not contain range header field
                status = Response.Status.OK
            }
            successful = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()

            return internalError
        } finally {
            if (!successful && inputStream != null) {
                IOUtils.closeQuietly(inputStream)
            }
        }

        val response = Response(status, URLConnection.guessContentTypeFromName(file.absolutePath), inputStream)

        response.addHeader("Accept-Ranges", "bytes")
        if (contentRange != null) {
            response.addHeader("Content-Range", contentRange)
        }
        response.addHeader("Content-Length", file.length().toString())
        return response
    }

    @Throws(IOException::class)
    private fun getGzippedResponse(size: Int): Response {
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val buffer = ByteArray(size)
        val random = Random(System.currentTimeMillis())
        random.nextBytes(buffer)

        val compressed = ByteArrayOutputStream(buffer.size)
        val gzipOutputStream = GZIPOutputStream(compressed)
        gzipOutputStream.write(buffer)
        gzipOutputStream.close()

        val inputStream: InputStream = ByteArrayInputStream(compressed.toByteArray())
        val response = Response(Response.Status.OK, MIME_PLAIN, inputStream)
        response.addHeader("Content-Encoding", "gzip")
        response.addHeader("Content-Length", compressed.size().toString())
        return response
    }

    private fun getStatus(code: Int): IStatus {
        when (code) {
            200 -> return Response.Status.OK
            201 -> return Response.Status.CREATED
            206 -> return Response.Status.PARTIAL_CONTENT
            301 -> return Response.Status.REDIRECT
            304 -> return Response.Status.NOT_MODIFIED
            400 -> return Response.Status.BAD_REQUEST
            401 -> return Response.Status.UNAUTHORIZED
            403 -> return Response.Status.FORBIDDEN
            404 -> return Response.Status.NOT_FOUND
            405 -> return Response.Status.METHOD_NOT_ALLOWED
            416 -> return Response.Status.RANGE_NOT_SATISFIABLE
            500 -> return Response.Status.INTERNAL_ERROR
            else -> return object : IStatus {
                override fun getRequestStatus(): Int {
                    return code
                }

                override fun getDescription(): String {
                    return "Unknown"
                }
            }
        }
    }

    private fun getRedirectResponse(times: Int): Response {
        if (times > 0) {
            val response = Response(Response.Status.REDIRECT, MIME_HTML, "This resource has been moved permanently")
            response.addHeader("Location", "/redirect/$times")
            return response
        } else if (times == 0) {
            return oKResponse
        } else {
            return internalError
        }
    }

    private val unauthorizedResponse: Response
        get() {
            val response = Response(Response.Status.UNAUTHORIZED, MIME_HTML, "")
            response.addHeader("WWW-Authenticate", "Basic realm=\"Test Realm\"")
            return response
        }

    private val oKResponse: Response
        get() = Response(Response.Status.OK, MIME_HTML, "")

    private val internalError: Response
        get() = Response(Response.Status.INTERNAL_ERROR, MIME_HTML, "The server encountered an internal error")

    private val rangeNotSatisfiable: Response
        get() = Response(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAIN, "")

    private fun get404Error(): Response {
        return Response(Response.Status.NOT_FOUND, MIME_HTML, "The requested URL was not found on this server")
    }

    companion object {
        private const val TAG = "HTTPBin"

        private const val MIME_HTML = "text/html"
        private const val MIME_PLAIN = "text/plain"
    }
}
