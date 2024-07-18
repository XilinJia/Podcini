package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeHomeFragmentBinding
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.storage.database.Episodes.persistEpisode
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*
import net.dankito.readability4j.extended.Readability4JExtended
import java.util.*

/**
 * Displays information about an Episode (FeedItem) and actions.
 */
class EpisodeHomeFragment : Fragment() {
    private var _binding: EpisodeHomeFragmentBinding? = null
    private val binding get() = _binding!!

    private var startIndex = 0
    private var ttsSpeed = 1.0f

    private lateinit var toolbar: MaterialToolbar

    private var readerText: String? = null
    private var cleanedNotes: String? = null
    private var readerhtml: String? = null
    private var readMode = true
    private var ttsPlaying = false
    private var jsEnabled = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = EpisodeHomeFragmentBinding.inflate(inflater, container, false)
        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        if (!episode?.link.isNullOrEmpty()) showContent()
        else {
            Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                    if (isEmpty) Logd(TAG, "content is empty")
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

    @OptIn(UnstableApi::class) private fun showReaderContent() {
        runOnIOScope {
            if (!episode?.link.isNullOrEmpty()) {
                if (cleanedNotes == null) {
                    if (episode?.transcript == null) {
                        val url = episode!!.link!!
                        val htmlSource = fetchHtmlSource(url)
                        val article = Readability4JExtended(episode?.link!!, htmlSource).parse()
                        readerText = article.textContent
//                    Log.d(TAG, "readability4J: ${article.textContent}")
                        readerhtml = article.contentWithDocumentsCharsetOrUtf8
                    } else {
                        readerhtml = episode!!.transcript
                        readerText = HtmlCompat.fromHtml(readerhtml!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    }
                    if (!readerhtml.isNullOrEmpty()) {
                        val shownotesCleaner = ShownotesCleaner(requireContext())
                        cleanedNotes = shownotesCleaner.processShownotes(readerhtml!!, 0)
                        episode!!.setTranscriptIfLonger(readerhtml)
                        persistEpisode(episode)
                    }
                }
            }
            if (!cleanedNotes.isNullOrEmpty()) {
                if (!ttsReady) initializeTTS(requireContext())
                withContext(Dispatchers.Main) {
                    binding.readerView.loadDataWithBaseURL("https://127.0.0.1",
                        cleanedNotes ?: "No notes",
                        "text/html",
                        "UTF-8",
                        null)
                    binding.readerView.visibility = View.VISIBLE
                    binding.webView.visibility = View.GONE
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun initializeTTS(context: Context) {
        Logd(TAG, "starting TTS")
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
                    ttsReady = true
//                    semaphore.release()
                    Logd(TAG, "TTS init success")
                } else {
                    Log.w(TAG, "TTS init failed")
                    requireActivity().runOnUiThread {Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private fun showWebContent() {
        if (!episode?.link.isNullOrEmpty()) {
            binding.webView.settings.javaScriptEnabled = jsEnabled
            Logd(TAG, "currentItem!!.link ${episode!!.link}")
            binding.webView.loadUrl(episode!!.link!!)
            binding.readerView.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
        } else Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
    }

    private fun showContent() {
        if (readMode) showReaderContent()
        else showWebContent()
    }

    private val menuProvider = object: MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
//            super.onPrepareMenu(menu)
            Logd(TAG, "onPrepareMenu called")
            val textSpeech = menu.findItem(R.id.text_speech)
            textSpeech?.isVisible = readMode && tts != null
            if (textSpeech?.isVisible == true) {
                if (ttsPlaying) textSpeech.setIcon(R.drawable.ic_pause) else textSpeech.setIcon(R.drawable.ic_play_24dp)
            }
            menu.findItem(R.id.share_notes)?.setVisible(readMode)
            menu.findItem(R.id.switchJS)?.setVisible(!readMode)
            val btn = menu.findItem(R.id.switch_home)
            if (readMode) btn?.setIcon(R.drawable.baseline_home_24)
            else btn?.setIcon(R.drawable.outline_home_24)
        }

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.episode_home, menu)
            onPrepareMenu(menu)
        }

        @OptIn(UnstableApi::class) override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.switch_home -> {
                    switchMode()
                    return true
                }
                R.id.switchJS -> {
                    jsEnabled = !jsEnabled
                    showWebContent()
                    return true
                }
                R.id.text_speech -> {
                    Logd(TAG, "text_speech selected: $readerText")
                    if (tts != null) {
                        if (tts!!.isSpeaking) tts?.stop()
                        if (!ttsPlaying) {
                            ttsPlaying = true
                            if (!readerText.isNullOrEmpty()) {
                                ttsSpeed = episode?.feed?.preferences?.playSpeed ?: 1.0f
                                tts?.setSpeechRate(ttsSpeed)
                                while (startIndex < readerText!!.length) {
                                    val endIndex = minOf(startIndex + MAX_CHUNK_LENGTH, readerText!!.length)
                                    val chunk = readerText!!.substring(startIndex, endIndex)
                                    tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                    startIndex += MAX_CHUNK_LENGTH
                                }
                            }
                        } else ttsPlaying = false
                        updateAppearance()
                    } else Toast.makeText(context, R.string.tts_not_available, Toast.LENGTH_LONG).show()

                    return true
                }
                R.id.share_notes -> {
                    val notes = readerhtml
                    if (!notes.isNullOrEmpty()) {
                        val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
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
                    return episode != null
                }
            }
        }
    }

    @UnstableApi override fun onResume() {
        super.onResume()
        updateAppearance()
    }

    private fun cleatWebview(webview: WebView) {
        binding.root.removeView(webview)
        webview.clearHistory()
        webview.clearCache(true)
        webview.clearView()
        webview.destroy()
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        cleatWebview(binding.webView)
        cleatWebview(binding.readerView)
        _binding = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroyView()
    }

    @UnstableApi private fun updateAppearance() {
        if (episode == null) {
            Logd(TAG, "updateAppearance currentItem is null")
            return
        }
//        onPrepareOptionsMenu(toolbar.menu)
        toolbar.invalidateMenu()
//        menuProvider.onPrepareMenu(toolbar.menu)
    }

    companion object {
        private val TAG: String = EpisodeHomeFragment::class.simpleName ?: "Anonymous"
        private const val MAX_CHUNK_LENGTH = 2000

        var episode: Episode? = null    // unmanged

        fun newInstance(item: Episode): EpisodeHomeFragment {
            val fragment = EpisodeHomeFragment()
            Logd(TAG, "item.itemIdentifier ${item.identifier}")
            if (item.identifier != episode?.identifier) episode = item
            return fragment
        }
    }
}
