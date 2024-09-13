package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Episodes.episodeFromStreamInfo
import ac.mdiq.podcini.storage.database.Feeds.addToYoutubeSyndicate
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.stream.StreamInfo
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder

class ShareReceiverActivity : AppCompatActivity() {
    var feedUrl: String? = null

    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            intent.hasExtra(ARG_FEEDURL) -> feedUrl = intent.getStringExtra(ARG_FEEDURL)
            intent.action == Intent.ACTION_SEND -> feedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action == Intent.ACTION_VIEW -> feedUrl = intent.dataString
        }
        if (feedUrl.isNullOrBlank()) {
            Log.e(TAG, "feedUrl is empty or null.")
            showNoPodcastFoundError()
            return
        }
        if (!feedUrl!!.startsWith("http")) {
            val uri = Uri.parse(feedUrl)
            val urlString = uri?.getQueryParameter("url")
            if (urlString != null) feedUrl = URLDecoder.decode(urlString, "UTF-8")
        }
        Logd(TAG, "feedUrl: $feedUrl")
        when {
//            plain text
            feedUrl!!.matches(Regex("^[^\\s<>/]+\$")) -> {
                val intent = MainActivity.showOnlineSearch(this, feedUrl!!)
                startActivity(intent)
                finish()
            }
//            Youtube media
            feedUrl!!.startsWith("https://youtube.com/watch?") -> {
                Logd(TAG, "got youtube media")
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(this@ShareReceiverActivity) {
                        confirmAddEpisode(showDialog.value, onDismissRequest = {
                            showDialog.value = false
                            finish()
                        })
                    }
                }
            }
            else -> {
                Logd(TAG, "Activity was started with url $feedUrl")
                val intent = MainActivity.showOnlineFeed(this, feedUrl!!)
//                intent.putExtra(MainActivity.Extras.started_from_share.name, getIntent().getBooleanExtra(MainActivity.Extras.started_from_share.name, false))
                startActivity(intent)
                finish()
            }
        }
    }

    @Composable
    fun confirmAddEpisode(showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        var audioOnly by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = audioOnly,
                                onCheckedChange = {
                                    audioOnly = it
                                }
                            )
                            Text(
                                text = stringResource(R.string.pref_video_mode_audio_only),
                                style = MaterialTheme.typography.body1.merge(),
                            )
                        }
                        Button(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val info = StreamInfo.getInfo(Vista.getService(0), feedUrl!!)
                                Logd(TAG, "info: $info")
                                val episode = episodeFromStreamInfo(info)
                                Logd(TAG, "episode: $episode")
                                addToYoutubeSyndicate(episode, !audioOnly)
                            }
                            onDismissRequest()
                        }) {
                            Text("Confirm")
                        }
                    }
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

    companion object {
        private val TAG: String = ShareReceiverActivity::class.simpleName ?: "Anonymous"

        const val ARG_FEEDURL: String = "arg.feedurl"
        private const val RESULT_ERROR = 2
    }
}
