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
import io.realm.kotlin.migration.AutomaticSchemaMigration
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
                EpisodeMedia::class,
                CurrentState::class,
                PlayQueue::class,
                DownloadResult::class,
                ShareLog::class,
                Chapter::class))
            .name("Podcini.realm")
            .schemaVersion(25)
            .migration({ mContext ->
                val oldRealm = mContext.oldRealm // old realm using the previous schema
                val newRealm = mContext.newRealm // new realm using the new schema
                if (oldRealm.schemaVersion() < 25) {
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            set(
                                "rating",
                                if (oldObject.getValue<Boolean>(fieldName = "isFavorite")) 2L else 0L
                            )
                        }
                    }
                }
            })
            .build()
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