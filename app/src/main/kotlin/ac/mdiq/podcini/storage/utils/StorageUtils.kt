package ac.mdiq.podcini.storage.utils

import android.os.StatFs
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.utils.FilesUtils.getDataFolder

/**
 * Utility functions for handling storage errors
 */
object StorageUtils {
    /**
     * Get the number of free bytes that are available on the external storage.
     */
    @JvmStatic
    val freeSpaceAvailable: Long
        get() {
            val dataFolder = getDataFolder(null)
            return if (dataFolder != null) getFreeSpaceAvailable(dataFolder.absolutePath) else 0
        }

    /**
     * Get the number of free bytes that are available on the external storage.
     */
    @JvmStatic
    fun getFreeSpaceAvailable(path: String?): Long {
        val stat = StatFs(path)
        val availableBlocks = stat.availableBlocksLong
        val blockSize = stat.blockSizeLong
        return availableBlocks * blockSize
    }

    @JvmStatic
    fun getTotalSpaceAvailable(path: String?): Long {
        val stat = StatFs(path)
        val blockCount = stat.blockCountLong
        val blockSize = stat.blockSizeLong
        return blockCount * blockSize
    }
}
