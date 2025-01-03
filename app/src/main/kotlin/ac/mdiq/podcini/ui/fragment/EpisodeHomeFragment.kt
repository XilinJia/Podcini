package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import java.io.File
import java.util.Locale

class EpisodeHomeFragment : Fragment() {
    private var startIndex = 0
    private var ttsSpeed = 1.0f

    private var readerText: String? = null
    private var cleanedNotes by mutableStateOf<String?>(null)
    private var readerhtml: String? = null
    private var readMode by mutableStateOf(true)

    private var ttsPlaying by mutableStateOf(false)
    private var jsEnabled by mutableStateOf(false)
    private var webUrl by mutableStateOf("")

    private var tts by mutableStateOf<TextToSpeech?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { HomeView() } } }

        if (!episode?.link.isNullOrEmpty()) prepareContent()
        else {
            Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
        return composeView
    }

    @Composable
    fun HomeView() {
        fun Color.toHex(): String {
            val red = (red * 255).toInt().toString(16).padStart(2, '0')
            val green = (green * 255).toInt().toString(16).padStart(2, '0')
            val blue = (blue * 255).toInt().toString(16).padStart(2, '0')
            return "#$red$green$blue"
        }
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            if (readMode) {
                val backgroundColor = MaterialTheme.colorScheme.background.toHex()
                val textColor = MaterialTheme.colorScheme.onBackground.toHex()
                val primaryColor = MaterialTheme.colorScheme.primary.toHex()
                AndroidView(modifier = Modifier.padding(innerPadding).fillMaxSize(), factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = jsEnabled
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                                if (isEmpty) Logd(TAG, "content is empty")
                                view?.evaluateJavascript("document.querySelectorAll('[hidden]').forEach(el => el.removeAttribute('hidden'));", null)
                            }
                        }
                    }
                }, update = { webView ->
                    webView.settings.javaScriptEnabled = jsEnabled
                    val htmlContent = """
                            <html>
                                <style>
                                    body {
                                        background-color: $backgroundColor;
                                        color: $textColor;
                                    }
                                    a {
                                        color: ${primaryColor};
                                    }
                                </style>
                                <body>${cleanedNotes ?: "No notes"}</body>
                            </html>
                        """.trimIndent()
                    webView.loadDataWithBaseURL("about:blank", htmlContent, "text/html", "utf-8", null)
                })
            } else
                AndroidView(modifier = Modifier.padding(innerPadding).fillMaxSize(), factory = {
                    WebView(it).apply {
                        settings.javaScriptEnabled = jsEnabled
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                                if (isEmpty) Logd(TAG, "content is empty")
                            }
                        }
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(true)
                    }
                }, update = {
                    it.settings.javaScriptEnabled = jsEnabled
                    it.loadUrl(webUrl)
                })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                if (readMode && tts != null) {
                    val iconRes = if (ttsPlaying) R.drawable.ic_pause else R.drawable.ic_play_24dp
                    IconButton(onClick = {
                        if (tts!!.isSpeaking) tts?.stop()
                        if (!ttsPlaying) {
                            ttsPlaying = true
                            if (!readerText.isNullOrEmpty()) {
                                ttsSpeed = episode?.feed?.playSpeed ?: 1.0f
                                tts?.setSpeechRate(ttsSpeed)
                                while (startIndex < readerText!!.length) {
                                    val endIndex = minOf(startIndex + MAX_CHUNK_LENGTH, readerText!!.length)
                                    val chunk = readerText!!.substring(startIndex, endIndex)
                                    tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                    startIndex += MAX_CHUNK_LENGTH
                                }
                            }
                        } else ttsPlaying = false
                    }) { Icon(imageVector = ImageVector.vectorResource(iconRes), contentDescription = "home") }
                }
                var showJSIconRes = if (readMode) R.drawable.outline_eyeglasses_24 else R.drawable.javascript_icon_245402
                IconButton(onClick = { jsEnabled = !jsEnabled }) { Icon(imageVector = ImageVector.vectorResource(showJSIconRes), contentDescription = "JS") }
                var homeIconRes = if (readMode) R.drawable.baseline_home_24 else R.drawable.outline_home_24
                IconButton(onClick = {
                    readMode = !readMode
                    jsEnabled = false
                    prepareContent()
                }) { Icon(imageVector = ImageVector.vectorResource(homeIconRes), contentDescription = "switch home") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (readMode && !readerhtml.isNullOrEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = readerhtml!!
                        val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                        val context = requireContext()
                        val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                        context.startActivity(intent)
                        expanded = false
                    })
                }
            }
        )
    }

    private fun prepareContent() {
        when {
            readMode -> {
                runOnIOScope {
                    if (!episode?.link.isNullOrEmpty()) {
                        if (cleanedNotes == null) {
                            if (episode?.transcript == null) {
                                val url = episode!!.link!!
                                val htmlSource = fetchHtmlSource(url)
                                val article = Readability4JExtended(episode?.link!!, htmlSource).parse()
                                readerText = article.textContent
                                readerhtml = article.contentWithDocumentsCharsetOrUtf8
                            } else {
                                readerhtml = episode!!.transcript
                                readerText = HtmlCompat.fromHtml(readerhtml!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            }
                            if (!readerhtml.isNullOrEmpty()) {
                                val shownotesCleaner = ShownotesCleaner(requireContext())
                                cleanedNotes = shownotesCleaner.processShownotes(readerhtml!!, 0)
                                episode = upsertBlk(episode!!) { it.setTranscriptIfLonger(readerhtml) }
                            }
                        }
                    }
                    if (!cleanedNotes.isNullOrEmpty()) {
                        val file = File(context?.filesDir, "test_content.html")
                        file.writeText(cleanedNotes ?: "No content")
                        if (tts == null) {
                            tts = TextToSpeech(context) { status: Int ->
                                if (status == TextToSpeech.SUCCESS) {
                                    if (episode?.feed?.language != null) {
                                        val result = tts?.setLanguage(Locale(episode!!.feed!!.language!!))
                                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                            Log.w(TAG, "TTS language not supported ${episode?.feed?.language}")
                                            requireActivity().runOnUiThread {
                                                Toast.makeText(context, getString(R.string.language_not_supported_by_tts) + " ${episode?.feed?.language}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    Logd(TAG, "TTS init success")
                                } else {
                                    Log.w(TAG, "TTS init failed")
                                    requireActivity().runOnUiThread { Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show() }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            readMode = true
                            Logd(TAG, "cleanedNotes: $cleanedNotes")
                        }
                    } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
                }
            }
            !episode?.link.isNullOrEmpty() -> {
                webUrl = episode!!.link!!
                readMode = false
            }
            else -> Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroyView()
    }

    companion object {
        private val TAG: String = EpisodeHomeFragment::class.simpleName ?: "Anonymous"
        private const val MAX_CHUNK_LENGTH = 2000

        var episode: Episode? = null    // unmanged

        fun newInstance(item: Episode): EpisodeHomeFragment {
            val fragment = EpisodeHomeFragment()
            Logd(TAG, "item.identifyingValue ${item.identifyingValue}")
            if (item.identifyingValue != episode?.identifyingValue) episode = item
            return fragment
        }
    }
}
