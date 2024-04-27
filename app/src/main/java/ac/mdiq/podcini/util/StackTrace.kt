package ac.mdiq.podcini.util

import android.util.Log

fun showStackTrace() {
    val stackTraceElements = Thread.currentThread().stackTrace
    stackTraceElements.forEach { element ->
        Log.d("showStackTrace", element.toString())
    }
}