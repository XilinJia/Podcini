package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlReader
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInputStream
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.apache.commons.io.IOUtils
import org.xmlpull.v1.XmlPullParserException
import java.io.*
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


class OpmlBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        val isAutoBackupOPML = appPrefs.getBoolean(UserPreferences.Prefs.prefOPMLBackup.name, true)
        if (isAutoBackupOPML) {
            Logd(TAG, "Backup of OPML enabled in preferences")
            addHelper(OPML_BACKUP_KEY, OpmlBackupHelper(this))
        } else Logd(TAG, "Backup of OPML disabled in preferences")
    }

    /**
     * Class for backing up and restoring the OPML file.
     */
    private class OpmlBackupHelper(private val mContext: Context) : BackupHelper {
        /**
         * Checksum of restored OPML file
         */
        private var mChecksum: ByteArray = byteArrayOf()

        override fun performBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
            Logd(TAG, "Performing backup")
            val byteStream = ByteArrayOutputStream()
            var digester: MessageDigest? = null
            var writer: Writer

            try {
                digester = MessageDigest.getInstance("MD5")
                writer = OutputStreamWriter(DigestOutputStream(byteStream, digester), Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) {
                writer = OutputStreamWriter(byteStream, Charset.forName("UTF-8"))
            }

            try {
                // Write OPML
                OpmlWriter().writeDocument(getFeedList(), writer, mContext)
                // Compare checksum of new and old file to see if we need to perform a backup at all
                if (digester != null) {
                    val newChecksum = digester.digest()
                    Logd(TAG, "New checksum: " + BigInteger(1, newChecksum).toString(16))
                    // Get the old checksum
                    if (oldState != null) {
                        val inState = FileInputStream(oldState.fileDescriptor)
                        val len = inState.read()
                        if (len != -1) {
                            val oldChecksum = ByteArray(len)
                            IOUtils.read(inState, oldChecksum, 0, len)
                            Logd(TAG, "Old checksum: " + BigInteger(1, oldChecksum).toString(16))
                            if (oldChecksum.contentEquals(newChecksum)) {
                                Logd(TAG, "Checksums are the same; won't backup")
                                return
                            }
                        }
                    }
                    writeNewStateDescription(newState, newChecksum)
                }
                Logd(TAG, "Backing up OPML")
                val bytes = byteStream.toByteArray()
                data.writeEntityHeader(OPML_ENTITY_KEY, bytes.size)
                data.writeEntityData(bytes, bytes.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error during backup", e)
            } finally {
                IOUtils.closeQuietly(writer)
            }
        }
        
        override fun restoreEntity(data: BackupDataInputStream) {
            Logd(TAG, "Backup restore")
            if (OPML_ENTITY_KEY != data.key) {
                Logd(TAG, "Unknown entity key: " + data.key)
                return
            }
            var digester: MessageDigest? = null
            var reader: Reader
            var linesRead = 0
            try {
                digester = MessageDigest.getInstance("MD5")
                reader = InputStreamReader(DigestInputStream(data, digester), Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) {
                reader = InputStreamReader(data, Charset.forName("UTF-8"))
            }
            try {
                mChecksum = digester?.digest() ?: byteArrayOf()
                BufferedReader(reader).use { bufferedReader ->
                    val tempFile = File(mContext.filesDir, "opml_restored.txt")
//                    val tempFile = File.createTempFile("opml_restored", ".tmp", mContext.filesDir)
                    FileWriter(tempFile).use { fileWriter ->
                        while (true) {
                            val line = bufferedReader.readLine() ?: break
                            Logd(TAG, "restoreEntity: $linesRead $line")
                            linesRead++
                            fileWriter.write(line)
                            fileWriter.write(System.lineSeparator()) // Write a newline character
                        }
                    }
                }
            } catch (e: XmlPullParserException) {
                Log.e(TAG, "Error while parsing the OPML file", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to restore OPML backup", e)
            } finally {
                if (linesRead > 0) {
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
                    with(sharedPreferences.edit()) {
                        putBoolean(UserPreferences.Prefs.prefOPMLRestore.name, true)
                        apply()
                    }
                }
                IOUtils.closeQuietly(reader)
            }
        }
        override fun writeNewStateDescription(newState: ParcelFileDescriptor) {
            writeNewStateDescription(newState, mChecksum)
        }
        /**
         * Writes the new state description, which is the checksum of the OPML file.
         * @param newState
         * @param checksum
         */
        private fun writeNewStateDescription(newState: ParcelFileDescriptor, checksum: ByteArray?) {
            if (checksum == null) return
            try {
                val outState = FileOutputStream(newState.fileDescriptor)
                outState.write(checksum.size)
                outState.write(checksum)
                outState.flush()
                outState.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write new state description", e)
            }
        }

        companion object {
            private val TAG: String = OpmlBackupHelper::class.simpleName ?: "Anonymous"
            private const val OPML_ENTITY_KEY = "podcini-feeds.opml"
        }
    }

    companion object {
        private val TAG: String = OpmlBackupAgent::class.simpleName ?: "Anonymous"
        private const val OPML_BACKUP_KEY = "opml"

        val isOPMLRestared: Boolean
            get() = appPrefs.getBoolean(UserPreferences.Prefs.prefOPMLRestore.name, false)

        fun performRestore(context: Context) {
            Logd(TAG, "performRestore")
            val tempFile = File(context.filesDir, "opml_restored.txt")
//            val tempFile = File.createTempFile("opml_restored", ".tmp", context.filesDir)
            if (tempFile.exists()) {
                val reader = FileReader(tempFile)
                val opmlElements = OpmlReader().readDocument(reader)
                for (opmlElem in opmlElements) {
                    val feed = Feed(opmlElem.xmlUrl, null, opmlElem.text)
                    feed.episodes.clear()
                    updateFeed(context, feed, false)
                }
                Toast.makeText(context, "${opmlElements.size} feeds were restored", Toast.LENGTH_SHORT).show()
                runOnce(context)
            } else {
                Toast.makeText(context, "No backup data found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
