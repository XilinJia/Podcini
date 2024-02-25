package ac.mdiq.podcini.storage

import android.content.Context
import android.util.Log

/**
 * A cleanup algorithm that never removes anything
 */
class APNullCleanupAlgorithm : EpisodeCleanupAlgorithm() {
    public override fun performCleanup(context: Context, parameter: Int): Int {
        // never clean anything up
        Log.i(TAG, "performCleanup: Not removing anything")
        return 0
    }

    public override fun getDefaultCleanupParameter(): Int {
        return 0
    }

    override fun getReclaimableItems(): Int {
        return 0
    }

    companion object {
        private const val TAG = "APNullCleanupAlgorithm"
    }
}
