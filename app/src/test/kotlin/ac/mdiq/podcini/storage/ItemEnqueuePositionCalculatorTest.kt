package ac.mdiq.podcini.storage

import ac.mdiq.podcini.feed.FeedMother.anyFeed
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterfaceTestStub
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.EnqueueLocation
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.model.RemoteMedia
import ac.mdiq.podcini.util.CollectionTestUtil
import ac.mdiq.podcini.storage.utils.EpisodeUtil.getIdList
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import java.util.stream.Collectors

object ItemEnqueuePositionCalculatorTest {
    fun doAddToQueueAndAssertResult(message: String?,
                                    calculator: Queues.EnqueuePositionPolicy,
                                    itemToAdd: Episode,
                                    queue: MutableList<Episode>,
                                    currentlyPlaying: Playable?,
                                    idsExpected: List<Long?>?
    ) {
        val posActual = calculator.calcPosition(queue, currentlyPlaying)
        queue.add(posActual, itemToAdd)
        Assert.assertEquals(message, idsExpected, getIdList(queue))
    }

    val QUEUE_EMPTY: List<Episode> = Collections.unmodifiableList(emptyList())

    val QUEUE_DEFAULT: List<Episode> = Collections.unmodifiableList(listOf(
        createFeedItem(11), createFeedItem(12), createFeedItem(13), createFeedItem(14)))
    val QUEUE_DEFAULT_IDS: List<Long> = QUEUE_DEFAULT.stream().map(Episode::id).collect(Collectors.toList())

    fun getCurrentlyPlaying(idCurrentlyPlaying: Long): Playable? {
        if (ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA == idCurrentlyPlaying) {
            return externalMedia()
        }
        if (ID_CURRENTLY_PLAYING_NULL == idCurrentlyPlaying) {
            return null
        }
        return createFeedItem(idCurrentlyPlaying).media
    }

    fun externalMedia(): Playable {
        return RemoteMedia(createFeedItem(0))
    }

    const val ID_CURRENTLY_PLAYING_NULL: Long = -1L
    const val ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA: Long = -9999L


    fun createFeedItem(id: Long): Episode {
        val item = Episode(id, "Item$id", "ItemId$id", "url",
            Date(), Episode.PlayState.PLAYED.code, anyFeed())
        val media = EpisodeMedia(item, "http://download.url.net/$id", 1234567, "audio/mpeg")
        media.id = item.id
        item.setMedia(media)
        return item
    }
}

@RunWith(Parameterized::class)
open class BasicTest {
    @Parameterized.Parameter
    var message: String? = null

    @Parameterized.Parameter(1)
    var idsExpected: List<Long?>? = null

    @Parameterized.Parameter(2)
    var options: Queues.EnqueueLocation? = null

    @Parameterized.Parameter(3)
    var curQueue: List<Episode?>? = null

    /**
     * Add a FeedItem with ID [.TFI_ID] with the setup
     */
    @Test
    fun test() {
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceTestStub())
        val calculator = Queues.EnqueuePositionPolicy(options!!)

        // shallow copy to which the test will add items
        val queue: MutableList<Episode> = ArrayList(curQueue)
        val tFI = ItemEnqueuePositionCalculatorTest.createFeedItem(TFI_ID)
        ItemEnqueuePositionCalculatorTest.doAddToQueueAndAssertResult(message,
            calculator, tFI, queue, currentlyPlaying,
            idsExpected)
    }

    open val currentlyPlaying: Playable?
        get() = null

    companion object {
        @Parameterized.Parameters(name = "{index}: case<{0}>, expected:{1}")
        fun data(): Iterable<Array<Any>> {
            return listOf(arrayOf("case default, i.e., add to the end",
                CollectionTestUtil.concat(ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT_IDS, TFI_ID),
                EnqueueLocation.BACK, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT), arrayOf("case option enqueue at front",
                    CollectionTestUtil.concat(TFI_ID, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT_IDS),
                    EnqueueLocation.FRONT, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT), arrayOf("case empty queue, option default",
                        CollectionTestUtil.list(TFI_ID),
                        EnqueueLocation.BACK, ItemEnqueuePositionCalculatorTest.QUEUE_EMPTY), arrayOf("case empty queue, option enqueue at front",
                            CollectionTestUtil.list(TFI_ID),
                            EnqueueLocation.FRONT, ItemEnqueuePositionCalculatorTest.QUEUE_EMPTY))
        }

        const val TFI_ID: Long = 101
    }
}

@RunWith(Parameterized::class)
class AfterCurrentlyPlayingTest : BasicTest() {
    @Parameterized.Parameter(4)
    var idCurrentlyPlaying: Long = 0

    override val currentlyPlaying: Playable?
        get() = ItemEnqueuePositionCalculatorTest.getCurrentlyPlaying(idCurrentlyPlaying)

    companion object {
        @Parameterized.Parameters(name = "{index}: case<{0}>, expected:{1}")
        fun data(): Iterable<Array<Any>> {
            return listOf(arrayOf("case option after currently playing",
                CollectionTestUtil.list(11L, TFI_ID, 12L, 13L, 14L),
                EnqueueLocation.AFTER_CURRENTLY_PLAYING, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT, 11L), arrayOf("case option after currently playing, currently playing in the middle of the queue",
                CollectionTestUtil.list(11L, 12L, 13L, TFI_ID, 14L),
                EnqueueLocation.AFTER_CURRENTLY_PLAYING, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT, 13L), arrayOf("case option after currently playing, currently playing is not in queue",
                CollectionTestUtil.concat(TFI_ID, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT_IDS),
                EnqueueLocation.AFTER_CURRENTLY_PLAYING, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT, 99L), arrayOf("case option after currently playing, no currentlyPlaying is null",
                CollectionTestUtil.concat(TFI_ID, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT_IDS),
                EnqueueLocation.AFTER_CURRENTLY_PLAYING,
                ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT, ID_CURRENTLY_PLAYING_NULL), arrayOf("case option after currently playing, currentlyPlaying is not a feedMedia",
                CollectionTestUtil.concat(TFI_ID, ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT_IDS),
                EnqueueLocation.AFTER_CURRENTLY_PLAYING,
                ItemEnqueuePositionCalculatorTest.QUEUE_DEFAULT, ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA), arrayOf("case empty queue, option after currently playing",
                CollectionTestUtil.list(TFI_ID),
                EnqueueLocation.AFTER_CURRENTLY_PLAYING,
                ItemEnqueuePositionCalculatorTest.QUEUE_EMPTY, ID_CURRENTLY_PLAYING_NULL))
        }

        private const val ID_CURRENTLY_PLAYING_NULL = -1L
        private const val ID_CURRENTLY_PLAYING_NOT_FEEDMEDIA = -9999L
    }
}
