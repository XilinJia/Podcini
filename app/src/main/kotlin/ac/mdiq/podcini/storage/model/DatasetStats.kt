package ac.mdiq.podcini.storage.model

class DatasetStats(val queueSize: Int,
                   val numDownloaded: Int,
                   val numReclaimables: Int,
                   val numEpisodes: Int,
                   val numFeeds: Int,
                   val historyCount: Int)
