package ac.mdiq.podcini.service.download

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedCounter
import ac.mdiq.podcini.ui.gui.NotificationUtils
import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class NewEpisodesNotification {
    private var countersBefore: Map<Long, Int>? = null

    fun loadCountersBeforeRefresh() {
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        countersBefore = adapter.getFeedCounters(FeedCounter.SHOW_NEW)
        adapter.close()
    }

    fun showIfNeeded(context: Context, feed: Feed) {
        val prefs = feed.preferences
        if (prefs == null || !prefs.keepUpdated || !prefs.showEpisodeNotification) {
            return
        }

        val newEpisodesBefore = if (countersBefore!!.containsKey(feed.id)) countersBefore!![feed.id]!! else 0
        val newEpisodesAfter = getNewEpisodeCount(feed.id)

        Log.d(TAG, "New episodes before: $newEpisodesBefore, after: $newEpisodesAfter")
        if (newEpisodesAfter > newEpisodesBefore) {
            val notificationManager = NotificationManagerCompat.from(context)
            showNotification(newEpisodesAfter, feed, context, notificationManager)
        }
    }

    companion object {
        private const val TAG = "NewEpisodesNotification"
        private const val GROUP_KEY = "ac.mdiq.podcini.EPISODES"

        private fun showNotification(newEpisodes: Int, feed: Feed, context: Context,
                                     notificationManager: NotificationManagerCompat
        ) {
            val res = context.resources
            val text = res.getQuantityString(
                R.plurals.new_episode_notification_message, newEpisodes, newEpisodes, feed.title
            )
            val title = res.getQuantityString(R.plurals.new_episode_notification_title, newEpisodes)

            val intent = Intent()
            intent.setAction("NewEpisodes" + feed.id)
            intent.setComponent(ComponentName(context, "ac.mdiq.podcini.activity.MainActivity"))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("fragment_feed_id", feed.id)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent,
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

            val notification = NotificationCompat.Builder(
                context, NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS)
                .setSmallIcon(R.drawable.ic_notification_new)
                .setContentTitle(title)
                .setLargeIcon(loadIcon(context, feed))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setGroup(GROUP_KEY)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()

            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.e(TAG, "showNotification: require POST_NOTIFICATIONS permission")
                Toast.makeText(context, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
                return
            }
            notificationManager.notify(NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS,
                feed.hashCode(),
                notification)
            showGroupSummaryNotification(context, notificationManager)
        }

        private fun showGroupSummaryNotification(context: Context, notificationManager: NotificationManagerCompat) {
            val intent = Intent()
            intent.setAction("NewEpisodes")
            intent.setComponent(ComponentName(context, "ac.mdiq.podcini.activity.MainActivity"))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("fragment_tag", "NewEpisodesFragment")
            val pendingIntent = PendingIntent.getActivity(context, 0, intent,
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

            val notificationGroupSummary = NotificationCompat.Builder(
                context, NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS)
                .setSmallIcon(R.drawable.ic_notification_new)
                .setContentTitle(context.getString(R.string.new_episode_notification_group_text))
                .setContentIntent(pendingIntent)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.e(TAG, "showGroupSummaryNotification: require POST_NOTIFICATIONS permission")
                Toast.makeText(context, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
                return
            }
            notificationManager.notify(NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS, 0, notificationGroupSummary)
        }

        private fun loadIcon(context: Context, feed: Feed): Bitmap? {
            val iconSize = (128 * context.resources.displayMetrics.density).toInt()
            return try {
                if (!feed.imageUrl.isNullOrBlank()) Glide.with(context)
                    .asBitmap()
                    .load(feed.imageUrl)
                    .apply(RequestOptions().centerCrop())
                    .submit(iconSize, iconSize)
                    .get()
                else null
            } catch (tr: Throwable) {
                null
            }
        }

        private fun getNewEpisodeCount(feedId: Long): Int {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            val counters = adapter.getFeedCounters(FeedCounter.SHOW_NEW, feedId)
            val episodeCount = if (counters.containsKey(feedId)) counters[feedId]!! else 0
            adapter.close()
            return episodeCount
        }
    }
}
