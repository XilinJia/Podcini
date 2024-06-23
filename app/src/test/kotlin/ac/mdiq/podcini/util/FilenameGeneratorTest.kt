package ac.mdiq.podcini.util

import ac.mdiq.podcini.storage.utils.FileNameGenerator
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.storage.utils.FileNameGenerator.generateFileName
import org.apache.commons.lang3.StringUtils
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FilenameGeneratorTest {
    @Test
    @Throws(Exception::class)
    fun testGenerateFileName() {
        val result = generateFileName("abc abc")
        Assert.assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    @Throws(Exception::class)
    fun testGenerateFileName1() {
        val result = generateFileName("ab/c: <abc")
        Assert.assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    @Throws(Exception::class)
    fun testGenerateFileName2() {
        val result = generateFileName("abc abc ")
        Assert.assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testFeedTitleContainsApostrophe() {
        val result = generateFileName("Feed's Title ...")
        Assert.assertEquals("Feeds Title", result)
    }

    @Test
    fun testFeedTitleContainsDash() {
        val result = generateFileName("Left - Right")
        Assert.assertEquals("Left - Right", result)
    }

    @Test
    fun testFeedTitleContainsAccents() {
        val result = generateFileName("Äàáâãå")
        Assert.assertEquals("Aaaaaa", result)
    }

    @Test
    fun testInvalidInput() {
        val result = generateFileName("???")
        Assert.assertFalse(result.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun testLongFilename() {
        val longName = StringUtils.repeat("x", 20 + FileNameGenerator.MAX_FILENAME_LENGTH)
        val result = generateFileName(longName)
        Assert.assertTrue(result.length <= FileNameGenerator.MAX_FILENAME_LENGTH)
        createFiles(result)
    }

    @Test
    fun testLongFilenameNotEquals() {
        // Verify that the name is not just trimmed and different suffixes end up with the same name
        val longName = StringUtils.repeat("x", 20 + FileNameGenerator.MAX_FILENAME_LENGTH)
        val result1 = generateFileName(longName + "a")
        val result2 = generateFileName(longName + "b")
        Assert.assertNotEquals(result1, result2)
    }

    /**
     * Tests if files can be created.
     */
    @Throws(Exception::class)
    private fun createFiles(name: String) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
        val testFile = File(cache, name)
        Assert.assertTrue(testFile.mkdir())
        Assert.assertTrue(testFile.exists())
        Assert.assertTrue(testFile.delete())
        Assert.assertTrue(testFile.createNewFile())
    }
}
