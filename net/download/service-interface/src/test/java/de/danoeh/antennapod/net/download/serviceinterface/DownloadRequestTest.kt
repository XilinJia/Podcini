package de.danoeh.antennapod.net.download.serviceinterface

import android.os.Bundle
import android.os.Parcel
import de.danoeh.antennapod.model.feed.FeedMedia
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadRequestTest {
    @Test
    fun parcelInArrayListTest_WithAuth() {
        doTestParcelInArrayList("case has authentication",
            "usr1", "pass1", "usr2", "pass2")
    }

    @Test
    fun parcelInArrayListTest_NoAuth() {
        doTestParcelInArrayList("case no authentication",
            null, null, null, null)
    }

    @Test
    fun parcelInArrayListTest_MixAuth() {
        doTestParcelInArrayList("case mixed authentication",
            null, null, "usr2", "pass2")
    }

    @Test
    fun downloadRequestTestEquals() {
        val destStr = "file://location/media.mp3"
        val username = "testUser"
        val password = "testPassword"
        val item = createFeedItem(1)
        val request1 = DownloadRequest.Builder(destStr, item)
            .withAuthentication(username, password)
            .build()

        val request2 = DownloadRequest.Builder(destStr, item)
            .withAuthentication(username, password)
            .build()

        val request3 = DownloadRequest.Builder(destStr, item)
            .withAuthentication("diffUsername", "diffPassword")
            .build()

        Assert.assertEquals(request1, request2)
        Assert.assertNotEquals(request1, request3)
    }

    // Test to ensure parcel using put/getParcelableArrayList() API work
    // based on: https://stackoverflow.com/a/13507191
    private fun doTestParcelInArrayList(message: String,
                                        username1: String?, password1: String?,
                                        username2: String?, password2: String?
    ) {
        var toParcel: ArrayList<DownloadRequest>
        run {
            // test DownloadRequests to parcel
            val destStr = "file://location/media.mp3"
            val item1 = createFeedItem(1)
            val request1 = DownloadRequest.Builder(destStr, item1)
                .withAuthentication(username1, password1)
                .build()

            val item2 = createFeedItem(2)
            val request2 = DownloadRequest.Builder(destStr, item2)
                .withAuthentication(username2, password2)
                .build()

            toParcel = ArrayList()
            toParcel.add(request1)
            toParcel.add(request2)
        }

        // parcel the download requests
        val bundleIn = Bundle()
        bundleIn.putParcelableArrayList("r", toParcel)

        val parcel = Parcel.obtain()
        bundleIn.writeToParcel(parcel, 0)

        val bundleOut = Bundle()
        bundleOut.classLoader = DownloadRequest::class.java.classLoader
        parcel.setDataPosition(0) // to read the parcel from the beginning.
        bundleOut.readFromParcel(parcel)

        val fromParcel = bundleOut.getParcelableArrayList<DownloadRequest>("r")

        // spot-check contents to ensure they are the same
        // DownloadRequest.equals() implementation doesn't quite work
        // for DownloadRequest.argument (a Bundle)
        Assert.assertEquals( "$message - size", toParcel.size.toLong(), fromParcel!!.size.toLong())
        Assert.assertEquals("$message - source", toParcel[1].source, fromParcel[1].source)
        Assert.assertEquals("$message - password", toParcel[0].password, fromParcel[0].password)
        Assert.assertEquals("$message - argument", toString(toParcel[0].arguments), toString(fromParcel[0].arguments))
    }

    private fun createFeedItem(id: Int): FeedMedia {
        // Use mockito would be less verbose, but it'll take extra 1 second for this tiny test
        return FeedMedia(id.toLong(), null, 0, 0, 0, "", "", "http://example.com/episode$id", false, null, 0, 0)
    }

    companion object {
        private fun toString(b: Bundle?): String {
            val sb = StringBuilder()
            sb.append("{")
            for (key in b!!.keySet()) {
                val `val` = b[key]
                sb.append("(").append(key).append(":").append(`val`).append(") ")
            }
            sb.append("}")
            return sb.toString()
        }
    }
}
