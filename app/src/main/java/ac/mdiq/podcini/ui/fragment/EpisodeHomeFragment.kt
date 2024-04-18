package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeHomeFragmentBinding
import ac.mdiq.podcini.storage.model.feed.FeedItem
import android.speech.tts.TextToSpeech
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.*
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

    private var item: FeedItem? = null

    private lateinit var tts: TextToSpeech
    private lateinit var toolbar: MaterialToolbar

    private var disposable: Disposable? = null

    private var readerhtml: String? = null
    private var textContent: String? = null
    private var readMode = false
    private var ttsPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requireArguments().getSerializable(ARG_FEEDITEM, FeedItem::class.java)
        else requireArguments().getSerializable(ARG_FEEDITEM) as? FeedItem
        tts = TextToSpeech(requireContext(), this)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = EpisodeHomeFragmentBinding.inflate(inflater, container, false)
        Log.d(TAG, "fragment onCreateView")

        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.episode_home)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)

        if (item?.link != null) {
            showContent()
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
            // TTS initialization successful
            Log.i(TAG, "TTS init success with Locale: ${item?.feed?.language}")
            if (item?.feed?.language != null) {
                val result = tts.setLanguage(Locale(item!!.feed!!.language))
//                val result = tts.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS language not supported")
                    // Language not supported
                    // Handle the error or fallback to default behavior
                }
            }
        } else {
            // TTS initialization failed
            // Handle the error or fallback to default behavior
            Log.w(TAG, "TTS init failed")
        }
    }

    private fun showContent() {
        if (readMode) {
            if (readerhtml == null)   {
                runBlocking {
                    val url = item!!.link!!
                    val htmlSource = fetchHtmlSource(url)
                    val readability4J = Readability4J(item?.link!!, htmlSource)
                    val article = readability4J.parse()
                    textContent = article.textContent
//                    Log.d(TAG, "readability4J: ${article.textContent}")
                    readerhtml = article.contentWithDocumentsCharsetOrUtf8
                }
            }
            if (readerhtml != null) binding.webView.loadDataWithBaseURL(item!!.link!!, readerhtml!!, "text/html", "UTF-8", null)
        } else {
            if (item?.link != null) binding.webView.loadUrl(item!!.link!!)
        }
    }

    private suspend fun fetchHtmlSource(urlString: String): String = withContext(Dispatchers.IO) {
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

    override fun onPrepareOptionsMenu(menu: Menu) {
        val textSpeech = menu.findItem(R.id.text_speech)
        textSpeech.isVisible = readMode
        if (readMode) {
            if (ttsPlaying) textSpeech.setIcon(R.drawable.ic_pause)
            else textSpeech.setIcon(R.drawable.ic_play_24dp)
        }
    }

    @UnstableApi override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.switch_home -> {
                Log.d(TAG, "switch_home selected")
                switchMode()
                return true
            }
            R.id.text_speech -> {
                Log.d(TAG, "text_speech selected: $textContent")
                if (tts.isSpeaking) tts.stop()
                if (!ttsPlaying) {
                    ttsPlaying = true
                    if (textContent != null) {
                        val maxTextLength = 4000
                        var startIndex = 0
                        var endIndex = minOf(maxTextLength, textContent!!.length)
                        while (startIndex < textContent!!.length) {
                            val chunk = textContent!!.substring(startIndex, endIndex)
                            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)

                            startIndex += maxTextLength
                            endIndex = minOf(endIndex + maxTextLength, textContent!!.length)
                        }
                    }
                } else ttsPlaying = false

                updateAppearance()
                return true
            }
            R.id.share_notes -> {
                if (item == null) return false
                val notes = item!!.description
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
                if (item == null) return false
                return true
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
        tts.shutdown()
    }

    @UnstableApi private fun updateAppearance() {
        if (item == null) {
            Log.d(TAG, "updateAppearance item is null")
            return
        }
        onPrepareOptionsMenu(toolbar.menu)
//        FeedItemMenuHandler.onPrepareMenu(toolbar.menu, item, R.id.switch_home)
    }

    companion object {
        private const val TAG = "EpisodeWebviewFragment"
        private const val ARG_FEEDITEM = "feeditem"

        @JvmStatic
        fun newInstance(item: FeedItem): EpisodeHomeFragment {
            val fragment = EpisodeHomeFragment()
            val args = Bundle()
            args.putSerializable(ARG_FEEDITEM, item)
            fragment.arguments = args
            return fragment
        }
    }
}
