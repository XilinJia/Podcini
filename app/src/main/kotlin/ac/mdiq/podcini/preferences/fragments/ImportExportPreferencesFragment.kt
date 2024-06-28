package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.preferences.ExportWriter
import ac.mdiq.podcini.preferences.UserPreferences.getDataFolder
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.preferences.OpmlTransporter.*
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeUtil.hasAlmostEnded
import ac.mdiq.podcini.storage.utils.SortOrder
import ac.mdiq.podcini.ui.activity.OpmlImportActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.util.Logd
import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.io.*
import java.nio.channels.FileChannel
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
                        showExportSuccessSnackbar(output.uri, exportType.contentType)
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
                    if (writer != null) try { writer.close() } catch (e: IOException) { throw e }
                    if (outputStream != null) try { outputStream.close() } catch (e: IOException) { throw e }
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
                    Logd(TAG, "Overwriting previously exported file: $success")
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

    object PreferencesTransporter {
        private val TAG: String = PreferencesTransporter::class.simpleName ?: "Anonymous"
        @Throws(IOException::class)
        fun exportToDocument(uri: Uri, context: Context) {
            try {
                val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
                val exportSubDir = chosenDir.createDirectory("Podcini-Prefs") ?: throw IOException("Error creating subdirectory Podcini-Prefs")
                val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file ->
                    file.name.startsWith("shared_prefs")
                }?.firstOrNull()
                if (sharedPreferencesDir != null) {
                    sharedPreferencesDir.listFiles()!!.forEach { file ->
                        val destFile = exportSubDir.createFile("text/xml", file.name)
                        if (destFile != null) copyFile(file, destFile, context)
                    }
                } else {
                    Log.e("Error", "shared_prefs directory not found")
                }
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally { }
        }
        private fun copyFile(sourceFile: File, destFile: DocumentFile, context: Context) {
            try {
                val inputStream = FileInputStream(sourceFile)
                val outputStream = context.contentResolver.openOutputStream(destFile.uri)
                if (outputStream != null) copyStream(inputStream, outputStream)
                inputStream.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("Error", "Error copying file: $e")
                throw e
            }
        }
        private fun copyFile(sourceFile: DocumentFile, destFile: File, context: Context) {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceFile.uri)
                val outputStream = FileOutputStream(destFile)
                if (inputStream != null) copyStream(inputStream, outputStream)
                inputStream?.close()
                outputStream.close()
            } catch (e: IOException) {
                Log.e("Error", "Error copying file: $e")
                throw e
            }
        }
        private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        }
        @Throws(IOException::class)
        fun importBackup(uri: Uri, context: Context) {
            try {
                val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
                val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file ->
                    file.name.startsWith("shared_prefs")
                }?.firstOrNull()
                if (sharedPreferencesDir != null) {
                    sharedPreferencesDir.listFiles()?.forEach { file ->
//                    val prefName = file.name.substring(0, file.name.lastIndexOf('.'))
                        file.delete()
                    }
                } else Log.e("Error", "shared_prefs directory not found")
                val files = exportedDir.listFiles()
                var hasPodciniRPrefs = false
                for (file in files) {
                    if (file?.isFile == true && file.name?.endsWith(".xml") == true && file.name!!.contains("podcini.R")) {
                        hasPodciniRPrefs = true
                        break
                    }
                }
                for (file in files) {
                    if (file?.isFile == true && file.name?.endsWith(".xml") == true) {
                        var destName = file.name!!
//                    contains info on existing widgets, no need to import
                        if (destName.contains("PlayerWidgetPrefs")) continue
//                  for importing from Podcini version 5 and below
                        if (!hasPodciniRPrefs) {
                            when {
                                destName.contains("podcini") -> destName = destName.replace("podcini", "podcini.R")
                                destName.contains("EpisodeItemListRecyclerView") -> destName = destName.replace("EpisodeItemListRecyclerView", "EpisodesRecyclerView")
                            }
                        }
                        when {
//                  for debug version importing release version
                            BuildConfig.DEBUG && !destName.contains(".debug") -> destName = destName.replace("podcini.R", "podcini.R.debug")
//                  for release version importing debug version
                            !BuildConfig.DEBUG && destName.contains(".debug") -> destName = destName.replace(".debug", "")
                        }
                        val destFile = File(sharedPreferencesDir, destName)
                        copyFile(file, destFile, context)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally { }

        }
    }

    object DatabaseTransporter {
        private val TAG: String = DatabaseTransporter::class.simpleName ?: "Anonymous"
        @Throws(IOException::class)
        fun exportToDocument(uri: Uri?, context: Context) {
            var pfd: ParcelFileDescriptor? = null
            var fileOutputStream: FileOutputStream? = null
            try {
                pfd = context.contentResolver.openFileDescriptor(uri!!, "wt")
                fileOutputStream = FileOutputStream(pfd!!.fileDescriptor)
                exportToStream(fileOutputStream, context)
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally {
                IOUtils.closeQuietly(fileOutputStream)
                if (pfd != null) try { pfd.close() } catch (e: IOException) { Logd(TAG, "Unable to close ParcelFileDescriptor") }
            }
        }
        @Throws(IOException::class)
        fun exportToStream(outFileStream: FileOutputStream, context: Context) {
            var src: FileChannel? = null
            var dst: FileChannel? = null
            try {
                val realmPath = realm.configuration.path
                Logd(TAG, "exportToStream realmPath: $realmPath")
                val currentDB = File(realmPath)
                if (currentDB.exists()) {
                    src = FileInputStream(currentDB).channel
                    dst = outFileStream.channel
                    val srcSize = src.size()
                    dst.transferFrom(src, 0, srcSize)
                    val newDstSize = dst.size()
                    if (newDstSize != srcSize)
                        throw IOException(String.format("Unable to write entire database. Expected to write %s, but wrote %s.", Formatter.formatShortFileSize(context, srcSize), Formatter.formatShortFileSize(context, newDstSize)))
                } else {
                    throw IOException("Can not access current database")
                }
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally {
                IOUtils.closeQuietly(src)
                IOUtils.closeQuietly(dst)
            }
        }
        @Throws(IOException::class)
        fun importBackup(inputUri: Uri?, context: Context) {
            val TEMP_DB_NAME = "temp.realm"
            var inputStream: InputStream? = null
            try {
                val tempDB = context.getDatabasePath(TEMP_DB_NAME)
                inputStream = context.contentResolver.openInputStream(inputUri!!)
                FileUtils.copyInputStreamToFile(inputStream, tempDB)
                val realmPath = realm.configuration.path
                val currentDB = File(realmPath)
                val success = currentDB.delete()
                if (!success) throw IOException("Unable to delete old database")
                FileUtils.moveFile(tempDB, currentDB)
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally {
                IOUtils.closeQuietly(inputStream)
            }
        }
    }

    /** Reads OPML documents.  */
    object EpisodeProgressReader {
        private const val TAG = "EpisodeProgressReader"
        @OptIn(UnstableApi::class)
        fun readDocument(reader: Reader) {
            val jsonString = reader.readText()
            val jsonArray = JSONArray(jsonString)
            val remoteActions = mutableListOf<EpisodeAction>()
            for (i in 0 until jsonArray.length()) {
                val jsonAction = jsonArray.getJSONObject(i)
                Logd(TAG, "Loaded EpisodeActions message: $i $jsonAction")
                val action = readFromJsonObject(jsonAction) ?: continue
                remoteActions.add(action)
            }
            if (remoteActions.isEmpty()) return
            val updatedItems: MutableList<Episode> = ArrayList()
            for (action in remoteActions) {
                Logd(TAG, "processing action: $action")
                val result = processEpisodeAction(action) ?: continue
                updatedItems.add(result.second)
            }
//        loadAdditionalFeedItemListData(updatedItems)
//        need to do it the sync way
            for (episode in updatedItems) upsertBlk(episode) {}
            Logd(TAG, "Parsing finished.")
            return
        }
        private fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
            val guid = if (isValidGuid(action.guid)) action.guid else null
            val feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"")
            if (feedItem == null) {
                Logd(TAG, "Unknown feed item: $action")
                return null
            }
            if (feedItem.media == null) {
                Logd(TAG, "Feed item has no media: $action")
                return null
            }
            var idRemove = 0L
            feedItem.media!!.setPosition(action.position * 1000)
            feedItem.media!!.setLastPlayedTime(action.timestamp!!.time)
            feedItem.isFavorite = action.isFavorite
            feedItem.playState = action.playState
            if (hasAlmostEnded(feedItem.media!!)) {
                Logd(TAG, "Marking as played: $action")
                feedItem.setPlayed(true)
                feedItem.media!!.setPosition(0)
                idRemove = feedItem.id
            } else Logd(TAG, "Setting position: $action")
            return Pair(idRemove, feedItem)
        }
    }

    /** Writes saved favorites to file.  */
    class EpisodesProgressWriter : ExportWriter {
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
            Logd(TAG, "Starting to write document")
            val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
            val pausedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.PAUSED), SortOrder.DATE_NEW_OLD)
            val readItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.PLAYED), SortOrder.DATE_NEW_OLD)
            val favoriteItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.IS_FAVORITE), SortOrder.DATE_NEW_OLD)
            val comItems = mutableSetOf<Episode>()
            comItems.addAll(pausedItems)
            comItems.addAll(readItems)
            comItems.addAll(favoriteItems)
            Logd(TAG, "Save state for all " + comItems.size + " played episodes")
            for (item in comItems) {
                val media = item.media ?: continue
                val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                    .timestamp(Date(media.getLastPlayedTime()))
                    .started(media.getPosition() / 1000)
                    .position(media.getPosition() / 1000)
                    .total(media.getDuration() / 1000)
                    .isFavorite(item.isFavorite)
                    .playState(item.playState)
                    .build()
                queuedEpisodeActions.add(played)
            }
            if (queuedEpisodeActions.isNotEmpty()) {
                try {
                    Logd(TAG, "Saving ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                    val list = JSONArray()
                    for (episodeAction in queuedEpisodeActions) {
                        val obj = episodeAction.writeToJsonObject()
                        if (obj != null) {
                            Logd(TAG, "saving EpisodeAction: $obj")
                            list.put(obj)
                        }
                    }
                    writer?.write(list.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw SyncServiceException(e)
                }
            }
            Logd(TAG, "Finished writing document")
        }
        override fun fileExtension(): String {
            return "json"
        }
        companion object {
            private const val TAG = "EpisodesProgressWriter"
        }
    }

    /** Writes saved favorites to file.  */
    class FavoritesWriter : ExportWriter {
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
            Logd(TAG, "Starting to write document")
            val templateStream = context!!.assets.open("html-export-template.html")
            var template = IOUtils.toString(templateStream, UTF_8)
            template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
            val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
            val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)
            val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
            val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)
            val allFavorites = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.IS_FAVORITE), SortOrder.DATE_NEW_OLD)
            val favoritesByFeed = buildFeedMap(allFavorites)
            writer!!.append(templateParts[0])
            for (feedId in favoritesByFeed.keys) {
                val favorites: List<Episode> = favoritesByFeed[feedId]!!
                writer.append("<li><div>\n")
                writeFeed(writer, favorites[0].feed, feedTemplate)
                writer.append("<ul>\n")
                for (item in favorites) writeFavoriteItem(writer, item, favTemplate)
                writer.append("</ul></div></li>\n")
            }
            writer.append(templateParts[1])
            Logd(TAG, "Finished writing document")
        }
        /**
         * Group favorite episodes by feed, sorting them by publishing date in descending order.
         * @param favoritesList `List` of all favorite episodes.
         * @return A `Map` favorite episodes, keyed by feed ID.
         */
        private fun buildFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
            val feedMap: MutableMap<Long, MutableList<Episode>> = TreeMap()
            for (item in favoritesList) {
                var feedEpisodes = feedMap[item.feedId]
                if (feedEpisodes == null) {
                    feedEpisodes = ArrayList()
                    if (item.feedId != null) feedMap[item.feedId!!] = feedEpisodes
                }
                feedEpisodes.add(item)
            }
            return feedMap
        }
        @Throws(IOException::class)
        private fun writeFeed(writer: Writer?, feed: Feed?, feedTemplate: String) {
            val feedInfo = feedTemplate
                .replace("{FEED_IMG}", feed!!.imageUrl!!)
                .replace("{FEED_TITLE}", feed.title!!)
                .replace("{FEED_LINK}", feed.link!!)
                .replace("{FEED_WEBSITE}", feed.downloadUrl!!)
            writer!!.append(feedInfo)
        }
        @Throws(IOException::class)
        private fun writeFavoriteItem(writer: Writer?, item: Episode, favoriteTemplate: String) {
            var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
            favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
            else favItem.replace("{FAV_WEBSITE}", "")
            favItem =
                if (item.media != null && item.media!!.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.media!!.downloadUrl!!)
                else favItem.replace("{FAV_MEDIA}", "")
            writer!!.append(favItem)
        }
        override fun fileExtension(): String {
            return "html"
        }
        companion object {
            private val TAG: String = FavoritesWriter::class.simpleName ?: "Anonymous"
            private const val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
            private const val FEED_TEMPLATE = "html-export-feed-template.html"
            private const val UTF_8 = "UTF-8"
        }
    }

    /** Writes HTML documents.  */
    class HtmlWriter : ExportWriter {
        /**
         * Takes a list of feeds and a writer and writes those into an HTML document.
         */
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
            Logd(TAG, "Starting to write document")

            val templateStream = context!!.assets.open("html-export-template.html")
            var template = IOUtils.toString(templateStream, "UTF-8")
            template = template.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
            val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            writer!!.append(templateParts[0])
            for (feed in feeds!!) {
                writer.append("<li><div><img src=\"")
                writer.append(feed!!.imageUrl)
                writer.append("\" /><p>")
                writer.append(feed.title)
                writer.append(" <span><a href=\"")
                writer.append(feed.link)
                writer.append("\">Website</a> • <a href=\"")
                writer.append(feed.downloadUrl)
                writer.append("\">Feed</a></span></p></div></li>\n")
            }
            writer.append(templateParts[1])
            Logd(TAG, "Finished writing document")
        }

        override fun fileExtension(): String {
            return "html"
        }

        companion object {
            private val TAG: String = HtmlWriter::class.simpleName ?: "Anonymous"
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
