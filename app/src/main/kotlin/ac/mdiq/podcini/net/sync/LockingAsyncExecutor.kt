package ac.mdiq.podcini.net.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock

object LockingAsyncExecutor {
    @JvmField
    val lock: ReentrantLock = ReentrantLock()

    /**
     * Take the lock and execute runnable (to prevent changes to preferences being lost when enqueueing while sync is
     * in progress). If the lock is free, the runnable is directly executed in the calling thread to prevent overhead.
     */
    @JvmStatic
    fun executeLockedAsync(runnable: Runnable) {
        if (lock.tryLock()) {
            try {
                runnable.run()
            } finally {
                lock.unlock()
            }
        } else {
//            Completable.fromRunnable {
//                lock.lock()
//                try {
//                    runnable.run()
//                } finally {
//                    lock.unlock()
//                }
//            }.subscribeOn(Schedulers.io()).subscribe()

            val coroutineScope = CoroutineScope(Dispatchers.Main)
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    lock.lock()
                    try {
                        runnable.run()
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }
    }
}
