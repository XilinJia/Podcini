package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeHomeFragmentBinding
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Html
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.*


/**
 * Displays information about an Episode (FeedItem) and actions.
 */
class EpisodeHomeFragment : Fragment(), Toolbar.OnMenuItemClickListener, TextToSpeech.OnInitListener {
    private var _binding: EpisodeHomeFragmentBinding? = null
    private val binding get() = _binding!!

//    private var item: FeedItem? = null

    private var startIndex = 0
    private var tts: TextToSpeech? = null
    private var ttsSpeed = 1.0f

    private lateinit var toolbar: MaterialToolbar

    private var disposable: Disposable? = null

    private var readerhtml: String? = null
    private var readMode = false
    private var ttsPlaying = false
    private var jsEnabled = false

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = EpisodeHomeFragmentBinding.inflate(inflater, container, false)
        Log.d(TAG, "fragment onCreateView")

        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.episode_home)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)

        if (!currentItem?.link.isNullOrEmpty()) showContent()
        else {
            Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }

        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                    if (isEmpty) {
                        Log.d(TAG, "content is empty")
                    }
                }
            }
        }

        updateAppearance()
        return binding.root
    }

    @OptIn(UnstableApi::class) private fun switchMode() {
        readMode = !readMode
        showContent()
        updateAppearance()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.i(TAG, "TTS init success with Locale: ${currentItem?.feed?.language}")
            if (currentItem?.feed?.language != null) {
                val result = tts?.setLanguage(Locale(currentItem!!.feed!!.language!!))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS language not supported ${currentItem?.feed?.language}")
                    Toast.makeText(context, R.string.language_not_supported_by_tts, Toast.LENGTH_LONG).show()
                }
                ttsSpeed = currentItem?.feed?.preferences?.feedPlaybackSpeed ?: 1.0f
                tts?.setSpeechRate(ttsSpeed)
            }
        } else {
            Log.w(TAG, "TTS init failed")
            Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showReaderContent() {
        if (!currentItem?.link.isNullOrEmpty()) {
            if (cleanedNotes == null) {
                runBlocking {
                    val url = currentItem!!.link!!
                    val htmlSource = fetchHtmlSource(url)
                    val readability4J = Readability4J(currentItem?.link!!, htmlSource)
                    val article = readability4J.parse()
                    readerText = article.textContent
//                    Log.d(TAG, "readability4J: ${article.textContent}")
                    readerhtml = article.contentWithDocumentsCharsetOrUtf8
                    if (!readerhtml.isNullOrEmpty()) {
                        val shownotesCleaner = ShownotesCleaner(requireContext(), readerhtml!!, 0)
                        cleanedNotes = shownotesCleaner.processShownotes()
                    }
                }
            }
        }
        if (!cleanedNotes.isNullOrEmpty()) {
            if (tts == null) tts = TextToSpeech(requireContext(), this)
            binding.readerView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes!!, "text/html", "UTF-8", null)
            binding.readerView.visibility = View.VISIBLE
            binding.webView.visibility = View.GONE
        } else Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
    }

    private fun showWebContent() {
        if (!currentItem?.link.isNullOrEmpty()) {
            binding.webView.settings.javaScriptEnabled = jsEnabled
            Log.d(TAG, "currentItem!!.link ${currentItem!!.link}")
            binding.webView.loadUrl(currentItem!!.link!!)
            binding.readerView.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
        } else Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
    }

    private fun showContent() {
        if (readMode) showReaderContent()
        else showWebContent()
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        val textSpeech = menu.findItem(R.id.text_speech)
        textSpeech.isVisible = readMode
        if (readMode) {
            if (ttsPlaying) textSpeech.setIcon(R.drawable.ic_pause) else textSpeech.setIcon(R.drawable.ic_play_24dp)
        }
        menu.findItem(R.id.share_notes).setVisible(readMode)
    }

    @UnstableApi override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.switch_home -> {
                Log.d(TAG, "switch_home selected")
                switchMode()
                return true
            }
            R.id.switchJS -> {
                Log.d(TAG, "switchJS selected")
                jsEnabled = !jsEnabled
                showWebContent()
                return true
            }

            R.id.text_speech -> {
                Log.d(TAG, "text_speech selected: $readerText")
                if (tts != null) {
                    if (tts!!.isSpeaking) tts?.stop()
                    if (!ttsPlaying) {
                        ttsPlaying = true
                        if (!readerText.isNullOrEmpty()) {
                            tts?.setSpeechRate(ttsSpeed)
                            while (startIndex < readerText!!.length) {
                                val endIndex = minOf(startIndex + maxChunkLength, readerText!!.length)
                                val chunk = readerText!!.substring(startIndex, endIndex)
                                tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                startIndex += maxChunkLength
                            }
                        }
                    } else ttsPlaying = false
                    updateAppearance()
                }
                return true
            }
            R.id.share_notes -> {
                val notes = readerhtml
                if (!notes.isNullOrEmpty()) {
                    val shareText = if (Build.VERSION.SDK_INT >= 24) Html.fromHtml(notes, Html.FROM_HTML_MODE_LEGACY).toString()
                    else Html.fromHtml(notes).toString()
                    val context = requireContext()
                    val intent = ShareCompat.IntentBuilder(context)
                        .setType("text/plain")
                        .setText(shareText)
                        .setChooserTitle(R.string.share_notes_label)
                        .createChooserIntent()
                    context.startActivity(intent)
                }
                return true
            }
            else -> {
                return currentItem != null
            }
        }
    }

    @UnstableApi override fun onResume() {
        super.onResume()
        updateAppearance()
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
        disposable?.dispose()
        tts?.shutdown()
    }

    @UnstableApi private fun updateAppearance() {
        if (currentItem == null) {
            Log.d(TAG, "updateAppearance currentItem is null")
            return
        }
        onPrepareOptionsMenu(toolbar.menu)
//        FeedItemMenuHandler.onPrepareMenu(toolbar.menu, currentItem, R.id.switch_home)
    }

    companion object {
        private const val TAG = "EpisodeHomeFragment"
        private const val ARG_FEEDITEM = "feeditem"

        const val maxChunkLength = 200

        private var readerText: String? = null
        private var cleanedNotes: String? = null
        var currentItem: FeedItem? = null

        @JvmStatic
        fun newInstance(item: FeedItem): EpisodeHomeFragment {
            val fragment = EpisodeHomeFragment()
//            val args = Bundle()
            Log.d(TAG, "item.itemIdentifier ${item.itemIdentifier}")
            if (item.itemIdentifier != currentItem?.itemIdentifier) {
                currentItem = item
                cleanedNotes = null
                readerText = null
            } else {
                currentItem?.feed = item.feed
            }
//            args.putSerializable(ARG_FEEDITEM, item)
//            fragment.arguments = args
            return fragment
        }

        suspend fun fetchHtmlSource(urlString: String): String = withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection()
            val inputStream = connection.getInputStream()
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))

            val stringBuilder = StringBuilder()
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }

            bufferedReader.close()
            inputStream.close()

            stringBuilder.toString()
        }

    }
}
