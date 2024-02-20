package de.test.podcini.service.download

import android.util.Log
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.core.service.download.Downloader
import ac.mdiq.podcini.core.service.download.HttpDownloader
import ac.mdiq.podcini.model.download.DownloadError
import ac.mdiq.podcini.model.feed.FeedFile
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.storage.preferences.UserPreferences.init
import de.test.podcini.util.service.download.HTTPBin
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@LargeTest
class HttpDownloaderTest {
    private var url404: String? = null
    private var urlAuth: String? = null
    private var destDir: File? = null
    private var httpServer: HTTPBin? = null

    @After
    @Throws(Exception::class)
    fun tearDown() {
        val contents = destDir!!.listFiles()
        for (f in contents) {
            Assert.assertTrue(f.delete())
        }

        httpServer!!.stop()
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        init(InstrumentationRegistry.getInstrumentation().targetContext)
        destDir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(DOWNLOAD_DIR)
        Assert.assertNotNull(destDir)
        Assert.assertTrue(destDir!!.exists())
        httpServer = HTTPBin()
        httpServer!!.start()
        url404 = httpServer!!.baseUrl + "/status/404"
        urlAuth = httpServer!!.baseUrl + "/basic-auth/user/passwd"
    }

    private fun setupFeedFile(downloadUrl: String?, title: String, deleteExisting: Boolean): FeedFileImpl {
        val feedfile = FeedFileImpl(downloadUrl)
        val fileUrl = File(destDir, title).absolutePath
        val file = File(fileUrl)
        if (deleteExisting) {
            Log.d(TAG, "Deleting file: " + file.delete())
        }
        feedfile.setFile_url(fileUrl)
        return feedfile
    }

    private fun download(url: String?, title: String, expectedResult: Boolean, deleteExisting: Boolean = true,
                         username: String? = null, password: String? = null
    ): Downloader {
        val feedFile: FeedFile = setupFeedFile(url, title, deleteExisting)
        val request = DownloadRequest(
            feedFile.getFile_url()!!, url!!, title, 0, feedFile.getTypeAsInt(),
            username, password, null, false)
        val downloader: Downloader = HttpDownloader(request)
        downloader.call()
        val status = downloader.result
        Assert.assertNotNull(status)
        Assert.assertEquals(expectedResult, status.isSuccessful)
        // the file should not exist if the download has failed and deleteExisting was true
        Assert.assertTrue(!deleteExisting || File(feedFile.getFile_url()).exists() == expectedResult)
        return downloader
    }

    @Test
    fun testPassingHttp() {
        download(httpServer!!.baseUrl + "/status/200", "test200", true)
    }

    @Test
    fun testRedirect() {
        download(httpServer!!.baseUrl + "/redirect/4", "testRedirect", true)
    }

    @Test
    fun testGzip() {
        download(httpServer!!.baseUrl + "/gzip/100", "testGzip", true)
    }

    @Test
    fun test404() {
        download(url404, "test404", false)
    }

    @Test
    fun testCancel() {
        val url = httpServer!!.baseUrl + "/delay/3"
        val feedFile = setupFeedFile(url, "delay", true)
        val downloader: Downloader = HttpDownloader(DownloadRequest(
            feedFile.getFile_url()!!, url, "delay", 0,
            feedFile.getTypeAsInt(), null, null, null, false))
        val t: Thread = object : Thread() {
            override fun run() {
                downloader.call()
            }
        }
        t.start()
        downloader.cancel()
        try {
            t.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val result = downloader.result
        Assert.assertFalse(result.isSuccessful)
    }

    @Test
    fun testDeleteOnFailShouldDelete() {
        val downloader = download(url404, "testDeleteOnFailShouldDelete", false, true, null, null)
        Assert.assertFalse(File(downloader.downloadRequest.destination).exists())
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteOnFailShouldNotDelete() {
        val filename = "testDeleteOnFailShouldDelete"
        val dest = File(destDir, filename)
        dest.delete()
        Assert.assertTrue(dest.createNewFile())
        val downloader = download(url404, filename, false, false, null, null)
        Assert.assertTrue(File(downloader.downloadRequest.destination).exists())
    }

    @Test
    @Throws(InterruptedException::class)
    fun testAuthenticationShouldSucceed() {
        download(urlAuth, "testAuthSuccess", true, true, "user", "passwd")
    }

    @Test
    fun testAuthenticationShouldFail() {
        val downloader = download(urlAuth, "testAuthSuccess", false, true, "user", "Wrong passwd")
        Assert.assertEquals(DownloadError.ERROR_UNAUTHORIZED, downloader.result.reason)
    }

    /* TODO: replace with smaller test file
    public void testUrlWithSpaces() {
        download("http://acedl.noxsolutions.com/ace/Don't Call Salman Rushdie Sneezy in Finland.mp3", "testUrlWithSpaces", true);
    }
    */
    private class FeedFileImpl(download_url: String?) : FeedFile(null, download_url, false) {
        override fun getHumanReadableIdentifier(): String? {
            return download_url
        }

        override fun getTypeAsInt(): Int {
            return 0
        }
    }

    companion object {
        private const val TAG = "HttpDownloaderTest"
        private const val DOWNLOAD_DIR = "testdownloads"
    }
}
