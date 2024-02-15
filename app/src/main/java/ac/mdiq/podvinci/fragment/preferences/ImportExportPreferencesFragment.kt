package ac.mdiq.podvinci.fragment.preferences

import android.app.Activity
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podvinci.PodVinciApp.Companion.forceRestart
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.activity.OpmlImportActivity
import ac.mdiq.podvinci.activity.PreferenceActivity
import ac.mdiq.podvinci.asynctask.DocumentFileExportWorker
import ac.mdiq.podvinci.asynctask.ExportWorker
import ac.mdiq.podvinci.core.export.ExportWriter
import ac.mdiq.podvinci.core.export.favorites.FavoritesWriter
import ac.mdiq.podvinci.core.export.html.HtmlWriter
import ac.mdiq.podvinci.core.export.opml.OpmlWriter
import ac.mdiq.podvinci.core.storage.DatabaseTransporter
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ImportExportPreferencesFragment : PreferenceFragmentCompat() {
    private val chooseOpmlExportPathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            this.chooseOpmlExportPathResult(result)
        }
    private val chooseHtmlExportPathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            this.chooseHtmlExportPathResult(result)
        }
    private val chooseFavoritesExportPathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            this.chooseFavoritesExportPathResult(result)
        }
    private val restoreDatabaseLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            this.restoreDatabaseResult(result)
        }
    private val backupDatabaseLauncher = registerForActivityResult<String, Uri>(BackupDatabase()
    ) { uri: Uri? -> this.backupDatabaseResult(uri) }
    private val chooseOpmlImportPathLauncher =
        registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? -> this.chooseOpmlImportPathResult(uri) }
    private var disposable: Disposable? = null
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
        (activity as PreferenceActivity?)!!.supportActionBar!!.setTitle(R.string.import_export_pref)
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun dateStampFilename(fname: String): String {
        return String.format(fname, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    private fun setupStorageScreen() {
        findPreference<Preference>(PREF_OPML_EXPORT)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                openExportPathPicker(Export.OPML, chooseOpmlExportPathLauncher, OpmlWriter())
                true
            }
        findPreference<Preference>(PREF_HTML_EXPORT)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                openExportPathPicker(Export.HTML, chooseHtmlExportPathLauncher, HtmlWriter())
                true
            }
        findPreference<Preference>(PREF_OPML_IMPORT)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                try {
                    chooseOpmlImportPathLauncher.launch("*/*")
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No activity found. Should never happen...")
                }
                true
            }
        findPreference<Preference>(PREF_DATABASE_IMPORT)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                importDatabase()
                true
            }
        findPreference<Preference>(PREF_DATABASE_EXPORT)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                exportDatabase()
                true
            }
        findPreference<Preference>(PREF_FAVORITE_EXPORT)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                openExportPathPicker(Export.FAVORITES, chooseFavoritesExportPathLauncher, FavoritesWriter())
                true
            }
    }

    private fun exportWithWriter(exportWriter: ExportWriter, uri: Uri?, exportType: Export) {
        val context: Context? = activity
        progressDialog!!.show()
        if (uri == null) {
            val observable = ExportWorker(exportWriter, requireContext()).exportObservable()
            disposable = observable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ output: File? ->
                    val fileUri = FileProvider.getUriForFile(context!!.applicationContext,
                        context.getString(R.string.provider_authority), output!!)
                    showExportSuccessSnackbar(fileUri, exportType.contentType)
                }, { error: Throwable -> this.showExportErrorDialog(error) }, { progressDialog!!.dismiss() })
        } else {
            val worker = DocumentFileExportWorker(exportWriter, context!!, uri)
            disposable = worker.exportObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ output: DocumentFile? ->
                    showExportSuccessSnackbar(output?.uri,
                        exportType.contentType)
                },
                    { error: Throwable -> this.showExportErrorDialog(error) },
                    { progressDialog!!.dismiss() })
        }
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
        builder.setPositiveButton(R.string.confirm_label) { dialog: DialogInterface?, which: Int ->
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
        builder.setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface?, i: Int -> forceRestart() }
        builder.show()
    }

    fun showExportSuccessSnackbar(uri: Uri?, mimeType: String?) {
        Snackbar.make(requireView(), R.string.export_success_title, Snackbar.LENGTH_LONG)
            .setAction(R.string.share_label) { v: View? ->
                IntentBuilder(requireContext())
                    .setType(mimeType)
                    .addStream(uri!!)
                    .setChooserTitle(R.string.share_label)
                    .startChooser()
            }
            .show()
    }

    private fun showExportErrorDialog(error: Throwable) {
        progressDialog!!.dismiss()
        val alert = MaterialAlertDialogBuilder(requireContext())
        alert.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
        alert.setTitle(R.string.export_error_label)
        alert.setMessage(error.message)
        alert.show()
    }

    private fun chooseOpmlExportPathResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return
        }
        val uri = result.data!!.data
        exportWithWriter(OpmlWriter(), uri, Export.OPML)
    }

    private fun chooseHtmlExportPathResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return
        }
        val uri = result.data!!.data
        exportWithWriter(HtmlWriter(), uri, Export.HTML)
    }

    private fun chooseFavoritesExportPathResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return
        }
        val uri = result.data!!.data
        exportWithWriter(FavoritesWriter(), uri, Export.FAVORITES)
    }

    private fun restoreDatabaseResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return
        }
        val uri = result.data!!.data
        progressDialog!!.show()
        disposable = Completable.fromAction { DatabaseTransporter.importBackup(uri, requireContext()) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                showDatabaseImportSuccessDialog()
                progressDialog!!.dismiss()
            }, { error: Throwable -> this.showExportErrorDialog(error) })
    }

    private fun backupDatabaseResult(uri: Uri?) {
        if (uri == null) {
            return
        }
        progressDialog!!.show()
        disposable = Completable.fromAction { DatabaseTransporter.exportToDocument(uri, requireContext()) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                showExportSuccessSnackbar(uri, "application/x-sqlite3")
                progressDialog!!.dismiss()
            }, { error: Throwable -> this.showExportErrorDialog(error) })
    }

    private fun chooseOpmlImportPathResult(uri: Uri?) {
        if (uri == null) {
            return
        }
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

    private enum class Export(val contentType: String,
                              val outputNameTemplate: String,
                              @field:StringRes val labelResId: Int
    ) {
        OPML(CONTENT_TYPE_OPML, DEFAULT_OPML_OUTPUT_NAME, R.string.opml_export_label),
        HTML(CONTENT_TYPE_HTML, DEFAULT_HTML_OUTPUT_NAME, R.string.html_export_label),
        FAVORITES(CONTENT_TYPE_HTML, DEFAULT_FAVORITES_OUTPUT_NAME, R.string.favorites_export_label)
    }

    companion object {
        private const val TAG = "ImportExPrefFragment"
        private const val PREF_OPML_EXPORT = "prefOpmlExport"
        private const val PREF_OPML_IMPORT = "prefOpmlImport"
        private const val PREF_HTML_EXPORT = "prefHtmlExport"
        private const val PREF_DATABASE_IMPORT = "prefDatabaseImport"
        private const val PREF_DATABASE_EXPORT = "prefDatabaseExport"
        private const val PREF_FAVORITE_EXPORT = "prefFavoritesExport"
        private const val DEFAULT_OPML_OUTPUT_NAME = "podvinci-feeds-%s.opml"
        private const val CONTENT_TYPE_OPML = "text/x-opml"
        private const val DEFAULT_HTML_OUTPUT_NAME = "podvinci-feeds-%s.html"
        private const val CONTENT_TYPE_HTML = "text/html"
        private const val DEFAULT_FAVORITES_OUTPUT_NAME = "podvinci-favorites-%s.html"
        private const val DATABASE_EXPORT_FILENAME = "PodVinciBackup-%s.db"
    }
}
