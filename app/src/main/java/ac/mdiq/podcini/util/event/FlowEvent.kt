package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.download.DownloadStatus
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.storage.model.feed.VolumeAdaptionSetting
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.view.KeyEvent
import androidx.core.util.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import java.util.*
import kotlin.math.abs
import kotlin.math.max

sealed class FlowEvent {
    data class PlaybackPositionEvent(val position: Int, val duration: Int) : FlowEvent()

    data class PlaybackServiceEvent(val action: Action) : FlowEvent() {
        enum class Action {
            SERVICE_STARTED,
            SERVICE_SHUT_DOWN,
        }
    }

    data class BufferUpdateEvent(val progress: Float) : FlowEvent() {
        fun hasStarted(): Boolean {
            return progress == PROGRESS_STARTED
        }

        fun hasEnded(): Boolean {
            return progress == PROGRESS_ENDED
        }

        companion object {
            private const val PROGRESS_STARTED = -1f
            private const val PROGRESS_ENDED = -2f
            fun started(): BufferUpdateEvent {
                return BufferUpdateEvent(PROGRESS_STARTED)
            }

            fun ended(): BufferUpdateEvent {
                return BufferUpdateEvent(PROGRESS_ENDED)
            }

            fun progressUpdate(progress: Float): BufferUpdateEvent {
                return BufferUpdateEvent(progress)
            }
        }
    }

    data class HistoryEvent(val sortOrder: SortOrder = SortOrder.PLAYED_DATE_NEW_OLD, val startDate: Long = 0, val endDate: Long = Date().time) : FlowEvent()

    data class SleepTimerUpdatedEvent(private val timeLeft: Long) : FlowEvent() {
        fun getTimeLeft(): Long {
            return abs(timeLeft.toDouble()).toLong()
        }

        val isOver: Boolean
            get() = timeLeft == 0L

        fun wasJustEnabled(): Boolean {
            return timeLeft < 0
        }

        val isCancelled: Boolean
            get() = timeLeft == CANCELLED

        companion object {
            private const val CANCELLED = Long.MAX_VALUE
            fun justEnabled(timeLeft: Long): SleepTimerUpdatedEvent {
                return SleepTimerUpdatedEvent(-timeLeft)
            }

            fun updated(timeLeft: Long): SleepTimerUpdatedEvent {
                return SleepTimerUpdatedEvent(max(0.0, timeLeft.toDouble()).toLong())
            }

            fun cancelled(): SleepTimerUpdatedEvent {
                return SleepTimerUpdatedEvent(CANCELLED)
            }
        }
    }

    data class SpeedChangedEvent(val newSpeed: Float) : FlowEvent()

    data class StartPlayEvent(val item: FeedItem) : FlowEvent()

    data class SkipIntroEndingChangedEvent(val skipIntro: Int, val skipEnding: Int, val feedId: Long) : FlowEvent()

    data class SpeedPresetChangedEvent(val speed: Float, val feedId: Long) : FlowEvent()

    data class VolumeAdaptionChangedEvent(val volumeAdaptionSetting: VolumeAdaptionSetting, val feedId: Long) : FlowEvent()

    data class DiscoveryCompletedEvent(val dummy: Unit = Unit) : FlowEvent()

    data class DownloadLogEvent(val dummy: Unit = Unit) : FlowEvent()

    data class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) : FlowEvent() {
        val urls: Set<String>
            get() = map.keys
    }

    data class FavoritesEvent(val dummy: Unit = Unit) : FlowEvent()

    data class FeedItemEvent(val items: List<FeedItem>) : FlowEvent() {
        companion object {
            fun updated(items: List<FeedItem>): FeedItemEvent {
                return FeedItemEvent(items)
            }

            @JvmStatic
            fun updated(vararg items: FeedItem): FeedItemEvent {
                return FeedItemEvent(listOf(*items))
            }
        }
    }

    data class FeedListUpdateEvent(val feedIds: List<Long> = emptyList()) : FlowEvent() {
        constructor(feed: Feed) : this(listOf(feed.id))
        constructor(feedId: Long) : this(listOf(feedId))
        constructor(feeds: List<Feed>, junkInfo: String = "") : this(feeds.map { it.id })

        fun contains(feed: Feed): Boolean {
            return feedIds.contains(feed.id)
        }
    }

    data class FeedTagsChangedEvent(val dummy: Unit = Unit) : FlowEvent()

    data class FeedUpdateRunningEvent(val isFeedUpdateRunning: Boolean) : FlowEvent()

    data class MessageEvent @JvmOverloads constructor(val message: String, val action: Consumer<Context>? = null, val actionText: String? = null) : FlowEvent()

    data class PlayerErrorEvent(val message: String) : FlowEvent()

    data class PlayerStatusEvent(val dummy: Unit = Unit) : FlowEvent()

    data class QueueEvent(val action: Action, val item: FeedItem?, val items: List<FeedItem>, val position: Int) : FlowEvent() {

        enum class Action {
            ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED
        }

        companion object {
            @JvmStatic
            fun added(item: FeedItem, position: Int): QueueEvent {
                return QueueEvent(Action.ADDED, item, listOf(), position)
            }

            @JvmStatic
            fun setQueue(queue: List<FeedItem>): QueueEvent {
                return QueueEvent(Action.SET_QUEUE, null, queue, -1)
            }

            @JvmStatic
            fun removed(item: FeedItem): QueueEvent {
                return QueueEvent(Action.REMOVED, item, listOf(), -1)
            }

            @JvmStatic
            fun irreversibleRemoved(item: FeedItem): QueueEvent {
                return QueueEvent(Action.IRREVERSIBLE_REMOVED, item, listOf(), -1)
            }

            @JvmStatic
            fun cleared(): QueueEvent {
                return QueueEvent(Action.CLEARED, null, listOf(), -1)
            }

            @JvmStatic
            fun sorted(sortedQueue: List<FeedItem>): QueueEvent {
                return QueueEvent(Action.SORTED, null, sortedQueue, -1)
            }

            @JvmStatic
            fun moved(item: FeedItem, newPosition: Int): QueueEvent {
                return QueueEvent(Action.MOVED, item, listOf(), newPosition)
            }
        }
    }

    data class StatisticsEvent(val dummy: Unit = Unit) : FlowEvent()

    data class SwipeActionsChangedEvent(val dummy: Unit = Unit) : FlowEvent()

    data class SyncServiceEvent(val messageResId: Int, val message: String = "") : FlowEvent()

    data class UnreadItemsUpdateEvent(val dummy: Unit = Unit) : FlowEvent()

    data class DiscoveryDefaultUpdateEvent(val dummy: Unit = Unit) : FlowEvent()

    data class FeedEvent(private val action: Action, val feedId: Long) : FlowEvent() {
        enum class Action {
            FILTER_CHANGED,
            SORT_ORDER_CHANGED
        }

        override fun toString(): String {
            return ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("action", action)
                .append("feedId", feedId)
                .toString()
        }
    }

    data class AllEpisodesFilterChangedEvent(val filterValues: Set<String?>?) : FlowEvent()
}

object EventFlow {
    val collectorCount = MutableStateFlow(0)
    private val _events = MutableSharedFlow<FlowEvent>(replay = 0)
    val events: SharedFlow<FlowEvent> = _events
    private val _stickyEvents = MutableSharedFlow<FlowEvent>(replay = 1)
    val stickyEvents: SharedFlow<FlowEvent> = _stickyEvents
    private val _keyEvents = MutableSharedFlow<KeyEvent>(replay = 0)
    val keyEvents: SharedFlow<KeyEvent> = _keyEvents

    init {}

    fun postEvent(event: FlowEvent) = GlobalScope.launch(Dispatchers.Default) {
        val stat = _events.emit(event)
        Logd("EventFlow", "event posted: $event $stat")
    }

    fun postStickyEvent(event: FlowEvent) = GlobalScope.launch(Dispatchers.Default) {
        val stat = _stickyEvents.emit(event)
        Logd("EventFlow", "sticky event posted: $event $stat")
    }

    fun postEvent(event: KeyEvent) = GlobalScope.launch(Dispatchers.Default) {
        val stat = _keyEvents.emit(event)
        Logd("EventFlow", "key event posted: $event $stat")
    }
}