package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.*
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.dateStampFilename
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

@Composable
fun ImportExportPreferencesScreen(activity: PreferenceActivity) {
    val TAG = "ImportExportPreferencesScreen"
    val backupDirName = "Podcini-Backups"
    val prefsDirName = "Podcini-Prefs"
    val mediaFilesDirName = "Podcini-MediaFiles"

    var showProgress by remember { mutableStateOf(false) }
    fun isJsonFile(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.endsWith(".json", ignoreCase = true)
    }
    fun isRealmFile(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.trim().endsWith(".realm", ignoreCase = true)
    }
    fun isComboDir(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.contains(backupDirName, ignoreCase = true)
    }
    fun showExportSuccessSnackbar(uri: Uri?, mimeType: String?) {
        Snackbar.make(activity.findViewById(android.R.id.content), R.string.export_success_title, Snackbar.LENGTH_LONG)
            .setAction(R.string.share_label) { IntentBuilder(activity).setType(mimeType).addStream(uri!!).setChooserTitle(R.string.share_label).startChooser() }
            .show()
    }
    val showImporSuccessDialog = remember { mutableStateOf(false) }
    ComfirmDialog(titleRes = R.string.successful_import_label, message = stringResource(R.string.import_ok), showDialog = showImporSuccessDialog, cancellable = false) { forceRestart() }

    val showImporErrortDialog = remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }
    ComfirmDialog(titleRes = R.string.import_export_error_label, message = importErrorMessage, showDialog = showImporErrortDialog) {}

    fun exportWithWriter(exportWriter: ExportWriter, uri: Uri?, exportType: ExportTypes) {
        val context: Context? = activity
        showProgress = true
        if (uri == null) {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val output = ExportWorker(exportWriter, activity).exportFile()
                    withContext(Dispatchers.Main) {
                        val fileUri = FileProvider.getUriForFile(context!!.applicationContext, context.getString(R.string.provider_authority), output!!)
                        showExportSuccessSnackbar(fileUri, exportType.contentType)
                    }
                } catch (e: Exception) {
                    showProgress = false
                    importErrorMessage = e.message?:"Reason unknown"
                    showImporErrortDialog.value = true
                } finally { showProgress = false }
            }
        } else {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val worker = DocumentFileExportWorker(exportWriter, context!!, uri)
                try {
                    val output = worker.exportFile()
                    withContext(Dispatchers.Main) { showExportSuccessSnackbar(output.uri, exportType.contentType) }
                } catch (e: Exception) {
                    showProgress = false
                    importErrorMessage = e.message?:"Reason unknown"
                    showImporErrortDialog.value = true
                } finally { showProgress = false }
            }
        }
    }

    val chooseOpmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(OpmlWriter(), uri, ExportTypes.OPML)
    }
    val chooseHtmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(HtmlWriter(), uri, ExportTypes.HTML)
    }
    val chooseFavoritesExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(FavoritesWriter(), uri, ExportTypes.FAVORITES)
    }
    val chooseProgressExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(EpisodesProgressWriter(), uri, ExportTypes.PROGRESS)
    }
    val restoreProgressLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data
        uri?.let {
            if (isJsonFile(uri)) {
                showProgress = true
                activity.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val inputStream: InputStream? = activity.contentResolver.openInputStream(uri)
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            EpisodeProgressReader().readDocument(reader)
                            reader.close()
                        }
                        withContext(Dispatchers.Main) {
                            showImporSuccessDialog.value = true
//                                showImportSuccessDialog()
                            showProgress = false
                        }
                    } catch (e: Throwable) {
                        showProgress = false
                        importErrorMessage = e.message?:"Reason unknown"
                        showImporErrortDialog.value = true
                    }
                }
            } else {
                val message = activity.getString(R.string.import_file_type_toast) + ".json"
                showProgress = false
                importErrorMessage = message
                showImporErrortDialog.value = true
            }
        }
    }
    var showOpmlImportSelectionDialog by remember { mutableStateOf(false) }
    val readElements = remember { mutableStateListOf<OpmlElement>() }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        Logd(TAG, "chooseOpmlImportPathResult: uri: $uri")
        OpmlTransporter.startImport(activity, uri) {
            readElements.addAll(it)
            Logd(TAG, "readElements: ${readElements.size}")
        }
        showOpmlImportSelectionDialog = true
    }

    var comboRootUri by remember { mutableStateOf<Uri?>(null) }
    val comboDic = remember { mutableStateMapOf<String, Boolean>() }
    var showComboImportDialog by remember { mutableStateOf(false) }
    if (showComboImportDialog) {
        AlertDialog(onDismissRequest = { showComboImportDialog = false },
            title = { Text(stringResource(R.string.pref_select_properties), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    comboDic.keys.forEach { option ->
                        if (option != "Media files" || comboDic["Database"] != true) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = comboDic[option] == true, onCheckedChange = {
                                comboDic[option] = it
                                if (option == "Database" && it) comboDic["Media files"] = false
                            })
                            Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (comboDic["Media files"] != null && comboDic["Database"] == true) Text(stringResource(R.string.pref_import_media_files_later), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = comboRootUri!!
                    showProgress = true
                    activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val rootFile = DocumentFile.fromTreeUri(activity, uri)
                                if (rootFile != null && rootFile.isDirectory) {
                                    Logd(TAG, "comboDic[\"Preferences\"] ${comboDic["Preferences"]}")
                                    Logd(TAG, "comboDic[\"Media files\"] ${comboDic["Media files"]}")
                                    Logd(TAG, "comboDic[\"Database\"] ${comboDic["Database"]}")
                                    for (child in rootFile.listFiles()) {
                                        if (child.isDirectory) {
                                            if (child.name == prefsDirName) {
                                                if (comboDic["Preferences"] == true) PreferencesTransporter(prefsDirName).importBackup(child.uri, activity)
                                            } else if (child.name == mediaFilesDirName) {
                                                if (comboDic["Media files"] == true) MediaFilesTransporter(mediaFilesDirName).importBackup(child.uri, activity)
                                            }
                                        } else if (isRealmFile(child.uri) && comboDic["Database"] == true) DatabaseTransporter().importBackup(child.uri, activity)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                showImporSuccessDialog.value = true
                                showProgress = false
                            }
                        } catch (e: Throwable) {
                            showProgress = false
                            importErrorMessage = e.message?:"Reason unknown"
                            showImporErrortDialog.value = true
                        }
                    }
                    showComboImportDialog = false
                }) { Text(text = "OK") }
            },
            dismissButton = { TextButton(onClick = { showComboImportDialog = false }) { Text(text = "Cancel") } }
        )
    }
    var showComboExportDialog by remember { mutableStateOf(false) }
    if (showComboExportDialog) {
        AlertDialog(onDismissRequest = { showComboExportDialog = false },
            title = { Text(stringResource(R.string.pref_select_properties), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    comboDic.keys.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = comboDic[option] == true, onCheckedChange = { comboDic[option] = it })
                            Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = comboRootUri!!
                    showProgress = true
                    activity.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val chosenDir = DocumentFile.fromTreeUri(activity, uri) ?: throw IOException("Destination directory is not valid")
                            val exportSubDir = chosenDir.createDirectory(dateStampFilename("$backupDirName-%s")) ?: throw IOException("Error creating subdirectory $backupDirName")
                            val subUri: Uri = exportSubDir.uri
                            if (comboDic["Preferences"] == true) PreferencesTransporter(prefsDirName).exportToDocument(subUri, activity)
                            if (comboDic["Media files"] == true) MediaFilesTransporter(mediaFilesDirName).exportToDocument(subUri, activity)
                            if (comboDic["Database"] == true) {
                                val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                                if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri, activity)
                            }
                        }
                        withContext(Dispatchers.Main) { showProgress = false }
                    }
                    showComboExportDialog = false
                }) { Text(text = "OK") }
            },
            dismissButton = { TextButton(onClick = { showComboExportDialog = false }) { Text(text = "Cancel") } }
        )
    }

    var backupFolder by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefAutoBackupFolder.name, activity.getString(R.string.pref_auto_backup_folder_sum))!! ) }
    val autoBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                activity.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                backupFolder = uri.toString()
                appPrefs.edit().putString(UserPreferences.Prefs.prefAutoBackupFolder.name, uri.toString()).apply()
            }
        }
    }

    val restoreComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        if (isComboDir(uri)) {
            val rootFile = DocumentFile.fromTreeUri(activity, uri)
            if (rootFile != null && rootFile.isDirectory) {
                comboDic.clear()
                for (child in rootFile.listFiles()) {
                    Logd(TAG, "restoreComboLauncher child: ${child.isDirectory} ${child.name} ${child.uri} ")
                    if (child.isDirectory) {
                        if (child.name == prefsDirName) comboDic["Preferences"] = true
                        else if (child.name == mediaFilesDirName) comboDic["Media files"] = false
                    } else if (isRealmFile(child.uri)) comboDic["Database"] = true
                }
            }
            comboRootUri = uri
            showComboImportDialog = true
        } else {
            val message = activity.getString(R.string.import_directory_toast) + backupDirName
            showProgress = false
            importErrorMessage = message
            showImporErrortDialog.value = true
        }
    }
    val backupComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                comboDic.clear()
                comboDic["Database"] = true
                comboDic["Preferences"] = true
                comboDic["Media files"] = true
                comboRootUri = uri
                showComboExportDialog = true
            }
        }
    }

    val chooseAPImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            showProgress = true
            importAP(uri, activity = activity) {
                showImporSuccessDialog.value = true
                showProgress = false
            }
        } }

    val choosePAImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            showProgress = true
            importPA(uri, activity = activity) {
                showImporSuccessDialog.value = true
                showProgress = false
            }
        } }

    fun launchExportCombos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        backupComboLauncher.launch(intent)
    }

    fun openExportPathPicker(exportType: ExportTypes, result: ActivityResultLauncher<Intent>, writer: ExportWriter) {
        val title = dateStampFilename(exportType.outputNameTemplate)
        val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(exportType.contentType)
            .putExtra(Intent.EXTRA_TITLE, title)
        try {
            result.launch(intentPickAction)
            return
        } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
        // If we are using a SDK lower than API 21 or the implicit intent failed fallback to the legacy export process
        exportWithWriter(writer, null, exportType)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    if (showProgress) {
        Dialog(onDismissRequest = { showProgress = false }) {
            Surface(modifier = Modifier.size(100.dp), shape = RoundedCornerShape(8.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = {0.7f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.TopCenter))
                    Text("Loading...", color = textColor, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        var isAutoBackup by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefAutoBackup.name, false)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pref_auto_backup_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_auto_backup_sum), color = textColor)
            }
            Switch(checked = isAutoBackup, onCheckedChange = {
                isAutoBackup = it
                appPrefs.edit().putBoolean(UserPreferences.Prefs.prefAutoBackup.name, it).apply()
            })
        }
        if (isAutoBackup) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Text(stringResource(R.string.pref_auto_backup_interval), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var interval by remember { mutableStateOf(appPrefs.getInt(UserPreferences.Prefs.prefAutoBackupIntervall.name, 24).toString()) }
                var showIcon by remember { mutableStateOf(false) }
                TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("(hours)") },
                    singleLine = true, modifier = Modifier.weight(0.5f),
                    onValueChange = {
                        val intVal = it.toIntOrNull()
                        if (it.isEmpty() || (intVal != null && intVal>0)) {
                            interval = it
                            showIcon = true
                        }
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                            modifier = Modifier.size(30.dp).padding(start = 5.dp).clickable(onClick = {
                                if (interval.isEmpty()) interval = "0"
                                appPrefs.edit().putInt(UserPreferences.Prefs.prefAutoBackupIntervall.name, interval.toIntOrNull()?:0).apply()
                                showIcon = false
                            }))
                    })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Text(stringResource(R.string.pref_auto_backup_limit), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var count by remember { mutableStateOf(appPrefs.getInt(UserPreferences.Prefs.prefAutoBackupLimit.name, 2).toString()) }
                var showIcon by remember { mutableStateOf(false) }
                TextField(value = count, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(0.4f),  label = { Text("1 - 9") },
                    onValueChange = {
                        val intVal = it.toIntOrNull()
                        if (it.isEmpty() || (intVal != null && intVal>0 && intVal<10)) {
                            count = it
                            showIcon = true
                        }
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                            modifier = Modifier.size(30.dp).padding(start = 5.dp).clickable(onClick = {
                                if (count.isEmpty()) count = "0"
                                appPrefs.edit().putInt(UserPreferences.Prefs.prefAutoBackupLimit.name, count.toIntOrNull()?:0).apply()
                                showIcon = false
                            }))
                    })
            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                autoBackupLauncher.launch(intent)
            })) {
                Text(stringResource(R.string.pref_auto_backup_folder), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(backupFolder, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.combo_export_label, R.string.combo_export_summary) { launchExportCombos() }
        val showComboImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.combo_import_label, message = stringResource(R.string.combo_import_warning), showDialog = showComboImportDialog) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            restoreComboLauncher.launch(intent)
        }
        TitleSummaryActionColumn(R.string.combo_import_label, R.string.combo_import_summary) { showComboImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        val showAPImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.import_AP_label, message = stringResource(R.string.import_SQLite_message), showDialog = showAPImportDialog) {
            try { chooseAPImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
        }
        TitleSummaryActionColumn(R.string.import_AP_label, 0) { showAPImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        val showPAImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.import_PA_label, message = stringResource(R.string.import_SQLite_message), showDialog = showPAImportDialog) {
            try { choosePAImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
        }
        TitleSummaryActionColumn(R.string.import_PA_label, 0) { showPAImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.opml_export_label, R.string.opml_export_summary) { openExportPathPicker(ExportTypes.OPML, chooseOpmlExportPathLauncher, OpmlWriter()) }
        if (showOpmlImportSelectionDialog) OpmlImportSelectionDialog(readElements) { showOpmlImportSelectionDialog = false }
        TitleSummaryActionColumn(R.string.opml_import_label, R.string.opml_import_summary) {
            try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") } }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.progress_export_label, R.string.progress_export_summary) { openExportPathPicker(ExportTypes.PROGRESS, chooseProgressExportPathLauncher, EpisodesProgressWriter()) }
        val showProgressImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.progress_import_label, message = stringResource(R.string.progress_import_warning), showDialog = showProgressImportDialog) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setType("*/*")
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            restoreProgressLauncher.launch(intent)
        }
        TitleSummaryActionColumn(R.string.progress_import_label, R.string.progress_import_summary) { showProgressImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.html_export_label, R.string.html_export_summary) { openExportPathPicker(ExportTypes.HTML, chooseHtmlExportPathLauncher, HtmlWriter()) }
        TitleSummaryActionColumn(R.string.favorites_export_label, R.string.favorites_export_summary) { openExportPathPicker(ExportTypes.FAVORITES, chooseFavoritesExportPathLauncher, FavoritesWriter()) }
    }
}
