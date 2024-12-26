package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.util.Logd
import android.app.Activity
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.database.getStringOrNull
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun importPA(uri: Uri, activity: Activity, importDb: Boolean, importDirectory: Boolean, onDismiss: ()->Unit) {
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
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "_id" -> feed.id = cursor.getLong(i)
                        "name" -> feed.eigenTitle = cursor.getString(i)
                        "custom_name" -> feed.customTitle = cursor.getStringOrNull(i)
                        "feed_url" -> feed.downloadUrl = cursor.getString(i)
                        "homepage" -> feed.link = cursor.getStringOrNull(i)
                        "language" -> feed.language = cursor.getStringOrNull(i)
                        "author" -> feed.author = cursor.getStringOrNull(i)
                        "description" -> feed.description = cursor.getStringOrNull(i)
                        "login" -> feed.username = cursor.getStringOrNull(i)
                        "password" -> feed.password = cursor.getStringOrNull(i)
                        "automaticRefresh" -> feed.keepUpdated = cursor.getInt(i) == 1
                        "last_played_episode_date" -> {
                            val d = cursor.getLong(i)
                            feed.lastPlayed = if (d >= 0) d else 0
                        }
                        "subscribers" -> feed.subscriberCount = cursor.getInt(i)
                        "thumbnail_id" -> {
                            val id = cursor.getInt(i)
                            if (id >= 0) feed.imageUrl = idImageMap[id]
                        }
                    }
                }
                Logd(TAG, "feed title: ${feed.title}")
                feed.tags = pIdTagMap[feed.id.toInt()]?.toRealmSet() ?: realmSetOf()
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

    data class TeamInfo(val name: String, val homepage: String, val thumbId: Int, val store: String?)

    val idTeamInfoMap = mutableMapOf<Int, TeamInfo>()

    fun buildTeamMap(db: SQLiteDatabase) {
        val cursor0 = db.rawQuery("SELECT _id, home_page, thumbnail_id, store_url FROM teams", null)
        cursor0.use {
            val columnCount = cursor0.columnCount
            while (cursor0.moveToNext()) {
                var id = 0
                var name = ""
                var homepage = ""
                var thumbId = 0
                var store: String? = null
                for (i in 0 until columnCount) {
                    val columnName = cursor0.getColumnName(i)
                    when (columnName) {
                        "_id" -> id = cursor0.getInt(i)
                        "name" -> name = cursor0.getString(i)
                        "home_page" -> homepage = cursor0.getString(i)
                        "thumbnail_id" -> thumbId = cursor0.getInt(i)
                        "store_url" -> store = cursor0.getStringOrNull(i)
                    }
                }
                idTeamInfoMap[id] = TeamInfo(name, homepage, thumbId, store)
            }
        }
    }

    fun buildRelations(db: SQLiteDatabase, PAFeed: PAFeed) {
        val cursor = db.rawQuery("SELECT similar_id FROM relatedPodcasts WHERE url = '${PAFeed.feedUrl}'", null)
        cursor.use {
            val columnCount = cursor.columnCount
            while (cursor.moveToNext()) {
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "similar_id" -> PAFeed.related.add(cursor.getInt(i))
                    }
                }
            }
        }
    }


    fun buildDirectory(db: SQLiteDatabase) {
        buildImageMap(db)
        buildTeamMap(db)

        val cursor = db.rawQuery("SELECT * FROM podcasts", null)
        cursor.use {
            val columnCount = cursor.columnCount
            while (cursor.moveToNext()) {
                val PAFeed = PAFeed()
                for (i in 0 until columnCount) {
                    val columnName = cursor.getColumnName(i)
                    when (columnName) {
                        "_id" -> PAFeed.id = cursor.getInt(i)
                        "name" -> PAFeed.name = cursor.getString(i)
                        "team_id" -> {
                            val id = cursor.getInt(i)
                            if (id > 0) {
                                val ti = idTeamInfoMap[id]
                                if (ti != null) {
                                    PAFeed.teamName = ti.name
//                                    directory.teamhomepage = ti.homepage
//                                    directory.teamImageUrl = idImageMap[ti.thumbId]
//                                    directory.topicUrl = ti.store
                                }
                            }
                        }
                        "category" -> {
                            val cat = cursor.getStringOrNull(i)
                            if (!cat.isNullOrBlank()) PAFeed.category.addAll(cat.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                        }
                        "type" -> PAFeed.type = cursor.getString(i)
                        "homepage" -> PAFeed.homepage = cursor.getStringOrNull(i)
                        "latest_publication_date" -> PAFeed.lastPubDate = cursor.getLong(i)
                        "feed_url" -> PAFeed.feedUrl = cursor.getStringOrNull(i) ?:""
                        "language" -> PAFeed.language = cursor.getStringOrNull(i) ?: ""
                        "author" -> PAFeed.author = cursor.getStringOrNull(i) ?: ""
                        "description" -> PAFeed.description = cursor.getStringOrNull(i) ?:""
                        "subscribers" -> PAFeed.subscribers = cursor.getInt(i)
                        "thumbnail_id" -> {
                            val id = cursor.getInt(i)
                            if (id >= 0) PAFeed.imageUrl = idImageMap[id]
                        }
//                        "custom_name" -> directory.customName = cursor.getStringOrNull(i) ?:""
//                        "iTunesId" -> directory.iTunesId = cursor.getStringOrNull(i)
                        "averageDuration" -> PAFeed.aveDuration = cursor.getInt(i)
                        "frequency" -> PAFeed.frequency = cursor.getInt(i)
                        "episodesNb" -> PAFeed.episodesNb = cursor.getInt(i)
                        "reviews" -> PAFeed.reviews = cursor.getInt(i)
                        "hub_url" -> PAFeed.hubUrl = cursor.getStringOrNull(i)
                        "topic_url" -> PAFeed.topicUrl = cursor.getStringOrNull(i)
                    }
                }
                Logd(TAG, "feed title: ${PAFeed.name}")
                buildRelations(db, PAFeed)
                upsertBlk(PAFeed) {}
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

    fun unzipArchive(): File? {
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
        return if (targetFile.exists()) targetFile else null
    }

    Logd(TAG, "chooseAPImportPathLauncher: uri: $uri")

    val dbFile = unzipArchive() ?: return
    Logd(TAG, "chooseAPImportPathLauncher: dbFile: $dbFile")

    val database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)

    if (importDb) buildFeeds(database)

    if (importDirectory) buildDirectory(database)

    database.close()

    if (unzipDir != null) deleteDirectory(unzipDir)
    onDismiss()

}