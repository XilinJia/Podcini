package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.confirmAddYoutubeEpisode
import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.services.youtube.YoutubeParsingHelper.isYoutubeServiceURL
import ac.mdiq.vista.extractor.services.youtube.YoutubeParsingHelper.isYoutubeURL
import android.app.Service.START_STICKY
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkerParameters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URL
import java.net.URLDecoder

class ShareReceiverActivity : AppCompatActivity() {
    private var sharedUrl: String? = null

    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logd(TAG, "intent: $intent")
        when {
            intent.hasExtra(ARG_FEEDURL) -> sharedUrl = intent.getStringExtra(ARG_FEEDURL)
            intent.action == Intent.ACTION_SEND -> sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action == Intent.ACTION_VIEW -> sharedUrl = intent.dataString
        }
        if (sharedUrl.isNullOrBlank()) {
            Log.e(TAG, "feedUrl is empty or null.")
            showNoPodcastFoundError()
            return
        }
        if (!sharedUrl!!.startsWith("http")) {
            val uri = Uri.parse(sharedUrl)
            val urlString = uri?.getQueryParameter("url")
            if (urlString != null) sharedUrl = URLDecoder.decode(urlString, "UTF-8")
        }
        Logd(TAG, "feedUrl: $sharedUrl")
        val log = ShareLog(sharedUrl!!)
        upsertBlk(log) {}

        receiveShared(sharedUrl!!,this, true) {
            setContent {
                val showDialog = remember { mutableStateOf(true) }
                CustomTheme(this) {
                    confirmAddYoutubeEpisode(listOf(sharedUrl!!), showDialog.value, onDismissRequest = {
                        showDialog.value = false
//                        finish()
                    })
                }
            }
        }
    }

    private fun showNoPodcastFoundError() {
        runOnUiThread {
            MaterialAlertDialogBuilder(this@ShareReceiverActivity)
                .setNeutralButton(android.R.string.ok) { _: DialogInterface?, _: Int -> finish() }
                .setTitle(R.string.error_label)
                .setMessage(R.string.null_value_podcast_error)
                .setOnDismissListener {
                    setResult(RESULT_ERROR)
                    finish() }
                .show()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

//    class UrlFetchWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
//        override fun doWork(): Result {
//            val url = inputData.getString("shared_url") ?: return Result.failure()
//
//            // Fetch the content from URL using OkHttp or another HTTP client
//            // Ensure you're on a background thread
//
//            return Result.success()
//        }
//    }

//    // Schedule the worker
//    val workRequest = OneTimeWorkRequestBuilder<UrlFetchWorker>()
//        .setInputData(workDataOf("shared_url" to sharedUrl))
//        .build()
//
//    WorkManager.getInstance(context).enqueue(workRequest)

    companion object {
        private val TAG: String = ShareReceiverActivity::class.simpleName ?: "Anonymous"

        const val ARG_FEEDURL: String = "arg.feedurl"
        private const val RESULT_ERROR = 2

        fun receiveShared(sharedUrl: String, activity: AppCompatActivity, finish: Boolean, mediaCB: ()->Unit) {
            val url = URL(sharedUrl)
            val log = realm.query(ShareLog::class).query("url == $0", sharedUrl).first().find()
            when {
//            plain text
                sharedUrl.matches(Regex("^[^\\s<>/]+\$")) -> {
                    if (log != null)  upsertBlk(log) {it.type = "text" }
                    val intent = MainActivity.showOnlineSearch(activity, sharedUrl)
                    activity.startActivity(intent)
                    if (finish) activity.finish()
                }
//            Youtube media
                (isYoutubeURL(url) && url.path.startsWith("/watch")) || isYoutubeServiceURL(url) -> {
                    if (log != null)  upsertBlk(log) {it.type = "youtube media" }
                    Logd(TAG, "got youtube media")
                    mediaCB()
                }
//            podcast or Youtube channel, Youtube playlist, or other?
                else -> {
                    if (log != null)  upsertBlk(log) {it.type = "podcast" }
                    Logd(TAG, "Activity was started with url $sharedUrl")
                    val intent = MainActivity.showOnlineFeed(activity, sharedUrl)
                    activity.startActivity(intent)
                    if (finish) activity.finish()
                }
            }
        }
    }
}
