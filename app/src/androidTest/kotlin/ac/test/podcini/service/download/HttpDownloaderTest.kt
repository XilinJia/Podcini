package de.test.podcini.service.download

import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.Downloader
import ac.mdiq.podcini.net.download.service.HttpDownloader
import ac.mdiq.podcini.net.download.service.DownloadRequest
import ac.mdiq.podcini.preferences.UserPreferences.init
import ac.mdiq.podcini.util.Logd
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.test.podcini.util.service.download.HTTPBin
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.Serializable

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
        if (contents != null) {
            for (f in contents) {
                Assert.assertTrue(f.delete())
            }
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
            Logd(TAG, "Deleting file: " + file.delete())
        }
        feedfile.setFile_url(fileUrl)
        return feedfile
    }

    private fun download(url: String?, title: String, expectedResult: Boolean, deleteExisting: Boolean = true,
                         username: String? = null, password: String? = null): Downloader {

        val feedFile: FeedFile = setupFeedFile(url, title, deleteExisting)
        val request = DownloadRequest(feedFile.getFile_url()!!, url!!, title, 0, feedFile.getTypeAsInt(),
            username, password, null, false)
        val downloader: Downloader = HttpDownloader(request)
        downloader.call()
        val status = downloader.result
        Assert.assertNotNull(status)
        Assert.assertEquals(expectedResult, status.isSuccessful)
        // the file should not exist if the download has failed and deleteExisting was true
        Assert.assertTrue(!deleteExisting || File(feedFile.getFile_url()!!).exists() == expectedResult)
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
        val downloader: Downloader = HttpDownloader(DownloadRequest(feedFile.getFile_url()!!, url, "delay", 0,
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
        Assert.assertFalse(File(downloader.downloadRequest.destination!!).exists())
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteOnFailShouldNotDelete() {
        val filename = "testDeleteOnFailShouldDelete"
        val dest = File(destDir, filename)
        dest.delete()
        Assert.assertTrue(dest.createNewFile())
        val downloader = download(url404, filename, false, false, null, null)
        Assert.assertTrue(File(downloader.downloadRequest.destination!!).exists())
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
        private val TAG: String = HttpDownloaderTest::class.simpleName ?: "Anonymous"
        private const val DOWNLOAD_DIR = "testdownloads"
    }
}

abstract class FeedFile(@JvmField var file_url: String? = null,
                        @JvmField var download_url: String? = null,
                        private var downloaded: Boolean = false) : FeedComponent(), Serializable {

    /**
     * Creates a new FeedFile object.
     *
     * @param file_url     The location of the FeedFile. If this is null, the downloaded-attribute
     * will automatically be set to false.
     * @param download_url The location where the FeedFile can be downloaded.
     * @param downloaded   true if the FeedFile has been downloaded, false otherwise. This parameter
     * will automatically be interpreted as false if the file_url is null.
     */
    init {
//        Log.d("FeedFile", "$file_url $download_url $downloaded")
        this.downloaded = (file_url != null) && downloaded
    }

    abstract fun getTypeAsInt(): Int

    /**
     * Update this FeedFile's attributes with the attributes from another
     * FeedFile. This method should only update attributes which where read from
     * the feed.
     */
    fun updateFromOther(other: FeedFile) {
        super.updateFromOther(other)
        this.download_url = other.download_url
    }

    /**
     * Compare's this FeedFile's attribute values with another FeedFile's
     * attribute values. This method will only compare attributes which were
     * read from the feed.
     *
     * @return true if attribute values are different, false otherwise
     */
    fun compareWithOther(other: FeedFile): Boolean {
        if (super.compareWithOther(other)) return true
        if (download_url != other.download_url) return true

        return false
    }

    /**
     * Returns true if the file exists at file_url.
     */
    fun fileExists(): Boolean {
        if (file_url == null) return false
        else {
            val f = File(file_url!!)
            return f.exists()
        }
    }

    fun getFile_url(): String? {
        return file_url
    }

    /**
     * Changes the file_url of this FeedFile. Setting this value to
     * null will also set the downloaded-attribute to false.
     */
    open fun setFile_url(file_url: String?) {
        this.file_url = file_url
        if (file_url == null) downloaded = false
    }

    fun isDownloaded(): Boolean {
        return downloaded
    }

    open fun setDownloaded(downloaded: Boolean) {
        this.downloaded = downloaded
    }
}

/**
 * Represents every possible component of a feed
 *
 * @author daniel
 */
// only used in test
abstract class FeedComponent internal constructor() {
    open var id: Long = 0

    /**
     * Update this FeedComponent's attributes with the attributes from another
     * FeedComponent. This method should only update attributes which where read from
     * the feed.
     */
    fun updateFromOther(other: FeedComponent?) {}

    /**
     * Compare's this FeedComponent's attribute values with another FeedComponent's
     * attribute values. This method will only compare attributes which were
     * read from the feed.
     *
     * @return true if attribute values are different, false otherwise
     */
    fun compareWithOther(other: FeedComponent?): Boolean {
        return false
    }

    /**
     * Should return a non-null, human-readable String so that the item can be
     * identified by the user. Can be title, download-url, etc.
     */
    abstract fun getHumanReadableIdentifier(): String?

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is FeedComponent) return false

        return id == o.id
    }

    override fun hashCode(): Int {
        return (id xor (id ushr 32)).toInt()
    }
}