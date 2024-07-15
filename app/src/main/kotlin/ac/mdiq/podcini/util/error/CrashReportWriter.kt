package ac.mdiq.podcini.util.error

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.utils.FilesUtils.getDataFolder
import android.os.Build
import android.util.Log
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashReportWriter : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        write(ex)
        defaultHandler.uncaughtException(thread, ex)
    }

    companion object {
        private val TAG: String = CrashReportWriter::class.simpleName ?: "Anonymous"

        @JvmStatic
        val file: File
            get() = File(getDataFolder(null), "crash-report.log")

        @JvmStatic
        fun write(exception: Throwable) {
            val path = file
            var out: PrintWriter? = null
            try {
                out = PrintWriter(path, "UTF-8")
                out.println("## Crash info")
                out.println("Time: " + SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
                out.println("Podcini version: " + BuildConfig.VERSION_NAME)
                out.println()
                out.println("## StackTrace")
                out.println("```")
                exception.printStackTrace(out)
                out.println("```")
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
            } finally {
                IOUtils.closeQuietly(out)
            }
        }

        val systemInfo: String
            get() = """
                 ## Environment
                 Android version: ${Build.VERSION.RELEASE}
                 OS version: ${System.getProperty("os.version")}
                 Podcini version: ${BuildConfig.VERSION_NAME}
                 Model: ${Build.MODEL}
                 Device: ${Build.DEVICE}
                 Product: ${Build.PRODUCT}
                 """.trimIndent()
    }
}
