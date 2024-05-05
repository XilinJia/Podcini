package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.getMediafilePath
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.getMediafilename
import ac.mdiq.podcini.playback.AudioMediaOperation.MergeAudios
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.DBWriter.persistFeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.ui.fragment.FeedItemlistFragment.Companion.tts
import ac.mdiq.podcini.ui.fragment.FeedItemlistFragment.Companion.ttsReady
import ac.mdiq.podcini.ui.fragment.FeedItemlistFragment.Companion.ttsWorking
import ac.mdiq.podcini.util.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.util.event.FeedItemEvent.Companion.updated
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.getMaxSpeechInputLength
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.text.HtmlCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import net.dankito.readability4j.Readability4J
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

class TTSActionButton(item: FeedItem) : ItemActionButton(item) {

    private var readerText: String? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun getLabel(): Int {
        return R.string.TTS_label
    }
    override fun getDrawable(): Int {
        return R.drawable.text_to_speech
    }

    @OptIn(UnstableApi::class) override fun onClick(context: Context) {
        Log.d("TTSActionButton", "onClick called")
        if (item.link.isNullOrEmpty()) {
            Toast.makeText(context, R.string.episode_has_no_content, Toast.LENGTH_LONG).show()
            return
        }
        processing = 0.01f
        item.setBuilding()
        EventBus.getDefault().post(updated(item))
        ioScope.launch {
            if (item.transcript == null) {
                runBlocking {
                    val url = item.link!!
                    val htmlSource = fetchHtmlSource(url)
                    val article = Readability4J(item.link!!, htmlSource).parse()
                    readerText = article.textContent
                    item.setTranscriptIfLonger(article.contentWithDocumentsCharsetOrUtf8)
                    persistFeedItem(item)
                    Log.d(TAG, "readability4J: ${readerText?.substring(max(0, readerText!!.length-100), readerText!!.length)}")
                }
            } else readerText = HtmlCompat.fromHtml(item.transcript!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            processing = 0.1f
            EventBus.getDefault().post(updated(item))
            if (!readerText.isNullOrEmpty()) {
                while (!ttsReady) Thread.sleep(100)

                processing = 0.15f
                EventBus.getDefault().post(updated(item))
                while (ttsWorking) Thread.sleep(100)
                ttsWorking = true
                if (item.feed?.language != null) {
                    val result = tts?.setLanguage(Locale(item.feed!!.language!!))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "TTS language not supported ${item.feed?.language} $result")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.language_not_supported_by_tts) + " ${item.feed?.language} $result", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                var j = 0
                val mediaFile = File(getMediafilePath(item), getMediafilename(item))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        j++
                        Log.d(TAG, "onDone ${mediaFile.length()} $utteranceId")
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "onError utterance error: $utteranceId")
                        Log.e(TAG, "onError $readerText")
                    }
                    override fun onError(utteranceId: String, errorCode: Int) {
                        Log.e(TAG, "onError1 utterance error: $utteranceId $errorCode")
                        Log.e(TAG, "onError1 $readerText")
                    }
                })

                Log.d(TAG, "readerText: ${readerText?.length}")
                var startIndex = 0
                var i = 0
                val parts = mutableListOf<String>()
                val chunkLength = getMaxSpeechInputLength()
                var status = TextToSpeech.ERROR
                while (startIndex < readerText!!.length) {
                    Log.d(TAG, "working on chunk $i $startIndex")
                    val endIndex = minOf(startIndex + chunkLength, readerText!!.length)
                    val chunk = readerText!!.substring(startIndex, endIndex)
                    val tempFile = File.createTempFile("tts_temp_${i}_", ".wav")
                    parts.add(tempFile.absolutePath)
                    status = tts?.synthesizeToFile(chunk, null, tempFile, tempFile.absolutePath) ?: 0
                    Log.d(TAG, "status: $status chunk: ${chunk.substring(0, min(80, chunk.length))}")
                    if (status == TextToSpeech.ERROR) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error generating audio file $tempFile.absolutePath", Toast.LENGTH_LONG).show()
                        }
                        break
                    }
                    startIndex += chunkLength
                    i++
                    while (i-j > 0) Thread.sleep(100)
                    processing = 0.15f + 0.7f * startIndex / readerText!!.length
                    EventBus.getDefault().post(updated(item))
                }
                processing = 0.85f
                EventBus.getDefault().post(updated(item))
                if (status == TextToSpeech.SUCCESS) {
                    MergeAudios(parts.toTypedArray(), mediaFile.absolutePath, null)

//                if (mediaFile.exists()) mediaFile.delete()
//                if (!mediaFile.exists()) {
//                    try {
//                        mediaFile.createNewFile()
//                    } catch (e: IOException) {
//                        Log.e(TAG, "Unable to create file")
//                    }
//                }
                    val mFilename = mediaFile.absolutePath
                    Log.d(TAG, "saving TTS to file $mFilename")
                    val media = FeedMedia(item, null, 0, "audio/*")
                    media.setFile_url(mFilename)
                    media.setDownloaded(true)
                    item.setMedia(media)
                    DBWriter.persistFeedMedia(media)
                    item.setTranscriptIfLonger(readerText)
                    persistFeedItem(item)
                }
                for (p in parts) {
                    val f = File(p)
                    f.delete()
                }
                ttsWorking = false
            } else Toast.makeText(context, R.string.episode_has_no_content, Toast.LENGTH_LONG).show()

            item.setPlayed(false)
            processing = 1f
            EventBus.getDefault().post(updated(item))
        }
    }

    override val visibility: Int
        get() = if (item.link.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
}
