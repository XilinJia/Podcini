package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.EXTRA_FEED_ID
import ac.mdiq.podcini.preferences.ThemeSwitcher
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.NavDrawerData
import ac.mdiq.podcini.databinding.SubscriptionSelectionActivityBinding
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.preferences.UserPreferences
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

// TODO: need to enable
class SelectSubscriptionActivity : AppCompatActivity() {
    private var _binding: SubscriptionSelectionActivityBinding? = null
    private val binding get() = _binding!!

    @Volatile
    private var listItems: List<Feed> = listOf()

    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this))
        super.onCreate(savedInstanceState)

        _binding = SubscriptionSelectionActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setTitle(R.string.shortcut_select_subscription)

        binding.transparentBackground.setOnClickListener { finish() }
        binding.card.setOnClickListener(null)

        loadSubscriptions()

        val checkedPosition = arrayOfNulls<Int>(1)
        binding.list.choiceMode = ListView.CHOICE_MODE_SINGLE
        binding.list.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                checkedPosition[0] = position
            }
        binding.shortcutBtn.setOnClickListener {
            if (checkedPosition[0] != null && Intent.ACTION_CREATE_SHORTCUT == intent.action) {
                getBitmapFromUrl(listItems[checkedPosition[0]!!])
            }
        }
    }

    fun getFeedItems(items: List<NavDrawerData.FeedDrawerItem?>, result: MutableList<Feed>): List<Feed> {
        for (item in items) {
            if (item == null) continue
            val feed: Feed = item.feed
            if (!result.contains(feed)) {
                result.add(feed)
            }
        }
        return result
    }

    @UnstableApi private fun addShortcut(feed: Feed, bitmap: Bitmap?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.setAction(Intent.ACTION_MAIN)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(EXTRA_FEED_ID, feed.id)
        val id = "subscription-" + feed.id

        val icon: IconCompat = if (bitmap != null) {
            IconCompat.createWithAdaptiveBitmap(bitmap)
        } else {
            IconCompat.createWithResource(this, R.drawable.ic_subscriptions_shortcut)
        }

        val shortcut: ShortcutInfoCompat = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(feed.title?:"")
            .setLongLabel(feed.feedTitle?:"")
            .setIntent(intent)
            .setIcon(icon)
            .build()

        setResult(Activity.RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        finish()
    }

    private fun getBitmapFromUrl(feed: Feed) {
        val iconSize = (128 * resources.displayMetrics.density).toInt()
        if (!feed.imageUrl.isNullOrBlank()) Glide.with(this)
            .asBitmap()
            .load(feed.imageUrl)
            .apply(RequestOptions.overrideOf(iconSize, iconSize))
            .listener(object : RequestListener<Bitmap?> {
                @UnstableApi override fun onLoadFailed(e: GlideException?, model: Any?,
                                                       target: Target<Bitmap?>, isFirstResource: Boolean
                ): Boolean {
                    addShortcut(feed, null)
                    return true
                }

                @UnstableApi override fun onResourceReady(resource: Bitmap, model: Any,
                                                          target: Target<Bitmap?>, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    addShortcut(feed, resource)
                    return true
                }
            }).submit()
    }

    private fun loadSubscriptions() {
        disposable?.dispose()

        disposable = Observable.fromCallable {
            val data: NavDrawerData = DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter)
            getFeedItems(data.items, ArrayList())
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: List<Feed> ->
                    listItems = result
                    val titles = ArrayList<String>()
                    for (feed in result) {
                        if (feed.title != null) titles.add(feed.title!!)
                    }
                    val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this,
                        R.layout.simple_list_item_multiple_choice_on_start, titles)
                    binding.list.adapter = adapter
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val TAG = "SelectSubscription"
    }
}