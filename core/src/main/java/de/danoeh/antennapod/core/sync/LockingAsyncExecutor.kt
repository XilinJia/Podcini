package de.danoeh.antennapod.core.sync

import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
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
            Completable.fromRunnable {
                lock.lock()
                try {
                    runnable.run()
                } finally {
                    lock.unlock()
                }
            }.subscribeOn(Schedulers.io())
                .subscribe()
        }
    }
}
