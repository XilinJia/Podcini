package ac.mdiq.podcini.storage.model

import java.util.ArrayList

class StatisticsItem(val feed: Feed, val time: Long,
                     val timePlayed: Long,  // Respects speed, listening twice, ...
                     val episodes: Long,    // Number of episodes.
                     val episodesStarted: Long, // Episodes that are actually played.
                     val totalDownloadSize: Long,   // Simply sums up the size of download podcasts.
                     val episodesDownloadCount: Long    // Stores the number of episodes downloaded.
)

class MonthlyStatisticsItem {
    var year: Int = 0
    var month: Int = 0
    var timePlayed: Long = 0
}

class StatisticsResult {
    var feedTime: MutableList<StatisticsItem> = ArrayList()
    var oldestDate: Long = System.currentTimeMillis()
}
