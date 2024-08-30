package ac.mdiq.podcini.storage.algorithms

import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.Info
import ac.mdiq.vista.extractor.InfoItem.InfoType
import ac.mdiq.vista.extractor.ServiceList
import androidx.collection.LruCache
import java.util.concurrent.TimeUnit

// derived from VoiVista
class InfoCache private constructor() {
    private val TAG: String = javaClass.simpleName

    val size: Long
        get() {
            synchronized(LRU_CACHE) {
                return LRU_CACHE.size().toLong()
            }
        }

    fun getFromKey(serviceId: Int, url: String, infoType: InfoType): Info? {
        Logd(TAG, "getFromKey() called with: serviceId = [$serviceId], url = [$url]")
        synchronized(LRU_CACHE) {
            return getInfo(keyOf(serviceId, url, infoType))
        }
    }

    fun putInfo(serviceId: Int, url: String, info: Info, infoType: InfoType) {
        Logd(TAG, "putInfo() called with: info = [$info]")
        val expirationMillis = getCacheExpirationMillis(info.serviceId)
        synchronized(LRU_CACHE) {
            val data = CacheData(info, expirationMillis)
            LRU_CACHE.put(keyOf(serviceId, url, infoType), data)
        }
    }

    private fun getCacheExpirationMillis(serviceId: Int): Long {
        return if (serviceId == ServiceList.SoundCloud.serviceId) TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES)
        else TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
    }

    fun removeInfo(serviceId: Int, url: String, infoType: InfoType) {
        Logd(TAG, "removeInfo() called with: serviceId = [$serviceId], url = [$url]")
        synchronized(LRU_CACHE) {
            LRU_CACHE.remove(keyOf(serviceId, url, infoType))
        }
    }

    fun clearCache() {
        Logd(TAG, "clearCache() called")
        synchronized(LRU_CACHE) {
            LRU_CACHE.evictAll()
        }
    }

    fun trimCache() {
        Logd(TAG, "trimCache() called")
        synchronized(LRU_CACHE) {
            removeStaleCache()
            LRU_CACHE.trimToSize(TRIM_CACHE_TO)
        }
    }

    private class CacheData(val info: Info, timeoutMillis: Long) {
        private val expireTimestamp = System.currentTimeMillis() + timeoutMillis
        val isExpired: Boolean
            get() = System.currentTimeMillis() > expireTimestamp
    }

    companion object {
//        private val DEBUG = MainActivity.DEBUG

        val instance: InfoCache = InfoCache()
        private const val MAX_ITEMS_ON_CACHE = 60

        /**
         * Trim the cache to this size.
         */
        private const val TRIM_CACHE_TO = 30

        private val LRU_CACHE = LruCache<String, CacheData>(MAX_ITEMS_ON_CACHE)

        private fun keyOf(serviceId: Int, url: String, infoType: InfoType): String {
            return serviceId.toString() + url + infoType.toString()
        }

        private fun removeStaleCache() {
            for ((key, data) in LRU_CACHE.snapshot()) {
                if (data != null && data.isExpired) LRU_CACHE.remove(key)
            }
        }

        private fun getInfo(key: String): Info? {
            val data = LRU_CACHE[key] ?: return null
            if (data.isExpired) {
                LRU_CACHE.remove(key)
                return null
            }
            return data.info
        }
    }
}
