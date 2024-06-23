package ac.mdiq.podcini.sync

import ac.mdiq.podcini.net.sync.SyncService.Companion.getRemoteActionsOverridingLocalActions
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import junit.framework.TestCase
import java.text.ParseException
import java.text.SimpleDateFormat


class EpisodeActionFilterTest : TestCase() {
//    var episodeActionFilter: EpisodeActionFilter = EpisodeActionFilter

    @Throws(ParseException::class)
    fun testGetRemoteActionsHappeningAfterLocalActions() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val morning = format.parse("2021-01-01 08:00:00")
        val lateMorning = format.parse("2021-01-01 09:00:00")

        val episodeActions: MutableList<EpisodeAction> = ArrayList()
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(10)
            .build()
        )
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(lateMorning)
            .position(20)
            .build()
        )
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(5)
            .build()
        )

        val morningFiveMinutesLater = format.parse("2021-01-01 08:05:00")
        val remoteActions: MutableList<EpisodeAction> = ArrayList()
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesLater)
            .position(10)
            .build()
        )
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesLater)
            .position(5)
            .build()
        )

        val uniqueList =getRemoteActionsOverridingLocalActions(remoteActions, episodeActions)
        assertSame(1, uniqueList.size)
    }

    @Throws(ParseException::class)
    fun testGetRemoteActionsHappeningBeforeLocalActions() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val morning = format.parse("2021-01-01 08:00:00")
        val lateMorning = format.parse("2021-01-01 09:00:00")

        val episodeActions: MutableList<EpisodeAction> = ArrayList()
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(10)
            .build()
        )
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(lateMorning)
            .position(20)
            .build()
        )
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(5)
            .build()
        )

        val morningFiveMinutesEarlier = format.parse("2021-01-01 07:55:00")
        val remoteActions: MutableList<EpisodeAction> = ArrayList()
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesEarlier)
            .position(10)
            .build()
        )
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesEarlier)
            .position(5)
            .build()
        )

        val uniqueList = getRemoteActionsOverridingLocalActions(remoteActions, episodeActions)
        assertSame(0, uniqueList.size)
    }

    @Throws(ParseException::class)
    fun testGetMultipleRemoteActionsHappeningAfterLocalActions() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val morning = format.parse("2021-01-01 08:00:00")

        val episodeActions: MutableList<EpisodeAction> = ArrayList()
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(10)
            .build()
        )
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(5)
            .build()
        )

        val morningFiveMinutesLater = format.parse("2021-01-01 08:05:00")
        val remoteActions: MutableList<EpisodeAction> = ArrayList()
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesLater)
            .position(10)
            .build()
        )
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesLater)
            .position(5)
            .build()
        )

        val uniqueList = getRemoteActionsOverridingLocalActions(remoteActions, episodeActions)
        assertEquals(2, uniqueList.size)
    }

    @Throws(ParseException::class)
    fun testGetMultipleRemoteActionsHappeningBeforeLocalActions() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val morning = format.parse("2021-01-01 08:00:00")

        val episodeActions: MutableList<EpisodeAction> = ArrayList()
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(10)
            .build()
        )
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morning)
            .position(5)
            .build()
        )

        val morningFiveMinutesEarlier = format.parse("2021-01-01 07:55:00")
        val remoteActions: MutableList<EpisodeAction> = ArrayList()
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesEarlier)
            .position(10)
            .build()
        )
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.2", EpisodeAction.Action.PLAY)
            .timestamp(morningFiveMinutesEarlier)
            .position(5)
            .build()
        )

        val uniqueList = getRemoteActionsOverridingLocalActions(remoteActions, episodeActions)
        assertEquals(0, uniqueList.size)
    }

    @Throws(ParseException::class)
    fun testPresentRemoteTimestampOverridesMissingLocalTimestamp() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val arbitraryTime = format.parse("2021-01-01 08:00:00")

        val episodeActions: MutableList<EpisodeAction> = ArrayList()
        episodeActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY) // no timestamp
            .position(10)
            .build()
        )

        val remoteActions: MutableList<EpisodeAction> = ArrayList()
        remoteActions.add(EpisodeAction.Builder("podcast.a", "episode.1", EpisodeAction.Action.PLAY)
            .timestamp(arbitraryTime)
            .position(10)
            .build()
        )

        val uniqueList = getRemoteActionsOverridingLocalActions(remoteActions, episodeActions)
        assertSame(1, uniqueList.size)
    }
}
