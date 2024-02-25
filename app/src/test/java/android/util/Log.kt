package android.util

import java.io.PrintWriter
import java.io.StringWriter

/**
 * A stub for [android.util.Log] to be used in unit tests.
 *
 * It outputs the log statements to standard error.
 */
object Log {
    /**
     * Priority constant for the println method; use Log.v.
     */
    const val VERBOSE: Int = 2

    /**
     * Priority constant for the println method; use Log.d.
     */
    const val DEBUG: Int = 3

    /**
     * Priority constant for the println method; use Log.i.
     */
    const val INFO: Int = 4

    /**
     * Priority constant for the println method; use Log.w.
     */
    const val WARN: Int = 5

    /**
     * Priority constant for the println method; use Log.e.
     */
    const val ERROR: Int = 6

    /**
     * Priority constant for the println method.
     */
    const val ASSERT: Int = 7

    /**
     * Send a [.VERBOSE] log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    fun v(tag: String, msg: String): Int {
        return println_native(LOG_ID_MAIN, VERBOSE, tag, msg)
    }

    /**
     * Send a [.VERBOSE] log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    fun v(tag: String, msg: String, tr: Throwable?): Int {
        return printlns(LOG_ID_MAIN, VERBOSE, tag, msg, tr)
    }

    /**
     * Send a [.DEBUG] log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    fun d(tag: String, msg: String): Int {
        return println_native(LOG_ID_MAIN, DEBUG, tag, msg)
    }

    /**
     * Send a [.DEBUG] log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    fun d(tag: String, msg: String, tr: Throwable?): Int {
        return printlns(LOG_ID_MAIN, DEBUG, tag, msg, tr)
    }

    /**
     * Send an [.INFO] log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    fun i(tag: String, msg: String): Int {
        return println_native(LOG_ID_MAIN, INFO, tag, msg)
    }

    /**
     * Send a [.INFO] log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    fun i(tag: String, msg: String, tr: Throwable?): Int {
        return printlns(LOG_ID_MAIN, INFO, tag, msg, tr)
    }

    /**
     * Send a [.WARN] log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    fun w(tag: String, msg: String): Int {
        return println_native(LOG_ID_MAIN, WARN, tag, msg)
    }

    /**
     * Send a [.WARN] log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    fun w(tag: String, msg: String, tr: Throwable?): Int {
        return printlns(LOG_ID_MAIN, WARN, tag, msg, tr)
    }

    /**
     * Checks to see whether or not a log for the specified tag is loggable at the specified level.
     *
     * @return true in all cases (for unit test environment)
     */
    fun isLoggable(tag: String?, level: Int): Boolean {
        return true
    }

    /*
     * Send a {@link #WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param tr An exception to log
     */
    fun w(tag: String, tr: Throwable?): Int {
        return printlns(LOG_ID_MAIN, WARN, tag, "", tr)
    }

    /**
     * Send an [.ERROR] log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    fun e(tag: String, msg: String): Int {
        return println_native(LOG_ID_MAIN, ERROR, tag, msg)
    }

    /**
     * Send a [.ERROR] log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     * the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    fun e(tag: String, msg: String, tr: Throwable?): Int {
        return printlns(LOG_ID_MAIN, ERROR, tag, msg, tr)
    }

    /**
     * What a Terrible Failure: Report a condition that should never happen.
     * The error will always be logged at level ASSERT with the call stack.
     * Depending on system configuration, a report may be added to the
     * [android.os.DropBoxManager] and/or the process may be terminated
     * immediately with an error dialog.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     */
    fun wtf(tag: String, msg: String): Int {
        return wtf(LOG_ID_MAIN, tag, msg, null, false, false)
    }

    /**
     * Like [.wtf], but also writes to the log the full
     * call stack.
     * @hide
     */
    fun wtfStack(tag: String, msg: String): Int {
        return wtf(LOG_ID_MAIN, tag, msg, null, true, false)
    }

    /**
     * What a Terrible Failure: Report an exception that should never happen.
     * Similar to [.wtf], with an exception to log.
     * @param tag Used to identify the source of a log message.
     * @param tr An exception to log.
     */
    fun wtf(tag: String, tr: Throwable): Int {
        return wtf(LOG_ID_MAIN, tag, tr.message?:"", tr, false, false)
    }

    /**
     * What a Terrible Failure: Report an exception that should never happen.
     * Similar to [.wtf], with a message as well.
     * @param tag Used to identify the source of a log message.
     * @param msg The message you would like logged.
     * @param tr An exception to log.  May be null.
     */
    fun wtf(tag: String, msg: String, tr: Throwable?): Int {
        return wtf(LOG_ID_MAIN, tag, msg, tr, false, false)
    }

    /**
     * Priority Constant for wtf.
     * Added for this custom Log implementation, not in android sources.
     */
    private const val WTF = 8

    fun wtf(logId: Int, tag: String, msg: String, tr: Throwable?, localStack: Boolean,
            system: Boolean
    ): Int {
        return printlns(LOG_ID_MAIN, WTF, tag, msg, tr)
    }

    private const val LOG_ID_MAIN = 0

    private val PRIORITY_ABBREV = arrayOf("0", "1", "V", "D", "I", "W", "E", "A", "WTF")

    private fun println_native(bufID: Int, priority: Int, tag: String, msg: String): Int {
        val res: String = PRIORITY_ABBREV[priority] + "/" + tag + " " + msg + System.lineSeparator()
        System.err.print(res)
        return res.length
    }

    private fun printlns(bufID: Int, priority: Int, tag: String, msg: String,
                         tr: Throwable?
    ): Int {
        val trSW = StringWriter()
        if (tr != null) {
            trSW.append(" , Exception: ")
            val trPW = PrintWriter(trSW)
            tr.printStackTrace(trPW)
            trPW.flush()
        }
        return println_native(bufID, priority, tag, msg + trSW.toString())
    }
}
