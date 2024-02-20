package ac.mdiq.podcini.core.backup

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInputStream
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import ac.mdiq.podcini.core.export.opml.OpmlReader
import ac.mdiq.podcini.core.export.opml.OpmlWriter
import ac.mdiq.podcini.core.storage.DBReader.getFeedList
import ac.mdiq.podcini.core.storage.DBTasks.updateFeed
import ac.mdiq.podcini.core.util.download.FeedUpdateManager.runOnce
import ac.mdiq.podcini.model.feed.Feed
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
        addHelper(OPML_BACKUP_KEY, OpmlBackupHelper(this))
    }

    /**
     * Class for backing up and restoring the OPML file.
     */
    private class OpmlBackupHelper(private val mContext: Context) : BackupHelper {
        /**
         * Checksum of restored OPML file
         */
        private var mChecksum: ByteArray = byteArrayOf()

        override fun performBackup(oldState: ParcelFileDescriptor,
                                   data: BackupDataOutput,
                                   newState: ParcelFileDescriptor
        ) {
            Log.d(TAG, "Performing backup")
            val byteStream = ByteArrayOutputStream()
            var digester: MessageDigest? = null
            var writer: Writer

            try {
                digester = MessageDigest.getInstance("MD5")
                writer = OutputStreamWriter(DigestOutputStream(byteStream, digester),
                    Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) {
                writer = OutputStreamWriter(byteStream, Charset.forName("UTF-8"))
            }

            try {
                // Write OPML
                OpmlWriter().writeDocument(getFeedList(), writer, mContext)

                // Compare checksum of new and old file to see if we need to perform a backup at all
                if (digester != null) {
                    val newChecksum = digester.digest()
                    Log.d(TAG, "New checksum: " + BigInteger(1, newChecksum).toString(16))

                    // Get the old checksum
                    if (oldState != null) {
                        val inState = FileInputStream(oldState.fileDescriptor)
                        val len = inState.read()

                        if (len != -1) {
                            val oldChecksum = ByteArray(len)
                            IOUtils.read(inState, oldChecksum, 0, len)
                            Log.d(TAG, "Old checksum: " + BigInteger(1, oldChecksum).toString(16))

                            if (oldChecksum.contentEquals(newChecksum)) {
                                Log.d(TAG, "Checksums are the same; won't backup")
                                return
                            }
                        }
                    }

                    writeNewStateDescription(newState, newChecksum)
                }

                Log.d(TAG, "Backing up OPML")
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
            Log.d(TAG, "Backup restore")

            if (OPML_ENTITY_KEY != data.key) {
                Log.d(TAG, "Unknown entity key: " + data.key)
                return
            }

            var digester: MessageDigest? = null
            var reader: Reader

            try {
                digester = MessageDigest.getInstance("MD5")
                reader = InputStreamReader(DigestInputStream(data, digester),
                    Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) {
                reader = InputStreamReader(data, Charset.forName("UTF-8"))
            }

            try {
                val opmlElements = OpmlReader().readDocument(reader)
                mChecksum = digester?.digest()?: byteArrayOf()
                for (opmlElem in opmlElements) {
                    val feed = Feed(opmlElem.xmlUrl, null, opmlElem.text)
                    feed.items = mutableListOf()
                    updateFeed(mContext, feed, false)
                }
                runOnce(mContext)
            } catch (e: XmlPullParserException) {
                Log.e(TAG, "Error while parsing the OPML file", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to restore OPML backup", e)
            } finally {
                IOUtils.closeQuietly(reader)
            }
        }

        override fun writeNewStateDescription(newState: ParcelFileDescriptor) {
            writeNewStateDescription(newState, mChecksum)
        }

        /**
         * Writes the new state description, which is the checksum of the OPML file.
         *
         * @param newState
         * @param checksum
         */
        private fun writeNewStateDescription(newState: ParcelFileDescriptor, checksum: ByteArray?) {
            if (checksum == null) {
                return
            }

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
            private const val TAG = "OpmlBackupHelper"

            private const val OPML_ENTITY_KEY = "podcini-feeds.opml"
        }
    }

    companion object {
        private const val OPML_BACKUP_KEY = "opml"
    }
}
