package ac.mdiq.podcini.feed

import ac.mdiq.podcini.net.feed.LocalFeedUpdater
import ac.mdiq.podcini.net.feed.LocalFeedUpdater.getImageUrl
import ac.mdiq.podcini.net.feed.LocalFeedUpdater.tryUpdateFeed
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterfaceTestStub
import ac.mdiq.podcini.net.feed.LocalFeedUpdater.FastDocumentFile.Companion.list
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.config.ApplicationCallbacks
import ac.mdiq.podcini.util.config.ClientConfig
import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase.assertEquals
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Test local feeds handling in class LocalFeedUpdater.
 */
@RunWith(RobolectricTestRunner::class)
class LocalFeedUpdaterTest {
    private lateinit var context: Context

    @Before
    @Throws(Exception::class)
    fun setUp() {
        // Initialize environment
        context = InstrumentationRegistry.getInstrumentation().context
        UserPreferences.init(context!!)
//        init(context)

        val app = context as Application?
        ClientConfig.applicationCallbacks = Mockito.mock(ApplicationCallbacks::class.java)
        Mockito.`when`(ClientConfig.applicationCallbacks?.getApplicationInstance()).thenReturn(app)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceTestStub())

        // Initialize database
//        PodDBAdapter.init(context!!)
//        deleteDatabase()
//        val adapter = getInstance()
//        adapter.open()
//        adapter.close()

        mapDummyMetadata(LOCAL_FEED_DIR1)
        mapDummyMetadata(LOCAL_FEED_DIR2)
//        TODO: can't get 'addExtensionMimeTypMapping?
//        Shadows.shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("mp3", "audio/mp3")
    }

    @After
    fun tearDown() {
//        DBWriter.tearDownTests()
//        PodDBAdapter.tearDownTests()
    }

    /**
     * Test adding a new local feed.
     */
    @Test
    fun testUpdateFeed_AddNewFeed() {
        // check for empty database
        val feedListBefore = getFeedList()
        assertThat(feedListBefore, CoreMatchers.`is`(Matchers.empty()))

        callUpdateFeed(LOCAL_FEED_DIR2)

        // verify new feed in database
        verifySingleFeedInDatabaseAndItemCount(2)
        val feedAfter = verifySingleFeedInDatabase()
        assertEquals(FEED_URL, feedAfter.downloadUrl)
    }

    /**
     * Test adding further items to an existing local feed.
     */
    @Test
    fun testUpdateFeed_AddMoreItems() {
        // add local feed with 1 item (localFeedDir1)
        callUpdateFeed(LOCAL_FEED_DIR1)

        // now add another item (by changing to local feed folder localFeedDir2)
        callUpdateFeed(LOCAL_FEED_DIR2)

        verifySingleFeedInDatabaseAndItemCount(2)
    }

    /**
     * Test removing items from an existing local feed without a corresponding media file.
     */
    @Test
    fun testUpdateFeed_RemoveItems() {
        // add local feed with 2 items (localFeedDir1)
        callUpdateFeed(LOCAL_FEED_DIR2)

        // now remove an item (by changing to local feed folder localFeedDir1)
        callUpdateFeed(LOCAL_FEED_DIR1)

        verifySingleFeedInDatabaseAndItemCount(1)
    }

    /**
     * Test feed icon defined in the local feed media folder.
     */
    @Test
    fun testUpdateFeed_FeedIconFromFolder() {
        callUpdateFeed(LOCAL_FEED_DIR2)

        val feedAfter = verifySingleFeedInDatabase()
        assertThat(feedAfter.imageUrl, CoreMatchers.endsWith("local-feed2/folder.png"))
    }

    /**
     * Test default feed icon if there is no matching file in the local feed media folder.
     */
    @Test
    fun testUpdateFeed_FeedIconDefault() {
        callUpdateFeed(LOCAL_FEED_DIR1)

        val feedAfter = verifySingleFeedInDatabase()
        assertThat(feedAfter.imageUrl, Matchers.startsWith(Feed.PREFIX_GENERATIVE_COVER))
    }

    /**
     * Test default feed metadata.
     *
     * @see .mapDummyMetadata Title and PubDate are dummy values.
     */
    @Test
    fun testUpdateFeed_FeedMetadata() {
        callUpdateFeed(LOCAL_FEED_DIR1)

        val feed = verifySingleFeedInDatabase()
        val feedItems = feed.episodes
        assertEquals("track1.mp3", feedItems[0].title)
    }

    @Test
    fun testGetImageUrl_EmptyFolder() {
        val imageUrl = getImageUrl(emptyList(), Uri.EMPTY)
        assertThat(imageUrl, Matchers.startsWith(Feed.PREFIX_GENERATIVE_COVER))
    }

    @Test
    fun testGetImageUrl_NoImageButAudioFiles() {
        val folder = listOf(mockDocumentFile("audio.mp3", "audio/mp3"))
        val imageUrl = getImageUrl(folder, Uri.EMPTY)
        assertThat(imageUrl, Matchers.startsWith(Feed.PREFIX_GENERATIVE_COVER))
    }

    @Test
    fun testGetImageUrl_PreferredImagesFilenames() {
        for (filename in LocalFeedUpdater.PREFERRED_FEED_IMAGE_FILENAMES) {
            val folder = listOf(mockDocumentFile("audio.mp3", "audio/mp3"),
                mockDocumentFile(filename, "image/jpeg")) // image MIME type doesn't matter
            val imageUrl = getImageUrl(folder, Uri.EMPTY)
            assertThat(imageUrl, CoreMatchers.endsWith(filename))
        }
    }

    @Test
    fun testGetImageUrl_OtherImageFilenameJpg() {
        val folder = listOf(mockDocumentFile("audio.mp3", "audio/mp3"),
            mockDocumentFile("my-image.jpg", "image/jpeg"))
        val imageUrl = getImageUrl(folder, Uri.EMPTY)
        assertThat(imageUrl, CoreMatchers.endsWith("my-image.jpg"))
    }

    @Test
    fun testGetImageUrl_OtherImageFilenameJpeg() {
        val folder = listOf(mockDocumentFile("audio.mp3", "audio/mp3"),
            mockDocumentFile("my-image.jpeg", "image/jpeg"))
        val imageUrl = getImageUrl(folder, Uri.EMPTY)
        assertThat(imageUrl, CoreMatchers.endsWith("my-image.jpeg"))
    }

    @Test
    fun testGetImageUrl_OtherImageFilenamePng() {
        val folder = listOf(mockDocumentFile("audio.mp3", "audio/mp3"),
            mockDocumentFile("my-image.png", "image/png"))
        val imageUrl = getImageUrl(folder, Uri.EMPTY)
        assertThat(imageUrl, CoreMatchers.endsWith("my-image.png"))
    }

    @Test
    fun testGetImageUrl_OtherImageFilenameUnsupportedMimeType() {
        val folder = listOf(mockDocumentFile("audio.mp3", "audio/mp3"),
            mockDocumentFile("my-image.svg", "image/svg+xml"))
        val imageUrl = getImageUrl(folder, Uri.EMPTY)
        assertThat(imageUrl, Matchers.startsWith(Feed.PREFIX_GENERATIVE_COVER))
    }

    /**
     * Fill ShadowMediaMetadataRetriever with dummy duration and title.
     *
     * @param localFeedDir assets local feed folder with media files
     */
    private fun mapDummyMetadata(localFeedDir: String) {
        for (fileName in Objects.requireNonNull(File(localFeedDir).list())) {
            val path = "$localFeedDir/$fileName"
            ShadowMediaMetadataRetriever.addMetadata(path,
                MediaMetadataRetriever.METADATA_KEY_DURATION, "10")
            ShadowMediaMetadataRetriever.addMetadata(path,
                MediaMetadataRetriever.METADATA_KEY_TITLE, fileName)
            ShadowMediaMetadataRetriever.addMetadata(path,
                MediaMetadataRetriever.METADATA_KEY_DATE, "20200601T222324")
        }
    }

    /**
     * Calls the method LocalFeedUpdater#tryUpdateFeed with the given local feed folder.
     *
     * @param localFeedDir assets local feed folder with media files
     */
    private fun callUpdateFeed(localFeedDir: String) {
        Mockito.mockStatic(LocalFeedUpdater.FastDocumentFile::class.java).use { dfMock ->
            // mock external storage
            dfMock.`when`<Any> { list(ArgumentMatchers.any(), ArgumentMatchers.any()) }
                .thenReturn(mockLocalFolder(localFeedDir))

            // call method to test
            val feed = Feed(FEED_URL, null)
            try {
                tryUpdateFeed(feed, context!!, null, null)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    companion object {
        /**
         * URL to locate the local feed media files on the external storage (SD card).
         * The exact URL doesn't matter here as access to external storage is mocked
         * (seems not to be supported by Robolectric).
         */
        private const val FEED_URL =
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2Flocal-feed"
        private const val LOCAL_FEED_DIR1 = "src/test/assets/local-feed1"
        private const val LOCAL_FEED_DIR2 = "src/test/assets/local-feed2"

        /**
         * Verify that the database contains exactly one feed and return that feed.
         */
        private fun verifySingleFeedInDatabase(): Feed {
            val feedListAfter = getFeedList()
            Assert.assertEquals(1, feedListAfter.size.toLong())
            return feedListAfter[0]
        }

        /**
         * Verify that the database contains exactly one feed and the number of
         * items in the feed.
         *
         * @param expectedItemCount expected number of items in the feed
         */
        private fun verifySingleFeedInDatabaseAndItemCount(expectedItemCount: Int) {
            val feed = verifySingleFeedInDatabase()
            val feedItems = feed.episodes
            Assert.assertEquals(expectedItemCount.toLong(), feedItems.size.toLong())
        }

        /**
         * Create a DocumentFile mock object.
         */
        private fun mockDocumentFile(fileName: String, mimeType: String): LocalFeedUpdater.FastDocumentFile {
            return LocalFeedUpdater.FastDocumentFile(fileName, mimeType, Uri.parse("file:///path/$fileName"), 0, 0)
        }

        private fun mockLocalFolder(folderName: String): List<LocalFeedUpdater.FastDocumentFile> {
            val files: MutableList<LocalFeedUpdater.FastDocumentFile> = ArrayList()
            for (f in Objects.requireNonNull<Array<File>>(File(folderName).listFiles())) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(f.path)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                files.add(LocalFeedUpdater.FastDocumentFile(f.name, mimeType!!,
                    Uri.parse(f.toURI().toString()), f.length(), f.lastModified()))
            }
            return files
        }
    }
}
