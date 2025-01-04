package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {
    enum class CHANNEL_ID {
        user_action,
        downloading,
        playing,
        error,
        sync_error,
        episode_notifications
    }

    enum class GROUP_ID {
        group_errors,
        group_news
    }

    fun createChannels(context: Context) {
        val mNotificationManager = NotificationManagerCompat.from(context)

        val channelGroups = listOf(
            createGroupErrors(context)
//            createGroupNews(context)
        )
        mNotificationManager.createNotificationChannelGroupsCompat(channelGroups)

        val channels = listOf(
            createChannelUserAction(context),
            createChannelDownloading(context),
            createChannelPlaying(context),
            createChannelError(context),
            createChannelSyncError(context)
//            createChannelEpisodeNotification(context)
        )
        mNotificationManager.createNotificationChannelsCompat(channels)

        mNotificationManager.deleteNotificationChannelGroup(GROUP_ID.group_news.name)
        mNotificationManager.deleteNotificationChannel(CHANNEL_ID.episode_notifications.name)
    }

    private fun createChannelUserAction(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID.user_action.name, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(c.getString(R.string.notification_channel_user_action))
            .setDescription(c.getString(R.string.notification_channel_user_action_description))
            .setGroup(GROUP_ID.group_errors.name)
            .build()
    }

    private fun createChannelDownloading(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID.downloading.name, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(c.getString(R.string.notification_channel_downloading))
            .setDescription(c.getString(R.string.notification_channel_downloading_description))
            .setShowBadge(false)
            .build()
    }

    private fun createChannelPlaying(c: Context): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(
            CHANNEL_ID.playing.name, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(c.getString(R.string.notification_channel_playing))
            .setDescription(c.getString(R.string.notification_channel_playing_description))
            .setShowBadge(false)
            .build()
    }

    private fun createChannelError(c: Context): NotificationChannelCompat {
        val notificationChannel = NotificationChannelCompat.Builder(
            CHANNEL_ID.error.name, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(c.getString(R.string.notification_channel_download_error))
            .setDescription(c.getString(R.string.notification_channel_download_error_description))
            .setGroup(GROUP_ID.group_errors.name)

        // Migration from app managed setting: disable notification
        if (!getPref(AppPrefs.prefShowDownloadReport, true)) notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
        return notificationChannel.build()
    }

    private fun createChannelSyncError(c: Context): NotificationChannelCompat {
        val notificationChannel = NotificationChannelCompat.Builder(
            CHANNEL_ID.sync_error.name, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(c.getString(R.string.notification_channel_sync_error))
            .setDescription(c.getString(R.string.notification_channel_sync_error_description))
            .setGroup(GROUP_ID.group_errors.name)

        // Migration from app managed setting: disable notification
        if (!getPref(AppPrefs.pref_gpodnet_notifications, true)) notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
        return notificationChannel.build()
    }

    private fun createGroupErrors(c: Context): NotificationChannelGroupCompat {
        return NotificationChannelGroupCompat.Builder(GROUP_ID.group_errors.name).setName(c.getString(R.string.notification_group_errors)).build()
    }
}
