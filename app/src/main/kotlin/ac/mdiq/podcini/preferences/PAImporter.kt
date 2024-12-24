package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.Logd
import android.app.Activity
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.database.getStringOrNull
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun importPA(uri: Uri, activity: Activity, onDismiss: ()->Unit) {
    val TAG = "importPA"

    val idImageMap = mutableMapOf<Int, String>()

    fun buildImageMap(db: SQLiteDatabase) {
        val cursor0 = db.rawQuery("SELECT _id, url FROM bitmaps", null)
        cursor0.use {
            val columnCount = cursor0.columnCount
            while (cursor0.moveToNext()) {
                var id = 0
                var url = ""
                for (i in 0 until columnCount) {
                    val columnName = cursor0.getColumnName(i)
                    when (columnName) {
                        "_id" -> id = cursor0.getInt(i)
                        "url" -> url = cursor0.getString(i)
                    }
                }
                idImageMap[id] = url
            }
        }
    }

    fun buildEpisodes(db: SQLiteDatabase, feed: Feed) {
        val cursor = db.rawQuery("SELECT * FROM episodes WHERE podcast_id = ${feed.id}", null)
        cursor.use {
            val columnCount = cursor.columnCount
            val episodes = mutableListOf<Episode>()
            while (cursor.moveToNext()) {
                val episode = Episode()
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "_id" -> episode.id = cursor.getLong(i)
                        "name" -> episode.title = cursor.getStringOrNull(i)
                        "url" -> episode.link = cursor.getStringOrNull(i)
                        "download_url" -> episode.downloadUrl = cursor.getStringOrNull(i)
                        "publication_date" -> episode.pubDate = cursor.getLong(i)
                        "short_description" -> episode.shortDescription = cursor.getStringOrNull(i)
                        "content" -> episode.description = cursor.getStringOrNull(i)
                        "type" -> episode.mimeType = cursor.getStringOrNull(i)
                        "position_to_resume" -> episode.position = cursor.getInt(i)
                        "new_status" -> {
                            val n = cursor.getInt(i)
                            if (n == 1) episode.playState = PlayState.NEW.code
                        }
                        "playing_status" -> {
                            val p = cursor.getInt(i)
                            if (p == 1) episode.playState = PlayState.PLAYED.code
                        }
                        "duration_ms" -> episode.duration = cursor.getInt(i)
                        "playback_date" -> {
                            val d = cursor.getLong(i)
                            episode.lastPlayedTime = if (d > 0) d else 0
                        }
                        "favorite" -> {
                            val f = cursor.getInt(i)
                            if (f == 1) episode.rating = Rating.SUPER.code
                        }
                        "size" -> episode.size = cursor.getLong(i)
                        "thumbnail_id" -> {
                            val id = cursor.getInt(i)
                            if (id >= 0) episode.imageUrl = idImageMap[id]
                        }
                        "donation_url" -> episode.paymentLink = cursor.getStringOrNull(i)


//                                "media" -> episode.link = cursorItem.getString(i)
//                                "has_simple_chapter" -> episode.chapters
//                        "item_identifier" -> episode.identifier = cursor.getString(i)
//                        "auto_download" -> episode.isAutoDownloadEnabled = cursor.getInt(i) == 1
//                        "file_url" -> episode.fileUrl = cursor.getStringOrNull(i)
//                        "downloaded" -> {
//                            val t = cursor.getLong(i)
//                            episode.downloadTime = t
//                            if (t > 0) episode.downloaded = true
//                        }
//                        "playback_completion_date" -> episode.playbackCompletionTime = cursor.getLong(i)
//                        "played_duration" -> episode.playedDuration = cursor.getIntOrNull(i) ?: 0
//                        "has_embedded_picture" -> episode.hasEmbeddedPicture = cursor.getInt(i) == 1
                    }
                }
//                Logd(TAG, "episode title: ${episode.title}")
                episodes.add(episode)
            }
            feed.episodes = episodes.toRealmList()
        }
    }

    fun assembleSubscriptions(db: SQLiteDatabase): Set<Int> {
        val feedIds = mutableSetOf<Int>()
        val cursor = db.rawQuery("SELECT podcast_id FROM episodes", null)
        cursor.use {
            val columnCount = cursor.columnCount
            while (cursor.moveToNext()) {
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "podcast_id" -> feedIds.add(cursor.getInt(i))
                    }
                }
            }
        }
        return feedIds
    }

    fun buildFeeds(db: SQLiteDatabase) {
        buildImageMap(db)

        val idTagMap = mutableMapOf<Int, String>()
        val cursor0 = db.rawQuery("SELECT _id, name FROM tags", null)
        cursor0.use {
            val columnCount = cursor0.columnCount
            while (cursor0.moveToNext()) {
                var id = 0
                var name = ""
                for (i in 0 until columnCount) {
                    val columnName = cursor0.getColumnName(i)
                    when (columnName) {
                        "_id" -> id = cursor0.getInt(i)
                        "name" -> name = cursor0.getString(i)
                    }
                }
                idTagMap[id] = name
            }
        }
        val pIdTagMap = mutableMapOf<Int, MutableSet<String>>()
        val cursor1 = db.rawQuery("SELECT tag_id, podcast_id FROM tag_relation", null)
        cursor1.use {
            val columnCount = cursor1.columnCount
            while (cursor1.moveToNext()) {
                var pid = 0
                var tid = 0
                for (i in 0 until columnCount) {
                    val columnName = cursor1.getColumnName(i)
                    when (columnName) {
                        "tag_id" -> tid = cursor1.getInt(i)
                        "podcast_id" -> pid = cursor1.getInt(i)
                    }
                }
                val tag = idTagMap[tid]
                if (tag != null) {
                    val tSet = pIdTagMap[pid]
                    if (tSet == null) pIdTagMap[pid] = mutableSetOf<String>(tag)
                    else tSet.add(tag)
                }
            }
        }

        val idsString = assembleSubscriptions(db).joinToString(",")
        val cursor = db.rawQuery("SELECT * FROM podcasts WHERE _id IN ($idsString)", null)
        cursor.use {
            val columnCount = cursor.columnCount
            while (cursor.moveToNext()) {
                val feed = Feed()
                val pref = FeedPreferences()
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "_id" -> feed.id = cursor.getLong(i)
                        "name" -> feed.eigenTitle = cursor.getString(i)
                        "custom_name" -> feed.customTitle = cursor.getStringOrNull(i)
//                        "file_url" -> feed.fileUrl = cursor.getStringOrNull(i)
                        "feed_url" -> feed.downloadUrl = cursor.getString(i)
//                        "topic_url" -> feed.link = cursor.getStringOrNull(i)
                        "homepage" -> feed.link = cursor.getStringOrNull(i)
                        "language" -> feed.language = cursor.getStringOrNull(i)
                        "author" -> feed.author = cursor.getStringOrNull(i)
                        "description" -> feed.description = cursor.getStringOrNull(i)
                        "login" -> pref.username = cursor.getStringOrNull(i)
                        "password" -> pref.password = cursor.getStringOrNull(i)
                        "automaticRefresh" -> pref.keepUpdated = cursor.getInt(i) == 1
                        "last_played_episode_date" -> {
                            val d = cursor.getLong(i)
                            feed.lastPlayed = if (d >= 0) d else 0
                        }
                        "subscribers" -> feed.subscriberCount = cursor.getInt(i)
                        "thumbnail_id" -> {
                            val id = cursor.getInt(i)
                            if (id >= 0) feed.imageUrl = idImageMap[id]
                        }

//                        "payment_link" -> feed.payment_link = cursor.getStringOrNull(i)
//                        "last_update" -> feed.lastUpdate = cursor.getStringOrNull(i)
//                        "type" -> feed.type = cursor.getStringOrNull(i)
//                        "feed_identifier" -> feed.identifier = cursor.getStringOrNull(i)
//                        "auto_download" -> pref.autoDownload = cursor.getInt(i) == 1
//                        "feed_playback_speed" -> pref.playSpeed = cursor.getFloat(i)
                    }
                }
                Logd(TAG, "feed title: ${feed.title}")
                pref.tags = pIdTagMap[feed.id.toInt()]?.toRealmSet() ?: realmSetOf()
                feed.preferences = pref
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

    fun deleteDirectory(directory: File): Boolean {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteDirectory(file)
                else file.delete()
            }
        }
        return directory.delete()
    }

    fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
            var entry: ZipEntry?
            while (zipInputStream.nextEntry.also { entry = it } != null) {
                val file = File(targetDir, entry!!.name)
                if (entry.isDirectory) file.mkdirs()
                else file.outputStream().use { outputStream -> zipInputStream.copyTo(outputStream) }
                zipInputStream.closeEntry()
            }
        }
    }

    var unzipDir: File? = null

    suspend fun unzipArchive(): File? = withContext(Dispatchers.IO) {
        val internalDir = activity.filesDir
        val zipFileName = "tempArchive.zip"
        val zipFile = File(internalDir, zipFileName)

        activity.contentResolver.openInputStream(uri)?.use { inputStream -> zipFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) } } ?: throw IllegalArgumentException("Unable to read from URI: $uri")

        unzipDir = File(internalDir, "UnzippedFiles")
        if (unzipDir.exists()) deleteDirectory(unzipDir)

        unzipDir.mkdir()

        unzip(zipFile, unzipDir)
        zipFile.delete()

        val targetFile = File(unzipDir, "podcastAddict.db")
        return@withContext if (targetFile.exists()) targetFile else null
    }

    Logd(TAG, "chooseAPImportPathLauncher: uri: $uri")

    CoroutineScope(Dispatchers.IO).launch {
        val dbFile = unzipArchive() ?: return@launch
        Logd(TAG, "chooseAPImportPathLauncher: dbFile: $dbFile")

        val database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)

        buildFeeds(database)
        database.close()

        if (unzipDir != null) deleteDirectory(unzipDir)
        onDismiss()
    }
}