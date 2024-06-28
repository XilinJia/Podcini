package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.BuildConfig
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import ac.mdiq.podcini.util.Logd
import androidx.annotation.VisibleForTesting
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

object RatingDialog {
    private val TAG: String = RatingDialog::class.simpleName ?: "Anonymous"
    private const val AFTER_DAYS = 14

    private var mContext: WeakReference<Context>? = null
    private lateinit var mPreferences: SharedPreferences

    private const val PREFS_NAME = "RatingPrefs"
    private const val KEY_RATED = "KEY_WAS_RATED"
    private const val KEY_FIRST_START_DATE = "KEY_FIRST_HIT_DATE"
    private const val KEY_NUMBER_OF_REVIEWS = "NUMBER_OF_REVIEW_ATTEMPTS"

    fun init(context: Context) {
        mContext = WeakReference(context)
        mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val firstDate: Long = mPreferences.getLong(KEY_FIRST_START_DATE, 0)
        if (firstDate == 0L) {
            resetStartDate()
        }
    }

    fun check() {
        if (shouldShow()) {
            try {
                showInAppReview()
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    private fun showInAppReview() {
        val context = mContext!!.get() ?: return

        val manager: ReviewManager = ReviewManagerFactory.create(context)
        val request: Task<ReviewInfo> = manager.requestReviewFlow()

        request.addOnCompleteListener { task: Task<ReviewInfo?> ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                val flow: Task<Void?> = manager.launchReviewFlow(context as Activity, reviewInfo)
                flow.addOnCompleteListener { task1: Task<Void?>? ->
                    val previousAttempts: Int = mPreferences.getInt(KEY_NUMBER_OF_REVIEWS, 0)
                    if (previousAttempts >= 3) {
                        saveRated()
                    } else {
                        resetStartDate()
                        mPreferences
                            .edit()
                            .putInt(KEY_NUMBER_OF_REVIEWS, previousAttempts + 1)
                            .apply()
                    }
                    Logd("ReviewDialog", "Successfully finished in-app review")
                }
                    .addOnFailureListener { error: Exception? ->
                        Logd("ReviewDialog", "failed in reviewing process")
                    }
            }
        }
            .addOnFailureListener { error: Exception? ->
                Logd("ReviewDialog", "failed to get in-app review request")
            }
    }

    private fun rated(): Boolean {
        return mPreferences.getBoolean(KEY_RATED, false)
    }

    @VisibleForTesting
    fun saveRated() {
        mPreferences
            .edit()
            .putBoolean(KEY_RATED, true)
            .apply()
    }

    private fun resetStartDate() {
        mPreferences
            .edit()
            .putLong(KEY_FIRST_START_DATE, System.currentTimeMillis())
            .apply()
    }

    private fun shouldShow(): Boolean {
        if (rated() || BuildConfig.DEBUG) {
            return false
        }

        val now = System.currentTimeMillis()
        val firstDate: Long = mPreferences.getLong(KEY_FIRST_START_DATE, now)
        val diff = now - firstDate
        val diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        return diffDays >= AFTER_DAYS
    }
}
