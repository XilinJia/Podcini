package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URLDecoder

// this now is only used for receiving shared feed url
class OnlineFeedViewActivity : AppCompatActivity() {

    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var feedUrl: String? = null
        when {
            intent.hasExtra(ARG_FEEDURL) -> {
                feedUrl = intent.getStringExtra(ARG_FEEDURL)
            }
            TextUtils.equals(intent.action, Intent.ACTION_SEND) -> {
                feedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            TextUtils.equals(intent.action, Intent.ACTION_VIEW) -> {
                feedUrl = intent.dataString
            }
        }

        if (!feedUrl.isNullOrBlank() && !feedUrl.startsWith("http")) {
            val uri = Uri.parse(feedUrl)
            val urlString = uri?.getQueryParameter("url")
            if (urlString != null) feedUrl = URLDecoder.decode(urlString, "UTF-8")
        }

        if (feedUrl == null) {
            Log.e(TAG, "feedUrl is null.")
            showNoPodcastFoundError()
        } else {
            Log.d(TAG, "Activity was started with url $feedUrl")

            val intent = MainActivity.showOnlineFeed(this, feedUrl)
            intent.putExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH,
                getIntent().getBooleanExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, false))
            startActivity(intent)
            finish()
        }
    }

    private fun showNoPodcastFoundError() {
        runOnUiThread {
            MaterialAlertDialogBuilder(this@OnlineFeedViewActivity)
                .setNeutralButton(android.R.string.ok) { _: DialogInterface?, _: Int -> finish() }
                .setTitle(R.string.error_label)
                .setMessage(R.string.null_value_podcast_error)
                .setOnDismissListener {
                    setResult(RESULT_ERROR)
                    finish()
                }
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
        private const val TAG = "OnlineFeedViewActivity"
    }
}
