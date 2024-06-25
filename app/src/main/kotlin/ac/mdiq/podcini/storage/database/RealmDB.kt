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
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.*
import kotlin.coroutines.ContinuationInterceptor

object RealmDB {
    val TAG: String = RealmDB::class.simpleName ?: "Anonymous"

    val ioScope = CoroutineScope(Dispatchers.IO)
    val realm: Realm

    init {
        val config = RealmConfiguration.Builder(
            schema = setOf(
                Feed::class,
                FeedPreferences::class,
                Episode::class,
                EpisodeMedia::class,
                CurrentState::class,
                PlayQueue::class,
                DownloadResult::class,
                Chapter::class))
            .name("Podcini.realm")
            .schemaVersion(3)
            .build()
        realm = Realm.open(config)
    }

    fun <T : RealmObject> unmanagedCopy(entity: T) : T {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd(TAG, "${caller?.className}.${caller?.methodName} upsert: ${entity.javaClass.simpleName}")
        }
        return if (entity.isManaged()) realm.copyFromRealm(entity) else entity
    }

//    fun <T : RealmObject> updateBlk(entity: T, block: MutableRealm.(T) -> Unit) : T {
//        return realm.writeBlocking {
//            findLatest(entity)?.let {
//                block(it)
//            }
//            entity
//        }
//    }
//
//    fun <T : EmbeddedRealmObject> updateBlk(entity: T, block: MutableRealm.(T) -> Unit) : T {
//        return realm.writeBlocking {
//            findLatest(entity)?.let {
//                block(it)
//            }
//            entity
//        }
//    }

    suspend fun <T : RealmObject> update(entity: T, block: MutableRealm.(T) -> Unit) : T {
        return realm.write {
            findLatest(entity)?.let {
                block(it)
            }
            entity
        }
    }

    suspend fun <T : EmbeddedRealmObject> update(entity: T, block: MutableRealm.(T) -> Unit) : T {
        return realm.write {
            findLatest(entity)?.let {
                block(it)
            }
            entity
        }
    }

    suspend fun <T : RealmObject> upsert(entity: T, block: MutableRealm.(T) -> Unit) : T {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd(TAG, "${caller?.className}.${caller?.methodName} upsert: ${entity.javaClass.simpleName}")
        }
        return realm.write {
            if (entity.isManaged()) {
                findLatest(entity)?.let {
                    block(it)
                }
            } else {
                try {
                    copyToRealm(entity, UpdatePolicy.ALL).let {
                        block(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "copyToRealm error: ${e.message}")
                    showStackTrace()
                }
            }
            entity
        }
    }

    fun <T : RealmObject> upsertBlk(entity: T, block: MutableRealm.(T) -> Unit) : T {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd(TAG, "${caller?.className}.${caller?.methodName} upsertBlk: ${entity.javaClass.simpleName}")
        }
        return realm.writeBlocking {
            if (entity.isManaged()) {
                findLatest(entity)?.let {
                    block(it)
                }
            } else {
                try {
                    copyToRealm(entity, UpdatePolicy.ALL).let {
                        block(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "copyToRealm error: ${e.message}")
                    showStackTrace()
                }
            }
            entity
        }
    }

    fun runOnIOScope(block: suspend () -> Unit) : Job {
        return ioScope.launch {
            if (Dispatchers.IO == coroutineContext[ContinuationInterceptor]) block()
            else withContext(Dispatchers.IO) { block() }
        }
    }
}