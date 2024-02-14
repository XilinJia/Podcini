package de.danoeh.antennapod.net.sync

import org.junit.Assert
import org.junit.Test

class HostnameParserTest {
    @Test
    fun testHostOnly() {
        assertHostname(HostnameParser("example.com"), "https", 443, "example.com", "")
        assertHostname(HostnameParser("www.example.com"), "https", 443, "www.example.com", "")
    }

    @Test
    fun testHostAndPort() {
        assertHostname(HostnameParser("example.com:443"), "https", 443, "example.com", "")
        assertHostname(HostnameParser("example.com:80"), "http", 80, "example.com", "")
        assertHostname(HostnameParser("example.com:123"), "https", 123, "example.com", "")
    }

    @Test
    fun testScheme() {
        assertHostname(HostnameParser("https://example.com"), "https", 443, "example.com", "")
        assertHostname(HostnameParser("https://example.com:80"), "https", 80, "example.com", "")
        assertHostname(HostnameParser("http://example.com"), "http", 80, "example.com", "")
        assertHostname(HostnameParser("http://example.com:443"), "http", 443, "example.com", "")
    }

    @Test
    fun testSubfolder() {
        assertHostname(HostnameParser("https://example.com/"), "https", 443, "example.com", "")
        assertHostname(HostnameParser("https://example.com/a"), "https", 443, "example.com", "/a")
        assertHostname(HostnameParser("https://example.com/a/"), "https", 443, "example.com", "/a")
        assertHostname(HostnameParser("https://example.com:42/a"), "https", 42, "example.com", "/a")
    }

    private fun assertHostname(parser: HostnameParser, scheme: String, port: Int, host: String, subfolder: String) {
        Assert.assertEquals(scheme, parser.scheme)
        Assert.assertEquals(port.toLong(), parser.port.toLong())
        Assert.assertEquals(host, parser.host)
        Assert.assertEquals(subfolder, parser.subfolder)
    }
}
