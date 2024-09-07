package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.Logd
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URLDecoder

class ShareReceiverActivity : AppCompatActivity() {

    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var feedUrl: String? = null
        when {
            intent.hasExtra(ARG_FEEDURL) -> feedUrl = intent.getStringExtra(ARG_FEEDURL)
            intent.action == Intent.ACTION_SEND -> feedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action == Intent.ACTION_VIEW -> feedUrl = intent.dataString
        }

        if (!feedUrl.isNullOrBlank() && !feedUrl.startsWith("http")) {
            val uri = Uri.parse(feedUrl)
            val urlString = uri?.getQueryParameter("url")
            if (urlString != null) feedUrl = URLDecoder.decode(urlString, "UTF-8")
        }

        when {
            feedUrl.isNullOrBlank() -> {
                Log.e(TAG, "feedUrl is empty or null.")
                showNoPodcastFoundError()
            }
//            plain text
            feedUrl.matches(Regex("^[^\\s<>/]+\$")) -> {
                val intent = MainActivity.showOnlineSearch(this, feedUrl)
                startActivity(intent)
                finish()
            }
            else -> {
                Logd(TAG, "Activity was started with url $feedUrl")
                val intent = MainActivity.showOnlineFeed(this, feedUrl)
//                intent.putExtra(MainActivity.Extras.started_from_share.name, getIntent().getBooleanExtra(MainActivity.Extras.started_from_share.name, false))
                startActivity(intent)
                finish()
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

    companion object {
        const val ARG_FEEDURL: String = "arg.feedurl"
        private const val RESULT_ERROR = 2
        private val TAG: String = ShareReceiverActivity::class.simpleName ?: "Anonymous"
    }
}
