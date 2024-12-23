package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.showStackTrace
import android.util.Log
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlinx.coroutines.*
import kotlin.coroutines.ContinuationInterceptor

object RealmDB {
    private val TAG: String = RealmDB::class.simpleName ?: "Anonymous"

    private val ioScope = CoroutineScope(Dispatchers.IO)

    val realm: Realm

    init {
        val config = RealmConfiguration.Builder(
            schema = setOf(
                Feed::class,
                FeedPreferences::class,
                FeedMeasures::class,
                Episode::class,
//                EpisodeMedia::class,
                CurrentState::class,
                PlayQueue::class,
                DownloadResult::class,
                ShareLog::class,
                SubscriptionLog::class,
                Chapter::class))
            .name("Podcini.realm")
            .schemaVersion(37)
            .migration({ mContext ->
                val oldRealm = mContext.oldRealm // old realm using the previous schema
                val newRealm = mContext.newRealm // new realm using the new schema
                if (oldRealm.schemaVersion() < 25) {
                    Logd(TAG, "migrating DB from below 25")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            set(
                                "rating",
                                if (oldObject.getValue<Boolean>(fieldName = "isFavorite")) 2L else 0L
                            )
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 26) {
                    Logd(TAG, "migrating DB from below 26")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            if (oldObject.getValue<Long>(fieldName = "rating") == 0L) set("rating", -3L)
                        }
                    }
//                    val feeds = oldRealm.query(className = "Feed").query("id > 10000").find()
//                    for (f in feeds) {
//                        val id = f.getValue(propertyName = "id", Long::class)
//                        val url = f.getNullableValue(propertyName = "downloadUrl", String::class)
//                        val link = f.getNullableValue(propertyName = "link", String::class)
//                        val title = f.getNullableValue(propertyName = "eigenTitle", String::class)
//                        val subLog = newRealm.copyToRealm(
//                            DynamicMutableRealmObject.create(
//                                type = "SubscriptionLog",
//                                mapOf(
//                                    "id" to id/100,
//                                    "itemId" to id,
//                                    "url" to url,
//                                    "link" to link,
//                                    "type" to "Feed",
//                                    "title" to title,
//                                )
//                            )
//                        )
//                    }
//                    val episodes = oldRealm.query(className = "Episode").query("feedId < 100").find()
//                    for (e in episodes) {
//                        val id = e.getValue(propertyName = "id", Long::class)
//                        val media = oldRealm.query(className = "EpisodeMedia").query("id == $id").first().find()
//                        val url = media?.getNullableValue(propertyName = "downloadUrl", String::class) ?:""
//                        val link = e.getNullableValue(propertyName = "link", String::class)
//                        val title = e.getNullableValue(propertyName = "title", String::class)
//                        val subLog = newRealm.copyToRealm(
//                            DynamicMutableRealmObject.create(
//                                type = "SubscriptionLog",
//                                mapOf(
//                                    "id" to id/100,
//                                    "itemId" to id,
//                                    "url" to url,
//                                    "link" to link,
//                                    "type" to "Media",
//                                    "title" to title,
//                                )
//                            )
//                        )
//                    }
                }
                if (oldRealm.schemaVersion() < 28) {
                    Logd(TAG, "migrating DB from below 28")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            if (oldObject.getValue<Long>(fieldName = "playState") == 1L) {
                                set("playState", 10L)
                            } else {
                                val media = oldObject.getObject(propertyName = "media")
                                var position = 0L
                                if (media != null) position = media.getValue(propertyName = "position", Long::class)
                                if (position > 0) set("playState", 5L)
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 30) {
                    Logd(TAG, "migrating DB from below 30")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            val media = oldObject.getObject(propertyName = "media")
                            var playedDuration = 0L
                            if (media != null) {
                                playedDuration = media.getValue(propertyName = "playedDuration", Long::class)
                                Logd(TAG, "position: $playedDuration")
                                if (playedDuration > 0L) {
                                    val newMedia = newObject.getObject(propertyName = "media")
                                    newMedia?.set("timeSpent", playedDuration)
                                }
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 37) {
                    Logd(TAG, "migrating DB from below 37")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            Logd(TAG, "start: ${getNullableValue("title", String::class)}")
                            val media = oldObject.getObject(propertyName = "media")
                            var playedDuration = 0L
                            if (media != null) {
                                set("fileUrl", media.getNullableValue("fileUrl", String::class))
                                set("downloadUrl", media.getNullableValue("downloadUrl", String::class))
                                set("mimeType", media.getNullableValue("mimeType", String::class))
                                Logd(TAG, "after mimeType")
                                set("downloaded", media.getValue("downloaded", Boolean::class))
                                Logd(TAG, "after downloaded")
                                set("downloadTime", media.getValue("downloadTime", Long::class))
                                set("duration", media.getValue("duration", Long::class))
                                set("position", media.getValue("position", Long::class))
                                set("lastPlayedTime", media.getValue("lastPlayedTime", Long::class))
                                set("startPosition", media.getValue("startPosition", Long::class))
                                Logd(TAG, "after startPosition")
                                set("playedDurationWhenStarted", media.getValue("playedDurationWhenStarted", Long::class))
                                set("playedDuration", media.getValue("playedDuration", Long::class))
                                set("timeSpentOnStart", media.getValue("timeSpentOnStart", Long::class))
                                set("startTime", media.getValue("startTime", Long::class))
                                Logd(TAG, "after startTime")
                                set("timeSpent", media.getValue("timeSpent", Long::class))
                                set("size", media.getValue("size", Long::class))
                                set("playbackCompletionTime", media.getValue("playbackCompletionTime", Long::class))
                                set("playbackCompletionTime", media.getValue("playbackCompletionTime", Long::class))
                                Logd(TAG, "after all")
                            }
                        }
                    }
                }
            }).build()
        realm = Realm.open(config)
    }

    fun <T : TypedRealmObject> unmanaged(entity: T) : T {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd(TAG, "${caller?.className}.${caller?.methodName} unmanaged: ${entity.javaClass.simpleName}")
        }
        return if (entity.isManaged()) realm.copyFromRealm(entity) else entity
    }

    suspend fun <T : TypedRealmObject> update(entity: T, block: MutableRealm.(T) -> Unit) : T {
        return realm.write {
            val result: T = findLatest(entity)?.let {
                block(it)
                it
            } ?: entity
            result
        }
    }

    suspend fun <T : RealmObject> upsert(entity: T, block: MutableRealm.(T) -> Unit) : T {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd(TAG, "${caller?.className}.${caller?.methodName} upsert: ${entity.javaClass.simpleName}")
        }
        return realm.write {
            var result: T = entity
            if (entity.isManaged()) {
                result = findLatest(entity)?.let {
                    block(it)
                    it
                } ?: entity
            } else {
                try {
                    result = copyToRealm(entity, UpdatePolicy.ALL).let {
                        block(it)
                        it
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "copyToRealm error: ${e.message}")
                    showStackTrace()
                }
            }
            result
        }
    }

    fun <T : RealmObject> upsertBlk(entity: T, block: MutableRealm.(T) -> Unit) : T {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd(TAG, "${caller?.className}.${caller?.methodName} upsertBlk: ${entity.javaClass.simpleName}")
        }
        return realm.writeBlocking {
            var result: T = entity
            if (entity.isManaged()) {
                result = findLatest(entity)?.let {
                    block(it)
                    it
                } ?: entity
            } else {
                try {
                    result = copyToRealm(entity, UpdatePolicy.ALL).let {
                        block(it)
                        it
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "copyToRealm error: ${e.message}")
                    showStackTrace()
                }
            }
            result
        }
    }
    
    fun runOnIOScope(block: suspend () -> Unit) : Job {
        return ioScope.launch {
            if (Dispatchers.IO == coroutineContext[ContinuationInterceptor]) block()
            else withContext(Dispatchers.IO) { block() }
        }
    }
}