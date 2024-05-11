package ac.mdiq.podcini.util.error

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.util.error.CrashReportWriter.Companion.write
import android.util.Log
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins

object RxJavaErrorHandlerSetup {
    private const val TAG = "RxJavaErrorHandler"

    fun setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler { exception: Throwable? ->
            if (exception is UndeliverableException) {
                // Probably just disposed because the fragment was left
                Log.d(TAG, "Ignored exception: " + Log.getStackTraceString(exception))
                return@setErrorHandler
            }
            // Usually, undeliverable exceptions are wrapped in an UndeliverableException.
            // If an undeliverable exception is a NPE (or some others), wrapping does not happen.
            // Podcini threads might throw NPEs after disposing because we set controllers to null.
            // Just swallow all exceptions here.
            Log.e(TAG, Log.getStackTraceString(exception))
            write(exception!!)
            if (BuildConfig.DEBUG) {
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), exception)
            }
        }
    }
}
