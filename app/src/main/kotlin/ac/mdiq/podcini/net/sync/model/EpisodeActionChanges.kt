package ac.mdiq.podcini.net.sync.model


class EpisodeActionChanges(val episodeActions: List<EpisodeAction>, val timestamp: Long) {
    override fun toString(): String {
        return ("EpisodeActionGetResponse{episodeActions=$episodeActions, timestamp=$timestamp}")
    }
}
