package de.danoeh.antennapod.core.util

import de.danoeh.antennapod.core.util.URIUtil.getURIFromRequestUrl
import org.junit.Assert
import org.junit.Test

/**
 * Test class for URIUtil
 */
class URIUtilTest {
    @Test
    fun testGetURIFromRequestUrlShouldNotEncode() {
        val testUrl = "http://example.com/this%20is%20encoded"
        Assert.assertEquals(testUrl, getURIFromRequestUrl(testUrl).toString())
    }

    @Test
    fun testGetURIFromRequestUrlShouldEncode() {
        val testUrl = "http://example.com/this is not encoded"
        val expected = "http://example.com/this%20is%20not%20encoded"
        Assert.assertEquals(expected, getURIFromRequestUrl(testUrl).toString())
    }
}
