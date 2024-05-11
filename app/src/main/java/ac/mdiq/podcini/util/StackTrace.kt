package ac.mdiq.podcini.util

import ac.mdiq.podcini.BuildConfig
import android.util.Log

fun Logd(t: String, m: String) {
    if (BuildConfig.DEBUG) Log.d(t, m)
}

fun showStackTrace() {
    if (BuildConfig.DEBUG) {
        val stackTraceElements = Thread.currentThread().stackTrace
        stackTraceElements.forEach { element ->
            Log.d("showStackTrace", element.toString())
        }
    }
}