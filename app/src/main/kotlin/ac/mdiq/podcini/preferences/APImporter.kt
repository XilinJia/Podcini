package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.TAG_SEPARATOR
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.util.Logd
import android.app.Activity
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun importAP(uri: Uri, activity: Activity, onDismiss: ()->Unit) {
    val TAG = "importAP"

    fun buildEpisodes(db: SQLiteDatabase, feed: Feed) {
        val favIds = mutableSetOf<Long>()
        val favCursor = db.rawQuery("SELECT feeditem FROM Favorites WHERE feed = ${feed.id}", null)
        favCursor.use {
            while (favCursor.moveToNext()) {
                val index = favCursor.getColumnIndex("feeditem")
                if (index >= 0) favIds.add(favCursor.getLong(index))
            }
        }
        val cursor = db.rawQuery("SELECT *, FeedItems.id AS FeedItems_id, FeedMedia.id AS FeedMedia_id FROM FeedItems INNER JOIN FeedMedia ON FeedItems.id = FeedMedia.feeditem WHERE FeedItems.feed = ${feed.id}", null)
        cursor.use {
            val columnCount = cursor.columnCount
            val episodes = mutableListOf<Episode>()
            while (cursor.moveToNext()) {
                val episode = Episode()
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "FeedItems_id" -> episode.id = cursor.getLong(i)
                        "title" -> episode.title = cursor.getStringOrNull(i)
                        "pubDate" -> episode.pubDate = cursor.getLong(i)
                        "read" -> {
                            val read = cursor.getInt(i)
                            if (read == -1) episode.playState = PlayState.NEW.code
                            else episode.setPlayed(read == 1)
                        }
                        "link" -> episode.link = cursor.getStringOrNull(i)
                        "description" -> episode.description = cursor.getStringOrNull(i)
                        "payment_link" -> episode.paymentLink = cursor.getStringOrNull(i)
//                                "media" -> episode.link = cursorItem.getString(i)
//                                "has_simple_chapter" -> episode.chapters
                        "item_identifier" -> episode.identifier = cursor.getString(i)
                        "image_url" -> episode.imageUrl = cursor.getStringOrNull(i)
                        "auto_download" -> episode.isAutoDownloadEnabled = cursor.getInt(i) == 1
                        "duration" -> episode.duration = cursor.getInt(i)
                        "file_url" -> episode.fileUrl = cursor.getStringOrNull(i)
                        "download_url" -> episode.downloadUrl = cursor.getStringOrNull(i)
//                        "downloaded" -> {
//                            val t = cursor.getLong(i)
//                            episode.downloadTime = t
//                            if (t > 0) episode.downloaded = true
//                        }
                        "position" -> episode.position = cursor.getInt(i)
                        "filesize" -> episode.size = cursor.getLong(i)
                        "mime_type" -> episode.mimeType = cursor.getStringOrNull(i)
                        "playback_completion_date" -> episode.playbackCompletionTime = cursor.getLong(i)
                        "played_duration" -> episode.playedDuration = cursor.getIntOrNull(i) ?: 0
                        "has_embedded_picture" -> episode.hasEmbeddedPicture = cursor.getInt(i) == 1
                        "last_played_time" -> episode.lastPlayedTime = cursor.getLong(i)
                    }
                }
                if (episode.id in favIds) episode.rating = Rating.SUPER.code
//                Logd(TAG, "episode title: ${episode.title}")
                episodes.add(episode)
            }
            feed.episodes = episodes.toRealmList()
        }
    }

    fun buildFeeds(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT * FROM Feeds", null)
        cursor.use {
            val columnCount = cursor.columnCount
            while (cursor.moveToNext()) {
                val feed = Feed()
//                val pref = FeedPreferences()
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "id" -> feed.id = cursor.getLong(i)
                        "title" -> feed.eigenTitle = cursor.getString(i)
                        "custom_title" -> feed.customTitle = cursor.getStringOrNull(i)
                        "file_url" -> feed.fileUrl = cursor.getStringOrNull(i)
                        "download_url" -> feed.downloadUrl = cursor.getString(i)
//                        "downloaded" -> feed.
                        "link" -> feed.link = cursor.getStringOrNull(i)
                        "description" -> feed.description = cursor.getStringOrNull(i)
                        "payment_link" -> feed.payment_link = cursor.getStringOrNull(i)
                        "last_update" -> feed.lastUpdate = cursor.getStringOrNull(i)
                        "language" -> feed.language = cursor.getStringOrNull(i)
                        "author" -> feed.author = cursor.getStringOrNull(i)
                        "image_url" -> feed.imageUrl = cursor.getStringOrNull(i)
                        "type" -> feed.type = cursor.getStringOrNull(i)
                        "feed_identifier" -> feed.identifier = cursor.getStringOrNull(i)
                        "auto_download" -> feed.autoDownload = cursor.getInt(i) == 1
                        "username" -> feed.username = cursor.getStringOrNull(i)
                        "password" -> feed.password = cursor.getStringOrNull(i)
                        "keep_update" -> feed.keepUpdated = cursor.getInt(i) == 1
                        "feed_playback_speed" -> feed.playSpeed = cursor.getFloat(i)
                        "tags" -> feed.tags = cursor.getStringOrNull(i)?.split(TAG_SEPARATOR)?.toRealmSet() ?: realmSetOf()
                    }
                }
                Logd(TAG, "feed title: ${feed.title}")
//                feed.preferences = pref
                buildEpisodes(db, feed)

                feed.id = 0L
                for (item in feed.episodes) {
                    item.id = 0L
                    item.feedId = null
                    item.feed = feed
                }
                updateFeed(activity, feed, false, true)
            }
        }
    }

    Logd(TAG, "chooseAPImportPathLauncher: uri: $uri")

    CoroutineScope(Dispatchers.IO).launch {
        val dbFile = File(activity.filesDir, "temp.db")
        activity.contentResolver.openInputStream(uri)?.use { inputStream -> dbFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) } }
        val database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)

        buildFeeds(database)

        database.close()
        dbFile.delete()
        onDismiss()
    }
}