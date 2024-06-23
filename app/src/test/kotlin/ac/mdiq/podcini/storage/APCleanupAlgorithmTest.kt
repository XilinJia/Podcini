package ac.mdiq.podcini.storage

import ac.mdiq.podcini.storage.algorithms.EpisodeCleanupAlgorithmFactory.APCleanupAlgorithm
import org.junit.Assert
import org.junit.Test
import java.text.SimpleDateFormat

class APCleanupAlgorithmTest {
    @Test
    @Throws(Exception::class)
    fun testCalcMostRecentDateForDeletion() {
        val algo = APCleanupAlgorithm(24)
        val curDateForTest = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2018-11-13T14:08:56-0800")
        val resExpected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2018-11-12T14:08:56-0800")
        val resActual = algo.calcMostRecentDateForDeletion(curDateForTest)
        Assert.assertEquals("cutoff for retaining most recent 1 day", resExpected, resActual)
    }
}
