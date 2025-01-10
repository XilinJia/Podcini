package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CustomTheme
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubscriptionShortcutActivity : ComponentActivity() {
    private val listItems = mutableStateListOf<Feed>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CustomTheme(this) {
                Card(modifier = Modifier.padding(vertical = 30.dp, horizontal = 16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column {
                        Text(stringResource(R.string.shortcut_select_subscription), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                        var checkedIndex by remember { mutableIntStateOf(-1) }
                        val lazyListState = rememberLazyListState()
                        LazyColumn(state = lazyListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(listItems) { index, item ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = { checkedIndex = index })) {
                                    var checked by remember { mutableStateOf(false) }
                                    Checkbox(checked = checkedIndex == index, onCheckedChange = {
                                        checkedIndex = index
                                        checked = it
                                    })
                                    Text(item.title?: "No title", color = textColor, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                        Button(onClick = { if (checkedIndex >= 0 && Intent.ACTION_CREATE_SHORTCUT == intent.action) getBitmapFromUrl(listItems[checkedIndex]) }) { Text(stringResource(R.string.add_shortcut)) }
                    }
                }
            }
        }
        loadSubscriptions()
    }

    private fun addShortcut(feed: Feed, bitmap: Bitmap?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.setAction(Intent.ACTION_MAIN)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(MainActivity.Extras.fragment_feed_id.name, feed.id)
        val id = "subscription-" + feed.id

//         val icon: IconCompat = if (bitmap != null) IconCompat.createWithAdaptiveBitmap(bitmap)
        val icon: IconCompat = if (bitmap != null) bitmapToIconCompat(bitmap, getAppIconSize())
        else IconCompat.createWithResource(this, R.drawable.ic_subscriptions_shortcut)

        val shortcut: ShortcutInfoCompat = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(feed.title?:"")
            .setLongLabel(feed.eigenTitle?:"")
            .setIntent(intent)
            .setIcon(icon)
            .build()
        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        finish()
    }

    private fun getBitmapFromUrl(feed: Feed) {
        val iconSize = (128 * resources.displayMetrics.density).toInt()
        val request = ImageRequest.Builder(this)
            .data(feed.imageUrl)
            .setHeader("User-Agent", "Mozilla/5.0")
            .placeholder(R.mipmap.ic_launcher)
            .listener(onError = {_, e -> addShortcut(feed, null) }, onSuccess = { _, result -> addShortcut(feed, result.drawable.toBitmap()) })
            .size(iconSize, iconSize)
            .build()
        imageLoader.enqueue(request)
    }

    fun getAppIconSize(): Int {
        val activityManager = getSystemService(ActivityManager::class.java)
        val appIconSize = try { activityManager.launcherLargeIconSize } catch (e: Exception) { TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt() }
        return appIconSize
    }

    fun bitmapToIconCompat(bitmap: Bitmap, desiredSizeDp: Int): IconCompat {
        val desiredSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, desiredSizeDp.toFloat(), resources.displayMetrics).toInt()
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, desiredSizePx, desiredSizePx, true)
        return IconCompat.createWithBitmap(resizedBitmap)
    }

    private fun loadSubscriptions() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { getFeedList() }
                withContext(Dispatchers.Main) { listItems.addAll(result) }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    companion object {
        private val TAG: String = SubscriptionShortcutActivity::class.simpleName ?: "Anonymous"
    }
}