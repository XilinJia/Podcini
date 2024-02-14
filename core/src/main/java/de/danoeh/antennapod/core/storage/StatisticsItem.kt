package de.danoeh.antennapod.core.storage

import de.danoeh.antennapod.model.feed.Feed

class StatisticsItem(val feed: Feed, val time: Long,
                     /**
                      * Respects speed, listening twice, ...
                      */
                     val timePlayed: Long,
                     /**
                      * Number of episodes.
                      */
                     val episodes: Long,
                     /**
                      * Episodes that are actually played.
                      */
                     val episodesStarted: Long,
                     /**
                      * Simply sums up the size of download podcasts.
                      */
                     val totalDownloadSize: Long,
                     /**
                      * Stores the number of episodes downloaded.
                      */
                     val episodesDownloadCount: Long
)
