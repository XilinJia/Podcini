package ac.mdiq.podcini.storage.model.feed

import android.text.TextUtils
import java.io.File
import java.io.Serializable

/**
 * Represents a component of a Feed that has to be downloaded
 */
abstract class FeedFile @JvmOverloads constructor(@JvmField var file_url: String? = null,
                                                  @JvmField var download_url: String? = null,
                                                  private var downloaded: Boolean = false
) : FeedComponent(), Serializable {

    /**
     * Creates a new FeedFile object.
     *
     * @param file_url     The location of the FeedFile. If this is null, the downloaded-attribute
     * will automatically be set to false.
     * @param download_url The location where the FeedFile can be downloaded.
     * @param downloaded   true if the FeedFile has been downloaded, false otherwise. This parameter
     * will automatically be interpreted as false if the file_url is null.
     */
    init {
//        Log.d("FeedFile", "$file_url $download_url $downloaded")
        this.downloaded = (file_url != null) && downloaded
    }

    abstract fun getTypeAsInt(): Int

    /**
     * Update this FeedFile's attributes with the attributes from another
     * FeedFile. This method should only update attributes which where read from
     * the feed.
     */
    fun updateFromOther(other: FeedFile) {
        super.updateFromOther(other)
        this.download_url = other.download_url
    }

    /**
     * Compare's this FeedFile's attribute values with another FeedFile's
     * attribute values. This method will only compare attributes which were
     * read from the feed.
     *
     * @return true if attribute values are different, false otherwise
     */
    fun compareWithOther(other: FeedFile): Boolean {
        if (super.compareWithOther(other)) {
            return true
        }
        if (!TextUtils.equals(download_url, other.download_url)) {
            return true
        }
        return false
    }

    /**
     * Returns true if the file exists at file_url.
     */
    fun fileExists(): Boolean {
        if (file_url == null) {
            return false
        } else {
            val f = File(file_url!!)
            return f.exists()
        }
    }

    fun getFile_url(): String? {
        return file_url
    }

    /**
     * Changes the file_url of this FeedFile. Setting this value to
     * null will also set the downloaded-attribute to false.
     */
    open fun setFile_url(file_url: String?) {
        this.file_url = file_url
        if (file_url == null) {
            downloaded = false
        }
    }

    fun isDownloaded(): Boolean {
        return downloaded
    }

    open fun setDownloaded(downloaded: Boolean) {
        this.downloaded = downloaded
    }

}
