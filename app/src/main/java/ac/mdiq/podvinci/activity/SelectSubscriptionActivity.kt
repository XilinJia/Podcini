package ac.mdiq.podvinci.activity

import ac.mdiq.podvinci.activity.MainActivity
import ac.mdiq.podvinci.activity.MainActivity.Companion.EXTRA_FEED_ID
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.preferences.ThemeSwitcher
import ac.mdiq.podvinci.core.storage.DBReader
import ac.mdiq.podvinci.core.storage.NavDrawerData
import ac.mdiq.podvinci.databinding.SubscriptionSelectionActivityBinding
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class SelectSubscriptionActivity : AppCompatActivity() {
    private var disposable: Disposable? = null

    @Volatile
    private var listItems: List<Feed>? = null

    private var viewBinding: SubscriptionSelectionActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this))
        super.onCreate(savedInstanceState)

        viewBinding = SubscriptionSelectionActivityBinding.inflate(layoutInflater)
        setContentView(viewBinding!!.root)
        setSupportActionBar(viewBinding!!.toolbar)
        setTitle(R.string.shortcut_select_subscription)

        viewBinding!!.transparentBackground.setOnClickListener { v: View? -> finish() }
        viewBinding!!.card.setOnClickListener(null)

        loadSubscriptions()

        val checkedPosition = arrayOfNulls<Int>(1)
        viewBinding!!.list.choiceMode = ListView.CHOICE_MODE_SINGLE
        viewBinding!!.list.onItemClickListener =
            AdapterView.OnItemClickListener { listView: AdapterView<*>?, view1: View?, position: Int, rowId: Long ->
                checkedPosition[0] = position
            }
        viewBinding!!.shortcutBtn.setOnClickListener { view: View? ->
            if (checkedPosition[0] != null && Intent.ACTION_CREATE_SHORTCUT == intent.action) {
                getBitmapFromUrl(listItems!![checkedPosition[0]!!])
            }
        }
    }

    fun getFeedItems(items: List<NavDrawerData.DrawerItem?>, result: MutableList<Feed>): List<Feed> {
        for (item in items) {
            if (item == null) continue
            if (item.type == NavDrawerData.DrawerItem.Type.TAG) {
                getFeedItems((item as NavDrawerData.TagDrawerItem).children, result)
            } else {
                val feed: Feed = (item as NavDrawerData.FeedDrawerItem).feed
                if (!result.contains(feed)) {
                    result.add(feed)
                }
            }
        }
        return result
    }

    private fun addShortcut(feed: Feed, bitmap: Bitmap?) {
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
        Glide.with(this)
            .asBitmap()
            .load(feed.imageUrl)
            .apply(RequestOptions.overrideOf(iconSize, iconSize))
            .listener(object : RequestListener<Bitmap?> {
                override fun onLoadFailed(e: GlideException?, model: Any?,
                                          target: Target<Bitmap?>, isFirstResource: Boolean
                ): Boolean {
                    addShortcut(feed, null)
                    return true
                }

                override fun onResourceReady(resource: Bitmap, model: Any,
                                             target: Target<Bitmap?>, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    addShortcut(feed, resource)
                    return true
                }
            }).submit()
    }

    private fun loadSubscriptions() {
        if (disposable != null) {
            disposable?.dispose()
        }
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
                    viewBinding!!.list.adapter = adapter
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        private const val TAG = "SelectSubscription"
    }
}