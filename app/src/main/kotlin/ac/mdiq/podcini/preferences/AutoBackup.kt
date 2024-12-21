package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
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

fun autoBackup(activity: Activity) {
    val TAG = "autoBackup"

    val backupDirName = "Podcini-AudoBackups"
    val prefsDirName = "Podcini-Prefs"

    val isAutoBackup = appPrefs.getBoolean(UserPreferences.Prefs.prefAutoBackup.name, false)
    if (!isAutoBackup) return
    val uriString = appPrefs.getString(UserPreferences.Prefs.prefAutoBackupFolder.name, "")
    if (uriString.isNullOrBlank()) return

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
        val interval = appPrefs.getInt(UserPreferences.Prefs.prefAutoBackupIntervall.name, 24)
        var lastBackupTime = appPrefs.getLong(UserPreferences.Prefs.prefAutoBackupTimeStamp.name, 0L)
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
                                if (file.isDirectory && file.name?.startsWith(backupDirName, ignoreCase = true) == true) backupDirs.add(file)
                            }
                        }
                        Logd(TAG, "backupDirs: ${backupDirs.size}")
                        val limit = appPrefs.getInt(UserPreferences.Prefs.prefAutoBackupLimit.name, 2)
                        if (backupDirs.size >= limit) {
                            backupDirs.sortBy { it.name }
                            for (i in 0..(backupDirs.size - limit)) deleteDirectoryAndContents(backupDirs[i])
                        }

                        val dirName = dateStampFilename("$backupDirName-%s")
                        val exportSubDir = chosenDir.createDirectory(dirName) ?: throw IOException("Error creating subdirectory $dirName")
                        val subUri: Uri = exportSubDir.uri
                        PreferencesTransporter(prefsDirName).exportToDocument(subUri, activity)
                        val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                        if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri, activity)

                        appPrefs.edit().putLong(UserPreferences.Prefs.prefAutoBackupTimeStamp.name, curTime).apply()
                    } catch (e: Exception) { Log.e("autoBackup", "Error backing up ${e.message}") }
                }
            } else Log.e("autoBackup", "Uri permissions are no longer valid")
        }
    }
}