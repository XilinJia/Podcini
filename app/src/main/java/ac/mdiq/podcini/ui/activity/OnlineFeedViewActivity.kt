package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.databinding.OnlinefeedviewActivityBinding
import ac.mdiq.podcini.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.feed.parser.FeedHandler
import ac.mdiq.podcini.feed.parser.FeedHandlerResult
import ac.mdiq.podcini.feed.parser.UnsupportedFeedtypeException
import ac.mdiq.podcini.net.common.UrlChecker.prepareUrl
import ac.mdiq.podcini.net.discovery.CombinedSearcher
import ac.mdiq.podcini.net.discovery.PodcastSearcherRegistry
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTranslucentTheme
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.service.download.DownloadRequestCreator.create
import ac.mdiq.podcini.service.download.Downloader
import ac.mdiq.podcini.service.download.HttpDownloader
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBTasks
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.ui.common.ThemeUtils.getColorFromAttr
import ac.mdiq.podcini.ui.dialog.AuthenticationDialog
import ac.mdiq.podcini.ui.glide.FastBlurTransformation
import ac.mdiq.podcini.util.DownloadErrorLabel.from
import ac.mdiq.podcini.util.event.EpisodeDownloadEvent
import ac.mdiq.podcini.util.event.FeedListUpdateEvent
import ac.mdiq.podcini.util.syndication.FeedDiscoverer
import ac.mdiq.podcini.util.syndication.HtmlToPlainText
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.LightingColorFilter
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableMaybeObserver
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import kotlin.concurrent.Volatile
import kotlin.math.min

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 *
 *
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
class OnlineFeedViewActivity : AppCompatActivity() {
    private var _binding: OnlinefeedviewActivityBinding? = null
    private val binding get() = _binding!!

    @Volatile
    private var feeds: List<Feed>? = null
    private var selectedDownloadUrl: String? = null
    private var downloader: Downloader? = null
    private var username: String? = null
    private var password: String? = null

    private var isPaused = false
    private var didPressSubscribe = false
    private var isFeedFoundBySearch = false

    private var dialog: Dialog? = null

    private var download: Disposable? = null
    private var parser: Disposable? = null
    private var updater: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTranslucentTheme(this))
        super.onCreate(savedInstanceState)

        _binding = OnlinefeedviewActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.transparentBackground.setOnClickListener { finish() }
        binding.closeButton.setOnClickListener { finish() }
        binding.card.setOnClickListener(null)
        binding.card.setCardBackgroundColor(getColorFromAttr(this, R.attr.colorSurface))

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

        if (feedUrl == null) {
            Log.e(TAG, "feedUrl is null.")
            showNoPodcastFoundError()
        } else {
            Log.d(TAG, "Activity was started with url $feedUrl")
            setLoadingLayout()
            // Remove subscribeonandroid.com from feed URL in order to subscribe to the actual feed URL
            if (feedUrl.contains("subscribeonandroid.com")) {
                feedUrl = feedUrl.replaceFirst("((www.)?(subscribeonandroid.com/))".toRegex(), "")
            }
            if (savedInstanceState != null) {
                username = savedInstanceState.getString("username")
                password = savedInstanceState.getString("password")
            }
            lookupUrlAndDownload(feedUrl)
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

    /**
     * Displays a progress indicator.
     */
    private fun setLoadingLayout() {
        binding.progressBar.visibility = View.VISIBLE
        binding.feedDisplayContainer.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        isPaused = false
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        isPaused = true
        EventBus.getDefault().unregister(this)
        if (downloader != null && !downloader!!.isFinished) downloader!!.cancel()

        if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
    }

    public override fun onDestroy() {
        super.onDestroy()
        _binding = null
        updater?.dispose()
        download?.dispose()
        parser?.dispose()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("username", username)
        outState.putString("password", password)
    }

    private fun resetIntent(url: String) {
        val intent = Intent()
        intent.putExtra(ARG_FEEDURL, url)
        setIntent(intent)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @UnstableApi override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val destIntent = Intent(this, MainActivity::class.java)
            if (NavUtils.shouldUpRecreateTask(this, destIntent)) {
                startActivity(destIntent)
            } else {
                NavUtils.navigateUpFromSameTask(this)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun lookupUrlAndDownload(url: String) {
        download = PodcastSearcherRegistry.lookupUrl(url)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe({ url1: String -> this.startFeedDownload(url1) },
                { error: Throwable? ->
                    if (error is FeedUrlNotFoundException) {
                        tryToRetrieveFeedUrlBySearch(error)
                    } else {
                        showNoPodcastFoundError()
                        Log.e(TAG, Log.getStackTraceString(error))
                    }
                })
    }

    private fun tryToRetrieveFeedUrlBySearch(error: FeedUrlNotFoundException) {
        Log.d(TAG, "Unable to retrieve feed url, trying to retrieve feed url from search")
        val url = searchFeedUrlByTrackName(error.trackName, error.artistName)
        if (url != null) {
            Log.d(TAG, "Successfully retrieve feed url")
            isFeedFoundBySearch = true
            startFeedDownload(url)
        } else {
            showNoPodcastFoundError()
            Log.d(TAG, "Failed to retrieve feed url")
        }
    }

    private fun searchFeedUrlByTrackName(trackName: String, artistName: String): String? {
        val searcher = CombinedSearcher()
        val query = "$trackName $artistName"
        val results = searcher.search(query).blockingGet()
        if (results.isNullOrEmpty()) return null
        for (result in results) {
            if (result?.feedUrl != null && result.author != null &&
                    result.author.equals(artistName, ignoreCase = true) &&
                    result.title.equals(trackName, ignoreCase = true)) {
                return result.feedUrl
            }
        }
        return null
    }

    private fun startFeedDownload(url: String) {
        Log.d(TAG, "Starting feed download")
        selectedDownloadUrl = prepareUrl(url)
        val request = create(Feed(selectedDownloadUrl, null))
            .withAuthentication(username, password)
            .withInitiatedByUser(true)
            .build()

        download = Observable.fromCallable {
            feeds = DBReader.getFeedList()
            downloader = HttpDownloader(request)
            downloader?.call()
            downloader?.result
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ status: DownloadResult? -> if (request.destination != null) checkDownloadResult(status, request.destination) },
                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun checkDownloadResult(status: DownloadResult?, destination: String) {
        if (status == null) return
        if (status.isSuccessful) {
            parseFeed(destination)
        } else if (status.reason == DownloadError.ERROR_UNAUTHORIZED) {
            if (!isFinishing && !isPaused) {
                if (username != null && password != null) {
                    Toast.makeText(this, R.string.download_error_unauthorized, Toast.LENGTH_LONG).show()
                }
                if (downloader?.downloadRequest?.source != null) {
                    dialog = FeedViewAuthenticationDialog(this@OnlineFeedViewActivity,
                        R.string.authentication_notification_title, downloader!!.downloadRequest.source!!).create()
                    dialog?.show()
                }
            }
        } else {
            showErrorDialog(getString(from(status.reason)), status.reasonDetailed)
        }
    }

    @UnstableApi @Subscribe
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        updater = Observable.fromCallable { DBReader.getFeedList() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { feeds: List<Feed>? ->
                    this@OnlineFeedViewActivity.feeds = feeds
                    handleUpdatedFeedStatus()
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) }
            )
    }

    @UnstableApi @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent?) {
        handleUpdatedFeedStatus()
    }

    private fun parseFeed(destination: String) {
        Log.d(TAG, "Parsing feed")
        parser = Maybe.fromCallable { doParseFeed(destination) }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(object : DisposableMaybeObserver<FeedHandlerResult?>() {
                @UnstableApi override fun onSuccess(result: FeedHandlerResult) {
                    showFeedInformation(result.feed, result.alternateFeedUrls)
                }

                override fun onComplete() {
                    // Ignore null result: We showed the discovery dialog.
                }

                override fun onError(error: Throwable) {
                    showErrorDialog(error.message, "")
                    Log.d(TAG, "Feed parser exception: " + Log.getStackTraceString(error))
                }
            })
    }

    /**
     * Try to parse the feed.
     * @return  The FeedHandlerResult if successful.
     * Null if unsuccessful but we started another attempt.
     * @throws Exception If unsuccessful but we do not know a resolution.
     */
    @Throws(Exception::class)
    private fun doParseFeed(destination: String): FeedHandlerResult? {
        val handler = FeedHandler()
        val feed = Feed(selectedDownloadUrl, null)
        feed.file_url = destination
        val destinationFile = File(destination)
        return try {
            handler.parseFeed(feed)
        } catch (e: UnsupportedFeedtypeException) {
            Log.d(TAG, "Unsupported feed type detected")
            if ("html".equals(e.rootElement, ignoreCase = true)) {
                if (selectedDownloadUrl != null) {
                    val dialogShown = showFeedDiscoveryDialog(destinationFile, selectedDownloadUrl!!)
                    if (dialogShown) {
                        null // Should not display an error message
                    } else {
                        throw UnsupportedFeedtypeException(getString(R.string.download_error_unsupported_type_html))
                    }
                } else null
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            val rc = destinationFile.delete()
            Log.d(TAG, "Deleted feed source file. Result: $rc")
        }
    }

    /**
     * Called when feed parsed successfully.
     * This method is executed on the GUI thread.
     */
    @UnstableApi private fun showFeedInformation(feed: Feed, alternateFeedUrls: Map<String, String>) {
        binding.progressBar.visibility = View.GONE
        binding.feedDisplayContainer.visibility = View.VISIBLE
        if (isFeedFoundBySearch) {
            val resId = R.string.no_feed_url_podcast_found_by_search
            Snackbar.make(binding.root, resId, Snackbar.LENGTH_LONG).show()
        }

        binding.backgroundImage.colorFilter = LightingColorFilter(-0x7d7d7e, 0x000000)

        binding.episodeLabel.setOnClickListener { showEpisodes(feed.items)}

        if (!feed.imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(feed.imageUrl)
                .apply(RequestOptions()
                    .placeholder(R.color.light_gray)
                    .error(R.color.light_gray)
                    .fitCenter()
                    .dontAnimate())
                .into(binding.coverImage)
            Glide.with(this)
                .load(feed.imageUrl)
                .apply(RequestOptions()
                    .placeholder(R.color.image_readability_tint)
                    .error(R.color.image_readability_tint)
                    .transform(FastBlurTransformation())
                    .dontAnimate())
                .into(binding.backgroundImage)
        }

        binding.titleLabel.text = feed.title
        binding.authorLabel.text = feed.author

        binding.txtvDescription.text = HtmlToPlainText.getPlainText(feed.description?:"")

        binding.subscribeButton.setOnClickListener {
            if (feedInFeedlist()) {
                openFeed()
            } else {
                DBTasks.updateFeed(this, feed, false)
                didPressSubscribe = true
                handleUpdatedFeedStatus()
            }
        }

        if (isEnableAutodownload) {
            val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
            binding.autoDownloadCheckBox.isChecked = preferences.getBoolean(PREF_LAST_AUTO_DOWNLOAD, true)
        }

        if (alternateFeedUrls.isEmpty()) {
            binding.alternateUrlsSpinner.visibility = View.GONE
        } else {
            binding.alternateUrlsSpinner.visibility = View.VISIBLE

            val alternateUrlsList: MutableList<String> = ArrayList()
            val alternateUrlsTitleList: MutableList<String?> = ArrayList()

            if (feed.download_url != null) alternateUrlsList.add(feed.download_url!!)
            alternateUrlsTitleList.add(feed.title)

            alternateUrlsList.addAll(alternateFeedUrls.keys)
            for (url in alternateFeedUrls.keys) {
                alternateUrlsTitleList.add(alternateFeedUrls[url])
            }

            val adapter: ArrayAdapter<String> = object : ArrayAdapter<String>(this,
                R.layout.alternate_urls_item, alternateUrlsTitleList) {
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    // reusing the old view causes a visual bug on Android <= 10
                    return super.getDropDownView(position, null, parent)
                }
            }

            adapter.setDropDownViewResource(R.layout.alternate_urls_dropdown_item)
            binding.alternateUrlsSpinner.adapter = adapter
            binding.alternateUrlsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                    selectedDownloadUrl = alternateUrlsList[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
        }
        handleUpdatedFeedStatus()
    }

    @UnstableApi private fun openFeed() {
        // feed.getId() is always 0, we have to retrieve the id from the feed list from
        // the database
        val intent = MainActivity.getIntentToOpenFeed(this, feedId)
        intent.putExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH,
            getIntent().getBooleanExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, false))
        finish()
        startActivity(intent)
    }

    @UnstableApi private fun showEpisodes(episodes: List<FeedItem>) {
        Log.d(TAG, "showEpisodes ${episodes.size}")
        if (episodes.isNullOrEmpty()) return
        val intent = MainActivity.openEpisodesList(this, ArrayList(episodes.subList(0, min(50, episodes.size-1))))
        intent.putExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH,
            getIntent().getBooleanExtra(MainActivity.EXTRA_STARTED_FROM_SEARCH, false))
        finish()
        startActivity(intent)
    }

    @UnstableApi private fun handleUpdatedFeedStatus() {
        val dli = DownloadServiceInterface.get()
        if (dli == null || selectedDownloadUrl == null) return

        when {
            dli.isDownloadingEpisode(selectedDownloadUrl!!) -> {
                binding.subscribeButton.isEnabled = false
                binding.subscribeButton.setText(R.string.subscribing_label)
            }
            feedInFeedlist() -> {
                binding.subscribeButton.isEnabled = true
                binding.subscribeButton.setText(R.string.open)
                if (didPressSubscribe) {
                    didPressSubscribe = false

                    val feed1 = DBReader.getFeed(feedId)?: return
                    val feedPreferences = feed1.preferences
                    if (feedPreferences != null) {
                        if (isEnableAutodownload) {
                            val autoDownload = binding.autoDownloadCheckBox.isChecked
                            feedPreferences.autoDownload = autoDownload

                            val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
                            val editor = preferences.edit()
                            editor.putBoolean(PREF_LAST_AUTO_DOWNLOAD, autoDownload)
                            editor.apply()
                        }
                        if (username != null) {
                            feedPreferences.username = username
                            feedPreferences.password = password
                        }
                        DBWriter.setFeedPreferences(feedPreferences)
                    }
                    openFeed()
                }
            }
            else -> {
                binding.subscribeButton.isEnabled = true
                binding.subscribeButton.setText(R.string.subscribe_label)
                if (isEnableAutodownload) {
                    binding.autoDownloadCheckBox.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun feedInFeedlist(): Boolean {
        return feedId != 0L
    }

    private val feedId: Long
        get() {
            if (feeds == null) return 0

            for (f in feeds!!) {
                if (f.download_url == selectedDownloadUrl) {
                    return f.id
                }
            }
            return 0
        }

    @UiThread
    private fun showErrorDialog(errorMsg: String?, details: String) {
        if (!isFinishing && !isPaused) {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(R.string.error_label)
            if (errorMsg != null) {
                val total = """
                    $errorMsg
                    
                    $details
                    """.trimIndent()
                val errorMessage = SpannableString(total)
                errorMessage.setSpan(ForegroundColorSpan(-0x77777778),
                    errorMsg.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setMessage(errorMessage)
            } else {
                builder.setMessage(R.string.download_error_error_unknown)
            }
            builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.cancel() }
            if (intent.getBooleanExtra(ARG_WAS_MANUAL_URL, false)) {
                builder.setNeutralButton(R.string.edit_url_menu) { _: DialogInterface?, _: Int -> editUrl() }
            }
            builder.setOnCancelListener {
                setResult(RESULT_ERROR)
                finish()
            }
            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
            dialog = builder.show()
        }
    }

    private fun editUrl() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.edit_url_menu)
        val dialogBinding = EditTextDialogBinding.inflate(layoutInflater)
        if (downloader != null) {
            dialogBinding.urlEditText.setText(downloader!!.downloadRequest.source)
        }
        builder.setView(dialogBinding.root)
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            setLoadingLayout()
            lookupUrlAndDownload(dialogBinding.urlEditText.text.toString())
        }
        builder.setNegativeButton(R.string.cancel_label) { dialog1: DialogInterface, _: Int -> dialog1.cancel() }
        builder.setOnCancelListener {
            setResult(RESULT_ERROR)
            finish()
        }
        builder.show()
    }

    /**
     *
     * @return true if a FeedDiscoveryDialog is shown, false otherwise (e.g., due to no feed found).
     */
    private fun showFeedDiscoveryDialog(feedFile: File, baseUrl: String): Boolean {
        val fd = FeedDiscoverer()
        val urlsMap: Map<String, String>
        try {
            urlsMap = fd.findLinks(feedFile, baseUrl)
            if (urlsMap.isEmpty()) return false
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        if (isPaused || isFinishing) return false

        val titles: MutableList<String?> = ArrayList()

        val urls: List<String> = ArrayList(urlsMap.keys)
        for (url in urls) {
            titles.add(urlsMap[url])
        }

        if (urls.size == 1) {
            // Skip dialog and display the item directly
            resetIntent(urls[0])
            startFeedDownload(urls[0])
            return true
        }

        val adapter = ArrayAdapter(this@OnlineFeedViewActivity,
            R.layout.ellipsize_start_listitem, R.id.txtvTitle, titles)
        val onClickListener = DialogInterface.OnClickListener { dialog: DialogInterface, which: Int ->
            val selectedUrl = urls[which]
            dialog.dismiss()
            resetIntent(selectedUrl)
            startFeedDownload(selectedUrl)
        }

        val ab = MaterialAlertDialogBuilder(this@OnlineFeedViewActivity)
            .setTitle(R.string.feeds_label)
            .setCancelable(true)
            .setOnCancelListener { _: DialogInterface? -> finish() }
            .setAdapter(adapter, onClickListener)

        runOnUiThread {
            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
            dialog = ab.show()
        }
        return true
    }

    private inner class FeedViewAuthenticationDialog(context: Context, titleRes: Int, private val feedUrl: String) :
        AuthenticationDialog(context, titleRes, true, username, password) {
        override fun onCancelled() {
            super.onCancelled()
            finish()
        }

        override fun onConfirmed(username: String, password: String) {
            this@OnlineFeedViewActivity.username = username
            this@OnlineFeedViewActivity.password = password
            startFeedDownload(feedUrl)
        }
    }

    companion object {
        const val ARG_FEEDURL: String = "arg.feedurl"
        const val ARG_WAS_MANUAL_URL: String = "manual_url"
        private const val RESULT_ERROR = 2
        private const val TAG = "OnlineFeedViewActivity"
        private const val PREFS = "OnlineFeedViewActivityPreferences"
        private const val PREF_LAST_AUTO_DOWNLOAD = "lastAutoDownload"
        private const val DESCRIPTION_MAX_LINES_COLLAPSED = 20
    }
}
