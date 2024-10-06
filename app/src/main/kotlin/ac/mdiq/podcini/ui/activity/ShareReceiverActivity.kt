package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.confirmAddYoutubeEpisode
import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.services.youtube.YoutubeParsingHelper.isYoutubeServiceURL
import ac.mdiq.vista.extractor.services.youtube.YoutubeParsingHelper.isYoutubeURL
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URL
import java.net.URLDecoder

class ShareReceiverActivity : AppCompatActivity() {
    private var sharedUrl: String? = null

    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val url = URL(sharedUrl)
        when {
//            plain text
            sharedUrl!!.matches(Regex("^[^\\s<>/]+\$")) -> {
                val intent = MainActivity.showOnlineSearch(this, sharedUrl!!)
                startActivity(intent)
                finish()
            }
//            Youtube media
//            sharedUrl!!.startsWith("https://youtube.com/watch?") || sharedUrl!!.startsWith("https://www.youtube.com/watch?") || sharedUrl!!.startsWith("https://music.youtube.com/watch?") -> {
            (isYoutubeURL(url) && url.path.startsWith("/watch")) || isYoutubeServiceURL(url) -> {
                Logd(TAG, "got youtube media")
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(this@ShareReceiverActivity) {
                        confirmAddYoutubeEpisode(listOf(sharedUrl!!), showDialog.value, onDismissRequest = {
                            showDialog.value = false
                            finish()
                        })
                    }
                }
            }
//            podcast or Youtube channel, Youtube playlist, or other?
            else -> {
                Logd(TAG, "Activity was started with url $sharedUrl")
                val intent = MainActivity.showOnlineFeed(this, sharedUrl!!)
//                intent.putExtra(MainActivity.Extras.started_from_share.name, getIntent().getBooleanExtra(MainActivity.Extras.started_from_share.name, false))
                startActivity(intent)
                finish()
            }
        }
    }

//    @Composable
//    fun confirmAddEpisode(sharedUrl: String, showDialog: Boolean, onDismissRequest: () -> Unit) {
//        var showToast by remember { mutableStateOf(false) }
//        var toastMassege by remember { mutableStateOf("")}
//        if (showToast) CustomToast(message = toastMassege, onDismiss = { showToast = false })
//
//        if (showDialog) {
//            Dialog(onDismissRequest = { onDismissRequest() }) {
//                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
//                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
//                        var audioOnly by remember { mutableStateOf(false) }
//                        Row(Modifier.fillMaxWidth()) {
//                            Checkbox(checked = audioOnly, onCheckedChange = { audioOnly = it })
//                            Text(text = stringResource(R.string.pref_video_mode_audio_only), style = MaterialTheme.typography.bodyLarge.merge())
//                        }
//                        Button(onClick = {
//                            CoroutineScope(Dispatchers.IO).launch {
//                                try {
//                                    val info = StreamInfo.getInfo(Vista.getService(0), sharedUrl)
//                                    Logd(TAG, "info: $info")
//                                    val episode = episodeFromStreamInfo(info)
//                                    Logd(TAG, "episode: $episode")
//                                    addToYoutubeSyndicate(episode, !audioOnly)
//                                } catch (e: Throwable) {
//                                    toastMassege = "Receive share error: ${e.message}"
//                                    Log.e(TAG, toastMassege)
//                                    showToast = true
//                                }
//                            }
//                            onDismissRequest()
//                        }) {
//                            Text("Confirm")
//                        }
//                    }
//                }
//            }
//        }
//    }

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
