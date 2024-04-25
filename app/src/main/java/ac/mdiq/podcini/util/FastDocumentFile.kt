package ac.mdiq.podcini.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Android's DocumentFile is slow because every single method call queries the ContentResolver.
 * This queries the ContentResolver a single time with all the information.
 */
class FastDocumentFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long) {
    companion object {
        @JvmStatic
        fun list(context: Context, folderUri: Uri?): List<FastDocumentFile> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getDocumentId(folderUri))
            val cursor = context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
            val list = ArrayList<FastDocumentFile>()
            while (cursor!!.moveToNext()) {
                val id = cursor.getString(0)
                val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                val name = cursor.getString(1)
                val size = cursor.getLong(2)
                val lastModified = cursor.getLong(3)
                val mimeType = cursor.getString(4)
                list.add(FastDocumentFile(name, mimeType, uri, size, lastModified))
            }
            cursor.close()
            return list
        }
    }
}
