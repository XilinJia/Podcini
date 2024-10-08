package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedBuilder
import ac.mdiq.podcini.net.feed.discovery.PodcastSearchResult
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.OnlineFeedFragment
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatNumber
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineFeedItem(activity: MainActivity, feed: PodcastSearchResult) {
    val showSubscribeDialog = remember { mutableStateOf(false) }
    @Composable
    fun confirmSubscribe(feed: PodcastSearchResult, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text("Subscribe: \"${feed.title}\" ?")
                        Button(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                if (feed.feedUrl != null) {
                                    val feedBuilder = FeedBuilder(activity) {
                                        message, details -> Logd("OnineFeedItem", "Subscribe error: $message \n $details")
                                    }
                                    feedBuilder.feedSource = feed.source
                                    feedBuilder.startFeedBuilding(feed.feedUrl, "", "") { feed, _ ->  feedBuilder.subscribe(feed)}
                                }
                            }
                            onDismissRequest()
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
    if (showSubscribeDialog.value) {
        confirmSubscribe(feed, showSubscribeDialog.value, onDismissRequest = {
            showSubscribeDialog.value = false
        })
    }
    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp).combinedClickable(
        onClick = {
            if (feed.feedUrl != null) {
                if (feed.feedId > 0) {
                    val fragment = FeedEpisodesFragment.newInstance(feed.feedId)
                    activity.loadChildFragment(fragment)
                } else {
                    val fragment = OnlineFeedFragment.newInstance(feed.feedUrl)
                    fragment.feedSource = feed.source
                    activity.loadChildFragment(fragment)
                }
            }
        }, onLongClick = { showSubscribeDialog.value = true })) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Text(feed.title, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
        Row {
            ConstraintLayout(modifier = Modifier.width(56.dp).height(56.dp)) {
                val (imgvCover, checkMark) = createRefs()
                AsyncImage(model = feed.imageUrl, contentDescription = "imgvCover",
                    placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(65.dp).height(65.dp).constrainAs(imgvCover) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    })
                val alpha = if (feed.feedId > 0) 1.0f else 0f
                if (feed.feedId > 0) Icon(painter = painterResource(R.drawable.ic_check), tint = textColor, contentDescription = "played_mark",
                    modifier = Modifier.background(Color.Green).alpha(alpha).constrainAs(checkMark) {
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    })
            }
            Column(Modifier.padding(start = 10.dp)) {
                var authorText by remember { mutableStateOf("") }
                authorText = when {
                    !feed.author.isNullOrBlank() -> feed.author.trim { it <= ' ' }
                    feed.feedUrl != null && !feed.feedUrl.contains("itunes.apple.com") -> feed.feedUrl
                    else -> ""
                }
                if (authorText.isNotEmpty()) Text(authorText, color = textColor, style = MaterialTheme.typography.bodyMedium)
                if (feed.subscriberCount > 0) Text(formatNumber(feed.subscriberCount) + " subscribers", color = textColor, style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (feed.count != null && feed.count > 0) Text(feed.count.toString() + " episodes", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    if (feed.update != null) Text(feed.update, color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                Text(feed.source + ": " + feed.feedUrl, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

