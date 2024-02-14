package de.danoeh.antennapod.ui.echo

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils.getCurrentPlaybackSpeed
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBReader.StatisticsResult
import de.danoeh.antennapod.core.storage.StatisticsItem
import de.danoeh.antennapod.core.util.Converter.getDurationStringLocalized
import de.danoeh.antennapod.storage.preferences.UserPreferences.getDataFolder
import de.danoeh.antennapod.storage.preferences.UserPreferences.timeRespectsSpeed
import de.danoeh.antennapod.ui.echo.databinding.EchoActivityBinding
import de.danoeh.antennapod.ui.echo.screens.*
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class EchoActivity : AppCompatActivity() {
    private var viewBinding: EchoActivityBinding? = null
    private var currentScreen = -1
    private var progressPaused = false
    private var progress = 0f
    private var currentDrawable: Drawable? = null
    private var echoProgress: EchoProgress? = null
    private var redrawTimer: Disposable? = null
    private var timeTouchDown: Long = 0
    private var timeLastFrame: Long = 0
    private var disposable: Disposable? = null
    private var disposableFavorite: Disposable? = null

    private var totalTime: Long = 0
    private var totalActivePodcasts = 0
    private var playedPodcasts = 0
    private var playedActivePodcasts = 0
    private var randomUnplayedActivePodcast = ""
    private var queueNumEpisodes = 0
    private var queueSecondsLeft: Long = 0
    private var timeBetweenReleaseAndPlay: Long = 0
    private var oldestDate: Long = 0
    private val favoritePodNames = ArrayList<String>()
    private val favoritePodImages = ArrayList<Drawable>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        viewBinding = EchoActivityBinding.inflate(layoutInflater)
        viewBinding!!.closeButton.setOnClickListener { v: View? -> finish() }
        viewBinding!!.shareButton.setOnClickListener { v: View? -> share() }
        viewBinding!!.echoImage.setOnTouchListener { v: View?, event: MotionEvent ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                progressPaused = true
                timeTouchDown = System.currentTimeMillis()
            } else if (event.action == KeyEvent.ACTION_UP) {
                progressPaused = false
                if (timeTouchDown + 500 > System.currentTimeMillis()) {
                    val newScreen: Int
                    if (event.x < 0.5f * viewBinding!!.echoImage.measuredWidth) {
                        newScreen = max((currentScreen - 1).toDouble(), 0.0).toInt()
                    } else {
                        newScreen = min((currentScreen + 1).toDouble(), (NUM_SCREENS - 1).toDouble())
                            .toInt()
                        if (currentScreen == NUM_SCREENS - 1) {
                            finish()
                        }
                    }
                    progress = newScreen.toFloat()
                    echoProgress!!.setProgress(progress)
                    loadScreen(newScreen, false)
                }
            }
            true
        }
        echoProgress = EchoProgress(NUM_SCREENS)
        viewBinding!!.echoProgressImage.setImageDrawable(echoProgress)
        setContentView(viewBinding!!.root)
        loadScreen(0, false)
        loadStatistics()
    }

    private fun share() {
        try {
            val bitmap = Bitmap.createBitmap(SHARE_SIZE, SHARE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            currentDrawable!!.setBounds(0, 0, canvas.width, canvas.height)
            currentDrawable!!.draw(canvas)
            viewBinding!!.echoImage.setImageDrawable(null)
            viewBinding!!.echoImage.setImageDrawable(currentDrawable)
            val file = File(getDataFolder(null), "AntennaPodEcho.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.close()

            val fileUri = FileProvider.getUriForFile(this, getString(R.string.provider_authority), file)
            IntentBuilder(this)
                .setType("image/png")
                .addStream(fileUri)
                .setText(getString(R.string.echo_share, RELEASE_YEAR))
                .setChooserTitle(R.string.share_file_label)
                .startChooser()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()

        redrawTimer = Flowable.timer(20, TimeUnit.MILLISECONDS)
            .observeOn(Schedulers.io())
            .repeat()
            .subscribe { i: Long? ->
                if (progressPaused) {
                    return@subscribe
                }
                viewBinding!!.echoImage.postInvalidate()
                if (progress >= NUM_SCREENS - 0.001f) {
                    return@subscribe
                }
                var timePassed = System.currentTimeMillis() - timeLastFrame
                timeLastFrame = System.currentTimeMillis()
                if (timePassed > 500) {
                    timePassed = 0
                }
                progress = min((NUM_SCREENS - 0.001f).toDouble(), (progress + timePassed / 10000.0f).toDouble())
                    .toFloat()
                echoProgress!!.setProgress(progress)
                viewBinding!!.echoProgressImage.postInvalidate()
                loadScreen(progress.toInt(), false)
            }
    }

    override fun onStop() {
        super.onStop()
        redrawTimer!!.dispose()
        if (disposable != null) {
            disposable!!.dispose()
        }
        if (disposableFavorite != null) {
            disposableFavorite!!.dispose()
        }
    }

    private fun loadScreen(screen: Int, force: Boolean) {
        if (screen == currentScreen && !force) {
            return
        }
        currentScreen = screen
        runOnUiThread {
            viewBinding!!.echoLogo.visibility = if (currentScreen == 0) View.VISIBLE else View.GONE
            viewBinding!!.shareButton.visibility = if (currentScreen == 6) View.VISIBLE else View.GONE

            when (currentScreen) {
                0 -> {
                    viewBinding!!.aboveLabel.setText(R.string.echo_intro_your_year)
                    viewBinding!!.largeLabel.text = String.format(echoLanguage, "%d", RELEASE_YEAR)
                    viewBinding!!.belowLabel.setText(R.string.echo_intro_in_podcasts)
                    viewBinding!!.smallLabel.setText(R.string.echo_intro_locally)
                    currentDrawable = BubbleScreen(this)
                }
                1 -> {
                    viewBinding!!.aboveLabel.setText(R.string.echo_hours_this_year)
                    viewBinding!!.largeLabel.text = String.format(echoLanguage, "%d", totalTime / 3600)
                    viewBinding!!.belowLabel.text = resources
                        .getQuantityString(R.plurals.echo_hours_podcasts, playedPodcasts, playedPodcasts)
                    viewBinding!!.smallLabel.text = ""
                    currentDrawable = WaveformScreen(this)
                }
                2 -> {
                    viewBinding!!.largeLabel.text = String.format(echoLanguage, "%d", queueSecondsLeft / 3600)
                    viewBinding!!.belowLabel.text = resources.getQuantityString(
                        R.plurals.echo_queue_hours_waiting, queueNumEpisodes, queueNumEpisodes)
                    val dec31 = Calendar.getInstance()
                    dec31[Calendar.DAY_OF_MONTH] = 31
                    dec31[Calendar.MONTH] = Calendar.DECEMBER
                    val daysUntilNextYear = max(1.0,
                        (dec31[Calendar.DAY_OF_YEAR] - Calendar.getInstance()[Calendar.DAY_OF_YEAR] + 1).toDouble()).toInt()
                    val secondsPerDay = queueSecondsLeft / daysUntilNextYear
                    val timePerDay = getDurationStringLocalized(
                        getLocalizedResources(this, echoLanguage), secondsPerDay * 1000)
                    val hoursPerDay = (secondsPerDay / 3600).toDouble()
                    val nextYear = RELEASE_YEAR + 1
                    if (hoursPerDay < 1.5) {
                        viewBinding!!.aboveLabel.setText(R.string.echo_queue_title_clean)
                        viewBinding!!.smallLabel.text = getString(R.string.echo_queue_hours_clean, timePerDay, nextYear)
                    } else if (hoursPerDay <= 24) {
                        viewBinding!!.aboveLabel.setText(R.string.echo_queue_title_many)
                        viewBinding!!.smallLabel.text =
                            getString(R.string.echo_queue_hours_normal, timePerDay, nextYear)
                    } else {
                        viewBinding!!.aboveLabel.setText(R.string.echo_queue_title_many)
                        viewBinding!!.smallLabel.text = getString(R.string.echo_queue_hours_much, timePerDay, nextYear)
                    }
                    currentDrawable = StripesScreen(this)
                }
                3 -> {
                    viewBinding!!.aboveLabel.setText(R.string.echo_listened_after_title)
                    if (timeBetweenReleaseAndPlay <= 1000L * 3600 * 24 * 2.5) {
                        viewBinding!!.largeLabel.setText(R.string.echo_listened_after_emoji_run)
                        viewBinding!!.belowLabel.setText(R.string.echo_listened_after_comment_addict)
                    } else {
                        viewBinding!!.largeLabel.setText(R.string.echo_listened_after_emoji_yoga)
                        viewBinding!!.belowLabel.setText(R.string.echo_listened_after_comment_easy)
                    }
                    viewBinding!!.smallLabel.text = getString(R.string.echo_listened_after_time,
                        getDurationStringLocalized(
                            getLocalizedResources(this, echoLanguage), timeBetweenReleaseAndPlay))
                    currentDrawable = RotatingSquaresScreen(this)
                }
                4 -> {
                    viewBinding!!.aboveLabel.setText(R.string.echo_hoarder_title)
                    val percentagePlayed = (100.0 * playedActivePodcasts / totalActivePodcasts).toInt()
                    if (percentagePlayed < 25) {
                        viewBinding!!.largeLabel.setText(R.string.echo_hoarder_emoji_cabinet)
                        viewBinding!!.belowLabel.setText(R.string.echo_hoarder_subtitle_hoarder)
                        viewBinding!!.smallLabel.text = getString(R.string.echo_hoarder_comment_hoarder,
                            percentagePlayed, totalActivePodcasts)
                    } else if (percentagePlayed < 75) {
                        viewBinding!!.largeLabel.setText(R.string.echo_hoarder_emoji_check)
                        viewBinding!!.belowLabel.setText(R.string.echo_hoarder_subtitle_medium)
                        viewBinding!!.smallLabel.text = getString(R.string.echo_hoarder_comment_medium,
                            percentagePlayed, totalActivePodcasts, randomUnplayedActivePodcast)
                    } else {
                        viewBinding!!.largeLabel.setText(R.string.echo_hoarder_emoji_clean)
                        viewBinding!!.belowLabel.setText(R.string.echo_hoarder_subtitle_clean)
                        viewBinding!!.smallLabel.text = getString(R.string.echo_hoarder_comment_clean,
                            percentagePlayed, totalActivePodcasts)
                    }
                    currentDrawable = WavesScreen(this)
                }
                5 -> {
                    viewBinding!!.aboveLabel.text = ""
                    viewBinding!!.largeLabel.setText(R.string.echo_thanks_large)
                    if (oldestDate < jan1()) {
                        val skeleton = DateFormat.getBestDateTimePattern(echoLanguage, "MMMM yyyy")
                        val dateFormat = SimpleDateFormat(skeleton, echoLanguage)
                        val dateFrom = dateFormat.format(Date(oldestDate))
                        viewBinding!!.belowLabel.text = getString(R.string.echo_thanks_we_are_glad_old, dateFrom)
                    } else {
                        viewBinding!!.belowLabel.setText(R.string.echo_thanks_we_are_glad_new)
                    }
                    viewBinding!!.smallLabel.setText(R.string.echo_thanks_now_favorite)
                    currentDrawable = RotatingSquaresScreen(this)
                }
                6 -> {
                    viewBinding!!.aboveLabel.text = ""
                    viewBinding!!.largeLabel.text = ""
                    viewBinding!!.belowLabel.text = ""
                    viewBinding!!.smallLabel.text = ""
                    currentDrawable = FinalShareScreen(this, favoritePodNames, favoritePodImages)
                }
                else -> {}
            }
            viewBinding!!.echoImage.setImageDrawable(currentDrawable)
        }
    }

    private val echoLanguage: Locale
        get() {
            val hasTranslation = getString(R.string.echo_listened_after_title) != getLocalizedResources(this,
                Locale.US).getString(R.string.echo_listened_after_title)
            return if (hasTranslation) {
                Locale.getDefault()
            } else {
                Locale.US
            }
        }

    private fun getLocalizedResources(context: Context, desiredLocale: Locale): Resources {
        var conf = context.resources.configuration
        conf = Configuration(conf)
        conf.setLocale(desiredLocale)
        val localizedContext = context.createConfigurationContext(conf)
        return localizedContext.resources
    }

    private fun jan1(): Long {
        val date = Calendar.getInstance()
        date[Calendar.HOUR_OF_DAY] = 0
        date[Calendar.MINUTE] = 0
        date[Calendar.SECOND] = 0
        date[Calendar.MILLISECOND] = 0
        date[Calendar.DAY_OF_MONTH] = 1
        date[Calendar.MONTH] = 0
        date[Calendar.YEAR] = RELEASE_YEAR
        return date.timeInMillis
    }

    private fun loadStatistics() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        val timeFilterFrom = jan1()
        val timeFilterTo = Long.MAX_VALUE
        disposable = Observable.fromCallable {
            val statisticsData = DBReader.getStatistics(
                false, timeFilterFrom, timeFilterTo)
            statisticsData.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                item2.timePlayed.compareTo(item1.timePlayed)
            }

            favoritePodNames.clear()
            var i = 0
            while (i < 5 && i < statisticsData.feedTime.size) {
                val title = statisticsData.feedTime[i].feed.title
                if (title != null) favoritePodNames.add(title)
                i++
            }
            loadFavoritePodImages(statisticsData)

            totalActivePodcasts = 0
            playedActivePodcasts = 0
            playedPodcasts = 0
            totalTime = 0
            val unplayedActive = ArrayList<String>()
            for (item in statisticsData.feedTime) {
                totalTime += item.timePlayed
                if (item.timePlayed > 0) {
                    playedPodcasts++
                }
                if (item.feed.preferences != null && item.feed.preferences!!.keepUpdated) {
                    totalActivePodcasts++
                    if (item.timePlayed > 0) {
                        playedActivePodcasts++
                    } else {
                        unplayedActive.add(item.feed.title?:"")
                    }
                }
            }
            if (unplayedActive.isNotEmpty()) {
                randomUnplayedActivePodcast = unplayedActive[(Math.random() * unplayedActive.size).toInt()]
            }

            val queue = DBReader.getQueue()
            queueNumEpisodes = queue.size
            queueSecondsLeft = 0
            for (item in queue) {
                var playbackSpeed = 1f
                if (timeRespectsSpeed()) {
                    playbackSpeed = getCurrentPlaybackSpeed(item.media)
                }
                if (item.media != null) {
                    val itemTimeLeft = (item.media!!.getDuration() - item.media!!.getPosition()).toLong()
                    queueSecondsLeft = (queueSecondsLeft + itemTimeLeft / playbackSpeed).toLong()
                }
            }
            queueSecondsLeft /= 1000

            timeBetweenReleaseAndPlay = DBReader.getTimeBetweenReleaseAndPlayback(timeFilterFrom, timeFilterTo)
            oldestDate = statisticsData.oldestDate
            statisticsData
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: StatisticsResult? -> loadScreen(currentScreen, true) },
                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    fun loadFavoritePodImages(statisticsData: StatisticsResult) {
        if (disposableFavorite != null) {
            disposableFavorite!!.dispose()
        }
        disposableFavorite = Observable.fromCallable {
            favoritePodImages.clear()
            var i = 0
            while (i < 5 && i < statisticsData.feedTime.size) {
                var cover = BitmapDrawable(resources, null as Bitmap?)
                try {
                    val size = SHARE_SIZE / 3
                    val radius = if ((i == 0)) (size / 16) else (size / 8)
                    cover = BitmapDrawable(resources, Glide.with(this)
                        .asBitmap()
                        .load(statisticsData.feedTime[i].feed.imageUrl)
                        .apply(RequestOptions()
                            .fitCenter()
                            .transform(RoundedCorners(radius)))
                        .submit(size, size)[5, TimeUnit.SECONDS])
                } catch (e: Exception) {
                    Log.d(TAG, "Loading cover: " + e.message)
                }
                favoritePodImages.add(cover)
                i++
            }
            statisticsData
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: StatisticsResult? -> },
                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val RELEASE_YEAR: Int = 2023
        private const val TAG = "EchoActivity"
        private const val NUM_SCREENS = 7
        private const val SHARE_SIZE = 1000
    }
}
