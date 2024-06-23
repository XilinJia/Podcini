package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.getDataFolder
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.transport.DatabaseTransporter
import ac.mdiq.podcini.storage.transport.PreferencesTransporter
import ac.mdiq.podcini.storage.transport.ExportWriter
import ac.mdiq.podcini.storage.transport.EpisodeProgressReader
import ac.mdiq.podcini.storage.transport.EpisodesProgressWriter
import ac.mdiq.podcini.storage.transport.FavoritesWriter
import ac.mdiq.podcini.storage.transport.HtmlWriter
import ac.mdiq.podcini.storage.transport.OpmlWriter
import ac.mdiq.podcini.ui.activity.OpmlImportActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class ImportExportPreferencesFragment : PreferenceFragmentCompat() {

    private val chooseOpmlExportPathLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult -> this.chooseOpmlExportPathResult(result) }
    private val chooseHtmlExportPathLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult -> this.chooseHtmlExportPathResult(result) }
    private val chooseFavoritesExportPathLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult -> this.chooseFavoritesExportPathResult(result) }
    private val chooseProgressExportPathLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult -> this.chooseProgressExportPathResult(result) }
    private val restoreProgressLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult -> this.restoreProgressResult(result) }
    private val restoreDatabaseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult -> this.restoreDatabaseResult(result) }
    private val backupDatabaseLauncher = registerForActivityResult<String, Uri>(BackupDatabase()) { uri: Uri? -> this.backupDatabaseResult(uri) }
    private val chooseOpmlImportPathLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) {
        uri: Uri? -> this.chooseOpmlImportPathResult(uri) }

    private val restorePreferencesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult -> this.restorePreferencesResult(result) }
    private val backupPreferencesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val data: Uri? = it.data?.data
            if (data != null) PreferencesTransporter.exportToDocument(data, requireContext())
        }
    }

    private var progressDialog: ProgressDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_import_export)
        setupStorageScreen()
        progressDialog = ProgressDialog(context)
        progressDialog!!.isIndeterminate = true
        progressDialog!!.setMessage(requireContext().getString(R.string.please_wait))
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.import_export_pref)
    }

//    override fun onStop() {
//        super.onStop()
//    }

    private fun dateStampFilename(fname: String): String {
        return String.format(fname, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    private fun setupStorageScreen() {
        findPreference<Preference>(PREF_OPML_EXPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openExportPathPicker(Export.OPML, chooseOpmlExportPathLauncher, OpmlWriter())
            true
        }
        findPreference<Preference>(PREF_HTML_EXPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openExportPathPicker(Export.HTML, chooseHtmlExportPathLauncher, HtmlWriter())
            true
        }
        findPreference<Preference>(PREF_PROGRESS_EXPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openExportPathPicker(Export.PROGRESS, chooseProgressExportPathLauncher, EpisodesProgressWriter())
            true
        }
        findPreference<Preference>(PREF_PROGRESS_IMPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            importEpisodeProgress()
            true
        }
        findPreference<Preference>(PREF_OPML_IMPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            try {
                chooseOpmlImportPathLauncher.launch("*/*")
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No activity found. Should never happen...")
            }
            true
        }
        findPreference<Preference>(PREF_DATABASE_IMPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            importDatabase()
            true
        }
        findPreference<Preference>(PREF_DATABASE_EXPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            exportDatabase()
            true
        }
        findPreference<Preference>(PREF_PREFERENCES_IMPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            importPreferences()
            true
        }
        findPreference<Preference>(PREF_PREFERENCES_EXPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            exportPreferences()
            true
        }

        findPreference<Preference>(PREF_FAVORITE_EXPORT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openExportPathPicker(Export.FAVORITES, chooseFavoritesExportPathLauncher, FavoritesWriter())
            true
        }
    }

    private fun exportWithWriter(exportWriter: ExportWriter, uri: Uri?, exportType: Export) {
        val context: Context? = activity
        progressDialog!!.show()
        if (uri == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val output = ExportWorker(exportWriter, requireContext()).exportFile()
                    withContext(Dispatchers.Main) {
                        val fileUri = FileProvider.getUriForFile(context!!.applicationContext, context.getString(R.string.provider_authority), output!!)
                        showExportSuccessSnackbar(fileUri, exportType.contentType)
                    }
                } catch (e: Exception) {
                     showExportErrorDialog(e)
                } finally {
                    progressDialog!!.dismiss()
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val worker = DocumentFileExportWorker(exportWriter, context!!, uri)
                try {
                    val output = worker.exportFile()
                    withContext(Dispatchers.Main) {
                        showExportSuccessSnackbar(output?.uri, exportType.contentType)
                    }
                } catch (e: Exception) {
                    showExportErrorDialog(e)
                } finally {
                    progressDialog!!.dismiss()
                }
            }
        }
    }

    private fun exportPreferences() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        backupPreferencesLauncher.launch(intent)
    }

    private fun importPreferences() {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.preferences_import_label)
        builder.setMessage(R.string.preferences_import_warning)

        // add a button
        builder.setNegativeButton(R.string.no, null)
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            restorePreferencesLauncher.launch(intent)
        }

        // create and show the alert dialog
        builder.show()
    }

    private fun exportDatabase() {
        backupDatabaseLauncher.launch(dateStampFilename(DATABASE_EXPORT_FILENAME))
    }

    private fun importDatabase() {
        // setup the alert builder
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.database_import_label)
        builder.setMessage(R.string.database_import_warning)

        // add a button
        builder.setNegativeButton(R.string.no, null)
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.setType("*/*")
            restoreDatabaseLauncher.launch(intent)
        }

        // create and show the alert dialog
        builder.show()
    }

    private fun showDatabaseImportSuccessDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.successful_import_label)
        builder.setMessage(R.string.import_ok)
        builder.setCancelable(false)
        builder.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> forceRestart() }
        builder.show()
    }

    private fun showExportSuccessSnackbar(uri: Uri?, mimeType: String?) {
        Snackbar.make(requireView(), R.string.export_success_title, Snackbar.LENGTH_LONG)
            .setAction(R.string.share_label) { IntentBuilder(requireContext()).setType(mimeType).addStream(uri!!).setChooserTitle(R.string.share_label).startChooser() }
            .show()
    }

    private fun showExportErrorDialog(error: Throwable) {
        progressDialog!!.dismiss()
        val alert = MaterialAlertDialogBuilder(requireContext())
        alert.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        alert.setTitle(R.string.export_error_label)
        alert.setMessage(error.message)
        alert.show()
    }

    private fun importEpisodeProgress() {
        // setup the alert builder
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.progress_import_label)
        builder.setMessage(R.string.progress_import_warning)

        // add a button
        builder.setNegativeButton(R.string.no, null)
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.setType("*/*")
            restoreProgressLauncher.launch(intent)
        }
        // create and show the alert dialog
        builder.show()
    }

    private fun chooseProgressExportPathResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data == null) return
        val uri = result.data!!.data
        exportWithWriter(EpisodesProgressWriter(), uri, Export.PROGRESS)
    }

    private fun chooseOpmlExportPathResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data == null) return
        val uri = result.data!!.data
        exportWithWriter(OpmlWriter(), uri, Export.OPML)
    }

    private fun chooseHtmlExportPathResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data == null) return
        val uri = result.data!!.data
        exportWithWriter(HtmlWriter(), uri, Export.HTML)
    }

    private fun chooseFavoritesExportPathResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data == null) return
        val uri = result.data!!.data
        exportWithWriter(FavoritesWriter(), uri, Export.FAVORITES)
    }

    private fun restoreProgressResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data?.data == null) return
        val uri = result.data!!.data!!
        progressDialog!!.show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    EpisodeProgressReader.readDocument(reader)
                    reader.close()
                }
                withContext(Dispatchers.Main) {
                    showDatabaseImportSuccessDialog()
                    progressDialog!!.dismiss()
                }
            } catch (e: Throwable) {
                showExportErrorDialog(e)
            }
        }
    }

    private fun restoreDatabaseResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data == null) return
        val uri = result.data!!.data
        progressDialog!!.show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DatabaseTransporter.importBackup(uri, requireContext())
                }
                withContext(Dispatchers.Main) {
                    showDatabaseImportSuccessDialog()
                    progressDialog!!.dismiss()
                }
            } catch (e: Throwable) {
                showExportErrorDialog(e)
            }
        }
    }

    private fun restorePreferencesResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK || result.data?.data == null) return
        val uri = result.data!!.data!!
        progressDialog!!.show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PreferencesTransporter.importBackup(uri, requireContext())
                }
                withContext(Dispatchers.Main) {
                    showDatabaseImportSuccessDialog()
                    progressDialog!!.dismiss()
                }
            } catch (e: Throwable) {
                showExportErrorDialog(e)
            }
        }
    }

    private fun backupDatabaseResult(uri: Uri?) {
        if (uri == null) return
        progressDialog!!.show()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    DatabaseTransporter.exportToDocument(uri, requireContext())
                }
                withContext(Dispatchers.Main) {
                    showExportSuccessSnackbar(uri, "application/x-sqlite3")
                    progressDialog!!.dismiss()
                }
            } catch (e: Throwable) {
                showExportErrorDialog(e)
            }
        }
    }

    private fun chooseOpmlImportPathResult(uri: Uri?) {
        if (uri == null) return
        val intent = Intent(context, OpmlImportActivity::class.java)
        intent.setData(uri)
        startActivity(intent)
    }

    private fun openExportPathPicker(exportType: Export, result: ActivityResultLauncher<Intent>, writer: ExportWriter) {
        val title = dateStampFilename(exportType.outputNameTemplate)

        val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(exportType.contentType)
            .putExtra(Intent.EXTRA_TITLE, title)

        // Creates an implicit intent to launch a file manager which lets
        // the user choose a specific directory to export to.
        try {
            result.launch(intentPickAction)
            return
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity found. Should never happen...")
        }

        // If we are using a SDK lower than API 21 or the implicit intent failed
        // fallback to the legacy export process
        exportWithWriter(writer, null, exportType)
    }

    private class BackupDatabase : CreateDocument() {
        override fun createIntent(context: Context, input: String): Intent {
            return super.createIntent(context, input)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/x-sqlite3")
        }
    }

    private enum class Export(val contentType: String, val outputNameTemplate: String, @field:StringRes val labelResId: Int) {
        OPML(CONTENT_TYPE_OPML, DEFAULT_OPML_OUTPUT_NAME, R.string.opml_export_label),
        HTML(CONTENT_TYPE_HTML, DEFAULT_HTML_OUTPUT_NAME, R.string.html_export_label),
        FAVORITES(CONTENT_TYPE_HTML, DEFAULT_FAVORITES_OUTPUT_NAME, R.string.favorites_export_label),
        PROGRESS(CONTENT_TYPE_PROGRESS, DEFAULT_PROGRESS_OUTPUT_NAME, R.string.progress_export_label),
    }

    class DocumentFileExportWorker(private val exportWriter: ExportWriter, private val context: Context, private val outputFileUri: Uri) {

        suspend fun exportFile(): DocumentFile {
            return withContext(Dispatchers.IO) {
                val output = DocumentFile.fromSingleUri(context, outputFileUri)
                var outputStream: OutputStream? = null
                var writer: OutputStreamWriter? = null
                try {
                    if (output == null) throw IOException()
                    val uri = output.uri
                    outputStream = context.contentResolver.openOutputStream(uri, "wt")
                    if (outputStream == null) throw IOException()
                    writer = OutputStreamWriter(outputStream, Charset.forName("UTF-8"))
                    exportWriter.writeDocument(getFeedList(), writer, context)
                    output
                } catch (e: IOException) {
                    throw e
                } finally {
                    if (writer != null) {
                        try {
                            writer.close()
                        } catch (e: IOException) {
                            throw e
                        }
                    }
                    if (outputStream != null) {
                        try {
                            outputStream.close()
                        } catch (e: IOException) {
                            throw e
                        }
                    }
                }
            }
        }
    }

    /**
     * Writes an OPML file into the export directory in the background.
     */
    class ExportWorker private constructor(private val exportWriter: ExportWriter, private val output: File, private val context: Context) {

        constructor(exportWriter: ExportWriter, context: Context) : this(exportWriter, File(getDataFolder(EXPORT_DIR),
            DEFAULT_OUTPUT_NAME + "." + exportWriter.fileExtension()), context)

        suspend fun exportFile(): File? {
            return withContext(Dispatchers.IO) {
                if (output.exists()) {
                    val success = output.delete()
                    Log.w(TAG, "Overwriting previously exported file: $success")
                }

                var writer: OutputStreamWriter? = null
                try {
                    writer = OutputStreamWriter(FileOutputStream(output), Charset.forName("UTF-8"))
                    exportWriter.writeDocument(getFeedList(), writer, context)
                    output // return the output file
                } catch (e: IOException) {
                    Log.e(TAG, "Error during file export", e)
                    null // return null in case of error
                } finally {
                    writer?.close()
                }
            }
        }

        companion object {
            private const val EXPORT_DIR = "export/"
            private val TAG: String = ExportWorker::class.simpleName ?: "Anonymous"
            private const val DEFAULT_OUTPUT_NAME = "podcini-feeds"
        }
    }

    companion object {
        private val TAG: String = ImportExportPreferencesFragment::class.simpleName ?: "Anonymous"
        private const val PREF_OPML_EXPORT = "prefOpmlExport"
        private const val PREF_OPML_IMPORT = "prefOpmlImport"
        private const val PREF_PROGRESS_EXPORT = "prefProgressExport"
        private const val PREF_PROGRESS_IMPORT = "prefProgressImport"
        private const val PREF_HTML_EXPORT = "prefHtmlExport"
        private const val PREF_PREFERENCES_IMPORT = "prefPrefImport"
        private const val PREF_PREFERENCES_EXPORT = "prefPrefExport"
        private const val PREF_DATABASE_IMPORT = "prefDatabaseImport"
        private const val PREF_DATABASE_EXPORT = "prefDatabaseExport"
        private const val PREF_FAVORITE_EXPORT = "prefFavoritesExport"
        private const val DEFAULT_OPML_OUTPUT_NAME = "podcini-feeds-%s.opml"
        private const val CONTENT_TYPE_OPML = "text/x-opml"
        private const val DEFAULT_HTML_OUTPUT_NAME = "podcini-feeds-%s.html"
        private const val CONTENT_TYPE_HTML = "text/html"
        private const val DEFAULT_FAVORITES_OUTPUT_NAME = "podcini-favorites-%s.html"
        private const val CONTENT_TYPE_PROGRESS = "text/x-json"
        private const val DEFAULT_PROGRESS_OUTPUT_NAME = "podcini-progress-%s.json"
        private const val DATABASE_EXPORT_FILENAME = "PodciniBackup-%s.realm"
    }
}
