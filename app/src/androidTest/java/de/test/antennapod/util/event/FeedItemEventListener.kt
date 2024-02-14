package de.test.antennapod.util.event

import de.danoeh.antennapod.event.FeedItemEvent
import io.reactivex.functions.Consumer
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Test helpers to listen [FeedItemEvent] and handle them accordingly
 *
 */
class FeedItemEventListener {
    private val events: MutableList<FeedItemEvent> = ArrayList()

    @Subscribe
    fun onEvent(event: FeedItemEvent) {
        events.add(event)
    }

    fun getEvents(): List<FeedItemEvent> {
        return events
    }

    companion object {
        /**
         * Provides an listener subscribing to [FeedItemEvent] that the callers can use
         *
         * Note: it uses RxJava's version of [Consumer] because it allows exceptions to be thrown.
         */
        @Throws(Exception::class)
        fun withFeedItemEventListener(consumer: Consumer<FeedItemEventListener?>) {
            val feedItemEventListener = FeedItemEventListener()
            try {
                EventBus.getDefault().register(feedItemEventListener)
                consumer.accept(feedItemEventListener)
            } finally {
                EventBus.getDefault().unregister(feedItemEventListener)
            }
        }
    }
}
