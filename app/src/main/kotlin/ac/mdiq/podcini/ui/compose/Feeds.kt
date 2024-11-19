package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedBuilder
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.storage.database.Feeds.buildTags
import ac.mdiq.podcini.storage.database.Feeds.createSynthetic
import ac.mdiq.podcini.storage.database.Feeds.deleteFeedSync
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.OnlineFeedFragment
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun ChooseRatingDialog(selected: List<Feed>, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (rating in Rating.entries.reversed()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                        for (item in selected) upsertBlk(item) { it.rating = rating.code }
                        onDismissRequest()
                    }) {
                        Icon(imageVector = ImageVector.vectorResource(id = rating.res), "")
                        Text(rating.name, Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveFeedDialog(feeds: List<Feed>, onDismissRequest: () -> Unit, callback: Runnable?) {
    val message = if (feeds.size == 1) {
        if (feeds[0].isLocalFeed) stringResource(R.string.feed_delete_confirmation_local_msg) + feeds[0].title
        else stringResource(R.string.feed_delete_confirmation_msg) + feeds[0].title
    } else stringResource(R.string.feed_delete_confirmation_msg_batch)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message)
                Text(stringResource(R.string.feed_delete_reason_msg))
                BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Button(onClick = {
                    callback?.run()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (f in feeds) {
                                if (f.id > MAX_SYNTHETIC_ID) {
                                    val sLog = SubscriptionLog(f.id, f.title ?: "", f.downloadUrl ?: "", f.link ?: "", SubscriptionLog.Type.Feed.name)
                                    upsert(sLog) {
                                        it.rating = f.rating
                                        it.comment = f.comment
                                        it.comment += "\nReason to remove:\n" + textState.text
                                        it.cancelDate = Date().time
                                    }
                                } else {
                                    for (e in f.episodes) {
                                        val sLog = SubscriptionLog(e.id, e.title ?: "", e.media?.downloadUrl ?: "", e.link ?: "", SubscriptionLog.Type.Media.name)
                                        upsert(sLog) {
                                            it.rating = e.rating
                                            it.comment = e.comment
                                            it.comment += "\nReason to remove:\n" + textState.text
                                            it.cancelDate = Date().time
                                        }
                                    }
                                }
                                deleteFeedSync(context, f.id, false)
                            }
                            EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feeds.map { it.id }))
                        } catch (e: Throwable) { Log.e("RemoveFeedDialog", Log.getStackTraceString(e)) }
                    }
                    onDismissRequest()
                }) { Text("Confirm") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineFeedItem(activity: MainActivity, feed: PodcastSearchResult, log: SubscriptionLog? = null) {
//    var showYTChannelDialog by remember { mutableStateOf(false) }
//    if (showYTChannelDialog) feedBuilder.ConfirmYTChannelTabsDialog(onDismissRequest = {showYTChannelDialog = false}) {feed, map ->  handleFeed(feed, map)}

    val showSubscribeDialog = remember { mutableStateOf(false) }
    @Composable
    fun confirmSubscribe(feed: PodcastSearchResult, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text("Subscribe: \"${feed.title}\" ?", color = textColor, modifier = Modifier.padding(bottom = 10.dp))
                        Button(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                if (feed.feedUrl != null) {
                                    val feedBuilder = FeedBuilder(activity) { message, details -> Logd("OnineFeedItem", "Subscribe error: $message \n $details") }
                                    feedBuilder.feedSource = feed.source
                                    val url = feed.feedUrl
                                    if (feedBuilder.isYoutube(url)) {
                                        if (feedBuilder.isYoutubeChannel()) {
                                            val nTabs = feedBuilder.youtubeChannelValidTabs()
                                            feedBuilder.buildYTChannel(0, "") { feed, _ -> feedBuilder.subscribe(feed) }
//                                            if (nTabs > 1) showYTChannelDialog = true
//                                            else feedBuilder.buildYTChannel(0, "") { feed, map -> feedBuilder.subscribe(feed) }
                                        } else feedBuilder.buildYTPlaylist { feed, _ -> feedBuilder.subscribe(feed) }
                                    } else feedBuilder.buildPodcast(url, "", "") { feed, _ -> feedBuilder.subscribe(feed) }
                                }
                            }
                            onDismissRequest()
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }
    if (showSubscribeDialog.value) confirmSubscribe(feed, showSubscribeDialog.value, onDismissRequest = { showSubscribeDialog.value = false })

    val context = LocalContext.current
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
                val imgLoc = remember(feed) { feed.imageUrl }
                AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                    .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(), contentDescription = "imgvCover",
                    modifier = Modifier.width(65.dp).height(65.dp).constrainAs(imgvCover) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    })
                if (feed.feedId > 0 || log != null) {
                    Logd("OnlineFeedItem", "${feed.feedId} $log")
                    val alpha = 1.0f
                    val iRes = if (feed.feedId > 0) R.drawable.ic_check else R.drawable.baseline_clear_24
                    Icon(imageVector = ImageVector.vectorResource(iRes), tint = textColor, contentDescription = "played_mark",
                        modifier = Modifier.background(Color.Green).alpha(alpha).constrainAs(checkMark) {
                            bottom.linkTo(parent.bottom)
                            end.linkTo(parent.end)
                        })
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                var authorText by remember { mutableStateOf("") }
                authorText = when {
                    !feed.author.isNullOrBlank() -> feed.author.trim { it <= ' ' }
                    feed.feedUrl != null && !feed.feedUrl.contains("itunes.apple.com") -> feed.feedUrl
                    else -> ""
                }
                if (authorText.isNotEmpty()) Text(authorText, color = textColor, style = MaterialTheme.typography.bodyMedium)
                if (feed.subscriberCount > 0) Text(MiscFormatter.formatNumber(feed.subscriberCount) + " subscribers", color = textColor, style = MaterialTheme.typography.bodyMedium)
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

@Composable
fun RenameOrCreateSyntheticFeed(feed_: Feed? = null, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.rename_feed_label), color = textColor, style = MaterialTheme.typography.bodyLarge)
                var name by remember { mutableStateOf("") }
                TextField(value = name, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) name = it }, label = { Text(stringResource(R.string.new_namee)) })
                var hasVideo by remember { mutableStateOf(true) }
                var isYoutube by remember { mutableStateOf(false) }
                if (feed_ == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasVideo, onCheckedChange = { hasVideo = it })
                        Text(text = stringResource(R.string.has_video), style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.padding(start = 10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isYoutube, onCheckedChange = { isYoutube = it })
                        Text(text = stringResource(R.string.youtube), style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                Row {
                    Button({ onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                    Button({
                        val feed = feed_ ?: createSynthetic(0, name, hasVideo)
                        if (feed_ == null) {
                            feed.type = if (isYoutube) Feed.FeedType.YOUTUBE.name else Feed.FeedType.RSS.name
                            if (hasVideo) feed.preferences!!.videoModePolicy = VideoMode.WINDOW_VIEW
                        }
                        upsertBlk(feed) { if (feed_ != null) it.setCustomTitle1(name) }
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagSettingDialog(feeds: List<Feed>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val suggestions = remember { getTags() }
        val commonTags = remember {
            if (feeds.size == 1) feeds[0].preferences?.tags?.toMutableStateList()?: mutableStateListOf<String>()
            else {
                val commons = feeds[0].preferences?.tags?.toMutableSet()?: mutableSetOf()
                if (commons.isNotEmpty()) for (f in feeds) if (f.preferences != null) commons.retainAll(f.preferences!!.tags)
                commons.toMutableStateList()
            }
        }
        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feed_tags_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                var text by remember { mutableStateOf("") }
                var filteredSuggestions by remember { mutableStateOf(suggestions) }
                var showSuggestions by remember { mutableStateOf(false) }
                var tags = remember { commonTags }
                if (feeds.size > 1 && commonTags.isNotEmpty()) Text(stringResource(R.string.multi_feed_common_tags_info))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tags.forEach {
                        FilterChip(onClick = {  }, label = { Text(it) }, selected = false,
                            trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                                modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { tags.remove(it) })) })
                    }
                }
                ExposedDropdownMenuBox(expanded = showSuggestions, onExpandedChange = { }) {
                    TextField(value = text, onValueChange = {
                        text = it
                        filteredSuggestions = suggestions.filter { item -> item.contains(text, ignoreCase = true) && item !in tags }
                        showSuggestions = text.isNotEmpty() && filteredSuggestions.isNotEmpty()
                    },
                        placeholder = { Text("Type something...") }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (text.isNotBlank()) {
                                    if (text !in tags) tags.add(text)
                                    text = ""
                                }
                            }
                        ),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
                    )
                    ExposedDropdownMenu(expanded = showSuggestions, onDismissRequest = { showSuggestions = false }) {
                        for (i in filteredSuggestions.indices) {
                            DropdownMenuItem(text = { Text(filteredSuggestions[i]) },
                                onClick = {
                                    text = filteredSuggestions[i]
                                    showSuggestions = false
                                }
                            )
                        }
                    }
                }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                    Button(onClick = {
                        if ((tags.toSet() + commonTags.toSet()).isNotEmpty()) for (f in feeds) upsertBlk(f) {
                            if (commonTags.isNotEmpty()) it.preferences?.tags?.removeAll(commonTags)
                            if (tags.isNotEmpty()) it.preferences?.tags?.addAll(tags)
                        }
                        buildTags()
                        onDismiss()
                    }) { Text("Confirm") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onDismiss() }) { Text("Cancel") }
                }
            }
        }
    }
}
