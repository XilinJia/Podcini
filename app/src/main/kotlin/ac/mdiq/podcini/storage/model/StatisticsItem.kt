package ac.mdiq.podcini.storage.model

import java.util.ArrayList

class StatisticsItem(val feed: Feed,
                     val time: Long,    // total time, in seconds
                     val timePlayed: Long,  // in seconds, Respects speed, listening twice, ...
                     val timeSpent: Long,  // in seconds, actual time spent playing
                     val durationOfStarted: Long,  // in seconds, total duration of episodes started playing
                     val numEpisodes: Long,    // Number of episodes.
                     val episodesStarted: Long, // Episodes that are actually played.
                     val totalDownloadSize: Long,   // Simply sums up the size of download podcasts.
                     val episodesDownloadCount: Long    // Stores the number of episodes downloaded.
)

class MonthlyStatisticsItem {
    var year: Int = 0
    var month: Int = 0
    var timePlayed: Long = 0
    var timeSpent: Long = 0
}

class StatisticsResult {
    var statsItems: MutableList<StatisticsItem> = ArrayList()
    var oldestDate: Long = System.currentTimeMillis()
}
