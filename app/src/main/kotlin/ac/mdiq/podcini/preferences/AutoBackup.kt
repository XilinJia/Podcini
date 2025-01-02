package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.getPref
import ac.mdiq.podcini.preferences.UserPreferences.putPref
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.dateStampFilename
import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

const val autoBackupDirName = "Podcini-AutoBackups"

fun autoBackup(activity: Activity) {
    val TAG = "autoBackup"

    val prefsDirName = "Podcini-Prefs"

    val isAutoBackup = getPref(UserPreferences.Prefs.prefAutoBackup, false)
    if (!isAutoBackup) return
    val uriString = getPref(UserPreferences.Prefs.prefAutoBackupFolder, "")
    if (uriString.isBlank()) return

    Logd("autoBackup", "in autoBackup directory: $uriString")

    fun deleteDirectoryAndContents(directory: DocumentFile): Boolean {
        if (directory.isDirectory) {
            directory.listFiles().forEach { file ->
                if (file.isDirectory) deleteDirectoryAndContents(file)
                Logd(TAG, "deleting ${file.name}")
                file.delete()
            }
        }
        return directory.delete()
    }

    CoroutineScope(Dispatchers.IO).launch {
        val interval = getPref(UserPreferences.Prefs.prefAutoBackupIntervall, 24)
        var lastBackupTime = getPref(UserPreferences.Prefs.prefAutoBackupTimeStamp, 0L)
        val curTime = System.currentTimeMillis()
        if ((curTime - lastBackupTime) / 1000 / 3600 > interval) {
            val uri = Uri.parse(uriString)
            val permissions = activity.contentResolver.persistedUriPermissions.find { it.uri == uri }
            if (permissions != null && permissions.isReadPermission && permissions.isWritePermission) {
                val chosenDir = DocumentFile.fromTreeUri(activity, uri)
                if (chosenDir != null) {
                    val backupDirs = mutableListOf<DocumentFile>()
                    try {
                        if (chosenDir.isDirectory) {
                            chosenDir.listFiles().forEach { file ->
                                Logd(TAG, "file: $file")
                                if (file.isDirectory && file.name?.startsWith(autoBackupDirName, ignoreCase = true) == true) backupDirs.add(file)
                            }
                        }
                        Logd(TAG, "backupDirs: ${backupDirs.size}")
                        val limit = getPref(UserPreferences.Prefs.prefAutoBackupLimit, 2)
                        if (backupDirs.size >= limit) {
                            backupDirs.sortBy { it.name }
                            for (i in 0..(backupDirs.size - limit)) deleteDirectoryAndContents(backupDirs[i])
                        }

                        val dirName = dateStampFilename("$autoBackupDirName-%s")
                        val exportSubDir = chosenDir.createDirectory(dirName) ?: throw IOException("Error creating subdirectory $dirName")
                        val subUri: Uri = exportSubDir.uri
                        PreferencesTransporter(prefsDirName).exportToDocument(subUri, activity)
                        val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                        if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri, activity)

                        putPref(UserPreferences.Prefs.prefAutoBackupTimeStamp, curTime)
                    } catch (e: Exception) { Log.e("autoBackup", "Error backing up ${e.message}") }
                }
            } else Log.e("autoBackup", "Uri permissions are no longer valid")
        }
    }
}