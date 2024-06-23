package ac.mdiq.podcini.net.sync.model

import ac.mdiq.podcini.net.sync.model.SyncServiceException

interface ISyncService {
    @Throws(SyncServiceException::class)
    fun login()

    @Throws(SyncServiceException::class)
    fun getSubscriptionChanges(lastSync: Long): SubscriptionChanges?

    @Throws(SyncServiceException::class)
    fun uploadSubscriptionChanges(added: List<String>, removed: List<String>): UploadChangesResponse?

    @Throws(SyncServiceException::class)
    fun getEpisodeActionChanges(lastSync: Long): EpisodeActionChanges?

    @Throws(SyncServiceException::class)
    fun uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>): UploadChangesResponse?

    @Throws(SyncServiceException::class)
    fun logout()
}
