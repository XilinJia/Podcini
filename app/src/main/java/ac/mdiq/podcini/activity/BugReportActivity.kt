package ac.mdiq.podcini.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.core.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.error.CrashReportWriter
import ac.mdiq.podcini.storage.preferences.UserPreferences.getDataFolder
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.Charset

/**
 * Displays the 'crash report' screen
 */
class BugReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        setContentView(R.layout.bug_report)

        var stacktrace = "No crash report recorded"
        try {
            val crashFile = CrashReportWriter.file
            if (crashFile.exists()) {
                stacktrace = IOUtils.toString(FileInputStream(crashFile), Charset.forName("UTF-8"))
            } else {
                Log.d(TAG, stacktrace)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val crashDetailsTextView = findViewById<TextView>(R.id.crash_report_logs)
        crashDetailsTextView.text = """
            ${CrashReportWriter.systemInfo}
            
            $stacktrace
            """.trimIndent()

        findViewById<View>(R.id.btn_open_bug_tracker).setOnClickListener { v: View? ->
            openInBrowser(
                this@BugReportActivity, "https://github.com/XilinJia/Podcini/issues")
        }

        findViewById<View>(R.id.btn_copy_log).setOnClickListener { v: View? ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.bug_report_title), crashDetailsTextView.text)
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < 32) {
                Snackbar.make(findViewById(android.R.id.content), R.string.copied_to_clipboard,
                    Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bug_report_options, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.export_logcat) {
            val alertBuilder = MaterialAlertDialogBuilder(this)
            alertBuilder.setMessage(R.string.confirm_export_log_dialog_message)
            alertBuilder.setPositiveButton(R.string.confirm_label) { dialog: DialogInterface, which: Int ->
                exportLog()
                dialog.dismiss()
            }
            alertBuilder.setNegativeButton(R.string.cancel_label, null)
            alertBuilder.show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportLog() {
        try {
            val filename = File(getDataFolder(null), "full-logs.txt")
            val cmd = "logcat -d -f " + filename.absolutePath
            Runtime.getRuntime().exec(cmd)
            //share file
            try {
                val authority = getString(R.string.provider_authority)
                val fileUri = FileProvider.getUriForFile(this, authority, filename)

                IntentBuilder(this)
                    .setType("text/*")
                    .addStream(fileUri)
                    .setChooserTitle(R.string.share_file_label)
                    .startChooser()
            } catch (e: Exception) {
                e.printStackTrace()
                val strResId = R.string.log_file_share_exception
                Snackbar.make(findViewById(android.R.id.content), strResId, Snackbar.LENGTH_LONG)
                    .show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Snackbar.make(findViewById(android.R.id.content), e.message!!, Snackbar.LENGTH_LONG).show()
        }
    }


    companion object {
        private const val TAG = "BugReportActivity"
    }
}
