package ac.mdiq.podcini.core.util.gui

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import ac.mdiq.podcini.core.R
import ac.mdiq.podcini.storage.preferences.UserPreferences.gpodnetNotificationsEnabledRaw
import ac.mdiq.podcini.storage.preferences.UserPreferences.showDownloadReportRaw

object NotificationUtils {
    const val CHANNEL_ID_USER_ACTION: String = "user_action"
    const val CHANNEL_ID_DOWNLOADING: String = "downloading"
    const val CHANNEL_ID_PLAYING: String = "playing"
    const val CHANNEL_ID_DOWNLOAD_ERROR: String = "error"
    const val CHANNEL_ID_SYNC_ERROR: String = "sync_error"
    const val CHANNEL_ID_EPISODE_NOTIFICATIONS: String = "episode_notifications"

    const val GROUP_ID_ERRORS: String = "group_errors"
    const val GROUP_ID_NEWS: String = "group_news"

    fun createChannels(context: Context) {
        val mNotificationManager = NotificationManagerCompat.from(context)

        val channelGroups = listOf(
            createGroupErrors(context),
            createGroupNews(context))
        mNotificationManager.createNotificationChannelGroupsCompat(channelGroups)

        val channels = listOf(
            createChannelUserAction(context),
            createChannelDownloading(context),
            createChannelPlaying(context),
            createChannelError(context),
            createChannelSyncError(context),
            createChannelEpisodeNotification(context))
        mNotificationManager.createNotificationChannelsCompat(channels)
    }

    private fun createChannelUserAction(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID_USER_ACTION, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(c.getString(R.string.notification_channel_user_action))
            .setDescription(c.getString(R.string.notification_channel_user_action_description))
            .setGroup(GROUP_ID_ERRORS)
            .build()
    }

    private fun createChannelDownloading(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID_DOWNLOADING, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(c.getString(R.string.notification_channel_downloading))
            .setDescription(c.getString(R.string.notification_channel_downloading_description))
            .setShowBadge(false)
            .build()
    }

    private fun createChannelPlaying(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID_PLAYING, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(c.getString(R.string.notification_channel_playing))
            .setDescription(c.getString(R.string.notification_channel_playing_description))
            .setShowBadge(false)
            .build()
    }

    private fun createChannelError(c: Context): NotificationChannelCompat {
        val notificationChannel = NotificationChannelCompat.Builder(
            CHANNEL_ID_DOWNLOAD_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(c.getString(R.string.notification_channel_download_error))
            .setDescription(c.getString(R.string.notification_channel_download_error_description))
            .setGroup(GROUP_ID_ERRORS)

        if (!showDownloadReportRaw) {
            // Migration from app managed setting: disable notification
            notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
        }
        return notificationChannel.build()
    }

    private fun createChannelSyncError(c: Context): NotificationChannelCompat {
        val notificationChannel = NotificationChannelCompat.Builder(
            CHANNEL_ID_SYNC_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(c.getString(R.string.notification_channel_sync_error))
            .setDescription(c.getString(R.string.notification_channel_sync_error_description))
            .setGroup(GROUP_ID_ERRORS)

        if (!gpodnetNotificationsEnabledRaw) {
            // Migration from app managed setting: disable notification
            notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
        }
        return notificationChannel.build()
    }

    private fun createChannelEpisodeNotification(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID_EPISODE_NOTIFICATIONS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(c.getString(R.string.notification_channel_new_episode))
            .setDescription(c.getString(R.string.notification_channel_new_episode_description))
            .setGroup(GROUP_ID_NEWS)
            .build()
    }

    private fun createGroupErrors(c: Context): NotificationChannelGroupCompat {
        return NotificationChannelGroupCompat.Builder(GROUP_ID_ERRORS)
            .setName(c.getString(R.string.notification_group_errors))
            .build()
    }

    private fun createGroupNews(c: Context): NotificationChannelGroupCompat {
        return NotificationChannelGroupCompat.Builder(GROUP_ID_NEWS)
            .setName(c.getString(R.string.notification_group_news))
            .build()
    }
}
