package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedBuilder
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.storage.database.Feeds.buildTags
import ac.mdiq.podcini.storage.database.Feeds.createSynthetic
import ac.mdiq.podcini.storage.database.Feeds.deleteFeedSync
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.OnlineFeedFragment
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
import ac.mdiq.podcini.util.MiscFormatter.localDateTimeString
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.round

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
                                if (!f.isSynthetic()) {
                                    val sLog = SubscriptionLog(f.id, f.title ?: "", f.downloadUrl ?: "", f.link ?: "", SubscriptionLog.Type.Feed.name)
                                    upsert(sLog) {
                                        it.rating = f.rating
                                        it.comment = if (f.comment.isBlank()) "" else (f.comment + "\n")
                                        it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
                                        it.cancelDate = Date().time
                                    }
                                } else {
                                    for (e in f.episodes) {
                                        val sLog = SubscriptionLog(e.id, e.title ?: "", e.media?.downloadUrl ?: "", e.link ?: "", SubscriptionLog.Type.Media.name)
                                        upsert(sLog) {
                                            it.rating = e.rating
                                            it.comment = if (e.comment.isBlank()) "" else (e.comment + "\n")
                                            it.comment += localDateTimeString() + "\nReason to remove:\n" + textState.text
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
//                                            val nTabs = feedBuilder.youtubeChannelValidTabs()
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
            Box(modifier = Modifier.width(56.dp).height(56.dp)) {
                val imgLoc = remember(feed) { feed.imageUrl }
                AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                    .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(), contentDescription = "imgvCover",
                    modifier = Modifier.fillMaxSize())
                if (feed.feedId > 0 || log != null) {
                    Logd("OnlineFeedItem", "${feed.feedId} $log")
                    val alpha = 1.0f
                    val iRes = if (feed.feedId > 0) R.drawable.ic_check else R.drawable.baseline_clear_24
                    Icon(imageVector = ImageVector.vectorResource(iRes), tint = textColor, contentDescription = "played_mark",
                        modifier = Modifier.background(Color.Green).alpha(alpha).align(Alignment.BottomEnd))
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
                TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.new_namee)) })
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
                    Spacer(Modifier.weight(1f))
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
fun TagSettingDialog(feeds_: List<Feed>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val feeds = realm.query(Feed::class).query("id IN $0", feeds_.map {it.id}).find()
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
                var tags = remember { commonTags.toMutableStateList() }
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
                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon",
                            modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                if (text.isNotBlank()) {
                                    if (text !in tags) tags.add(text)
                                    text = ""
                                }
                            })) },
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true) // Material3 requirement
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
                    Button(onClick = { onDismiss() }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        Logd("TagsSettingDialog", "tags: [${tags.joinToString()}] commonTags: [${commonTags.joinToString()}]")
                        if ((tags.toSet() + commonTags.toSet()).isNotEmpty() || text.isNotBlank()) {
                            for (f in feeds) upsertBlk(f) {
                                if (commonTags.isNotEmpty()) it.preferences?.tags?.removeAll(commonTags)
                                if (tags.isNotEmpty()) it.preferences?.tags?.addAll(tags)
                                if (text.isNotBlank()) it.preferences?.tags?.add(text)
                            }
                            buildTags()
                        }
                        onDismiss()
                    }) { Text("Confirm") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedDialog(feeds: List<Feed>, initSpeed: Float, maxSpeed: Float, isGlobal: Boolean = false, onDismiss: () -> Unit, speedCB: (Float) -> Unit) {
    // min speed set to 0.1 and max speed at 10
    fun speed2Slider(speed: Float): Float {
        return if (speed < 1) (speed - 0.1f) / 1.8f else (speed - 2f + maxSpeed) / 2 / (maxSpeed - 1f)
    }
    fun slider2Speed(slider: Float): Float {
        return if (slider < 0.5) 1.8f * slider + 0.1f else 2 * (maxSpeed - 1f) * slider + 2f - maxSpeed
    }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column {
                Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
//                var speed by remember { mutableStateOf(if (isGlobal) prefPlaybackSpeed else if (feeds.size == 1) feeds[0].preferences!!.playSpeed else 1f) }
                var speed by remember { mutableStateOf(initSpeed) }
                var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == FeedPreferences.SPEED_USE_GLOBAL) 1f else speed)) }
                var useGlobal by remember { mutableStateOf(!isGlobal && speed == FeedPreferences.SPEED_USE_GLOBAL) }
                if (!isGlobal) Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useGlobal, onCheckedChange = { isChecked ->
                        useGlobal = isChecked
                        speed = if (useGlobal) FeedPreferences.SPEED_USE_GLOBAL
                        else if (feeds.size == 1) {
                            if (feeds[0].preferences!!.playSpeed == FeedPreferences.SPEED_USE_GLOBAL) prefPlaybackSpeed
                            else feeds[0].preferences!!.playSpeed
                        } else 1f
                        if (!useGlobal) sliderPosition = speed2Slider(speed)
                    })
                    Text(stringResource(R.string.global_default))
                }
                if (!useGlobal) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(text = String.format(Locale.getDefault(), "%.2fx", speed))
                    }
                    val stepSize = 0.05f
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = {
                                val speed_ = round(speed / stepSize) * stepSize - stepSize
                                if (speed_ >= 0.1f) {
                                    speed = speed_
                                    sliderPosition = speed2Slider(speed)
                                }
                            }))
                        Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(5.dp).padding(start = 20.dp, end = 20.dp),
                            onValueChange = {
                                sliderPosition = it
                                speed = slider2Speed(sliderPosition)
                                Logd("PlaybackSpeedDialog", "slider value: $it $speed}")
                            })
                        Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = {
                                val speed_ = round(speed / stepSize) * stepSize + stepSize
                                if (speed_ <= maxSpeed) {
                                    speed = speed_
                                    sliderPosition = speed2Slider(speed)
                                }
                            }))
                    }
                }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                    Button(onClick = { onDismiss() }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val newSpeed = if (useGlobal) FeedPreferences.SPEED_USE_GLOBAL else speed
                        speedCB(newSpeed)
                        onDismiss()
                    }) { Text("Confirm") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedFullDialog(settingCode: BooleanArray, indexDefault: Int, maxSpeed: Float, onDismiss: () -> Unit) {
    val TAG = "PlaybackSpeedFullDialog"
    // min speed set to 0.1 and max speed at 10
    fun speed2Slider(speed: Float): Float {
        return if (speed < 1) (speed - 0.1f) / 1.8f else (speed - 2f + maxSpeed) / 2 / (maxSpeed - 1f)
    }
    fun slider2Speed(slider: Float): Float {
        return if (slider < 0.5) 1.8f * slider + 0.1f else 2 * (maxSpeed - 1f) * slider + 2f - maxSpeed
    }
    fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
        if (valueFromPrefs != null) {
            try {
                val jsonArray = JSONArray(valueFromPrefs)
                val selectedSpeeds: MutableList<Float> = ArrayList()
                for (i in 0 until jsonArray.length()) selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                return selectedSpeeds
            } catch (e: JSONException) {
                Log.e(TAG, "Got JSON error when trying to get speeds from JSONArray")
                e.printStackTrace()
            }
        }
        return mutableListOf(1.0f, 1.25f, 1.5f)
    }
//    fun getPlaybackSpeedArray() = readPlaybackSpeedArray(appPrefs.getString(UserPreferences.Prefs.prefPlaybackSpeedArray.name, null))
    fun setPlaybackSpeedArray(speeds: List<Float>) {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        val speedFormat = DecimalFormat("0.00", format)
        val jsonArray = JSONArray()
        for (speed in speeds) jsonArray.put(speedFormat.format(speed.toDouble()))
        appPrefs.edit().putString(UserPreferences.Prefs.prefPlaybackSpeedArray.name, jsonArray.toString()).apply()
    }
    fun setCurTempSpeed(speed: Float) {
        curState = upsertBlk(curState) { it.curTempSpeed = speed }
    }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.BOTTOM)
            window.setDimAmount(0f)
        }
        Card(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(top = 10.dp, bottom = 10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column {
                var speed by remember { mutableStateOf(curSpeedFB) }
                var speeds = remember { readPlaybackSpeedArray(appPrefs.getString(UserPreferences.Prefs.prefPlaybackSpeedArray.name, null)).toMutableStateList() }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(50.dp))
                    FilterChip(onClick = {
                        if (speed !in speeds) {
                            speeds.add(speed)
                            speeds.sort()
                            setPlaybackSpeedArray(speeds)
                    } }, label = { Text(String.format(Locale.getDefault(), "%.2f", speed)) }, selected = false,
                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon", modifier = Modifier.size(FilterChipDefaults.IconSize)) })
                }
                var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == FeedPreferences.SPEED_USE_GLOBAL) 1f else speed)) }
                val stepSize = 0.05f
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = {
                            val speed_ = round(speed / stepSize) * stepSize - stepSize
                            if (speed_ >= 0.1f) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed)
                            }
                        }))
                    Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(10.dp).padding(start = 20.dp, end = 20.dp),
                        onValueChange = {
                            sliderPosition = it
                            speed = slider2Speed(sliderPosition)
                            Logd("PlaybackSpeedDialog", "slider value: $it $speed}")
                        })
                    Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = {
                            val speed_ = round(speed / stepSize) * stepSize + stepSize
                            if (speed_ <= maxSpeed) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed)
                            }
                        }))
                }
                var forCurrent by remember { mutableStateOf(indexDefault == 0) }
                var forPodcast by remember { mutableStateOf(indexDefault == 1) }
                var forGlobal by remember { mutableStateOf(indexDefault == 2) }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forCurrent, onCheckedChange = { isChecked -> forCurrent = isChecked })
                    Text(stringResource(R.string.current_episode))
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forPodcast, onCheckedChange = { isChecked -> forPodcast = isChecked })
                    Text(stringResource(R.string.current_podcast))
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forGlobal, onCheckedChange = { isChecked -> forGlobal = isChecked })
                    Text(stringResource(R.string.global))
                    Spacer(Modifier.weight(1f))
                }
                FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    speeds.forEach { chipSpeed ->
                        FilterChip(onClick = {
                            Logd("VariableSpeedDialog", "holder.chip settingCode0: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                            settingCode[0] = forCurrent
                            settingCode[1] = forPodcast
                            settingCode[2] = forGlobal
                            Logd("VariableSpeedDialog", "holder.chip settingCode: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                            if (playbackService != null) {
                                playbackService!!.isSpeedForward = false
                                playbackService!!.isFallbackSpeed = false
                                if (settingCode.size == 3) {
                                    Logd(TAG, "setSpeed codeArray: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                                    if (settingCode[2]) UserPreferences.setPlaybackSpeed(chipSpeed)
                                    if (settingCode[1]) {
                                        val episode = (curMedia as? EpisodeMedia)?.episodeOrFetch() ?: curEpisode
                                        if (episode?.feed?.preferences != null) upsertBlk(episode.feed!!) { it.preferences!!.playSpeed = chipSpeed }
                                    }
                                    if (settingCode[0]) {
                                        setCurTempSpeed(chipSpeed)
                                        playbackService!!.mPlayer?.setPlaybackParams(chipSpeed, isSkipSilence)
                                    }
                                } else {
                                    setCurTempSpeed(chipSpeed)
                                    playbackService!!.mPlayer?.setPlaybackParams(chipSpeed, isSkipSilence)
                                }
                            }
                            else {
                                UserPreferences.setPlaybackSpeed(chipSpeed)
                                EventFlow.postEvent(FlowEvent.SpeedChangedEvent(chipSpeed))
                            }
                            onDismiss()
                        }, label = { Text(String.format(Locale.getDefault(), "%.2f", chipSpeed)) }, selected = false,
                            trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    speeds.remove(chipSpeed)
                                    setPlaybackSpeedArray(speeds)
                                })) })
                    }
                }
                var isSkipSilence by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSkipSilence, onCheckedChange = { isChecked ->
                        isSkipSilence = isChecked
                        playbackService?.mPlayer?.setPlaybackParams(playbackService!!.curSpeed, isChecked)
                    })
                    Text(stringResource(R.string.pref_skip_silence_title))
                }
            }
        }
    }
}

@Composable
fun OpmlImportSelectionDialog(readElements: SnapshotStateList<OpmlTransporter.OpmlElement>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val selectedItems = remember {  mutableStateMapOf<Int, Boolean>() }
    AlertDialog(onDismissRequest = { onDismissRequest() },
        title = { Text("Import OPML file") },
        text = {
            var isSelectAllChecked by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Select/Deselect All", modifier = Modifier.weight(1f))
                    Checkbox(checked = isSelectAllChecked, onCheckedChange = { isChecked ->
                        isSelectAllChecked = isChecked
                        readElements.forEachIndexed { index, _ -> selectedItems.put(index, isChecked) }
                    })
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(readElements) { index, item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item.text?:"", modifier = Modifier.weight(1f))
                            Checkbox(checked = selectedItems[index]?: false, onCheckedChange = { checked -> selectedItems.put(index, checked) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Logd("OpmlImportSelectionDialog", "checked: $selectedItems")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (readElements.isNotEmpty()) {
                                for (i in selectedItems.keys) {
                                    if (selectedItems[i] != true) continue
                                    val element = readElements[i]
                                    val feed = Feed(element.xmlUrl, null, if (element.text != null) element.text else "Unknown podcast")
                                    feed.episodes.clear()
                                    updateFeed(context, feed, false)
                                }
                                runOnce(context)
                            }
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        Toast.makeText(context, (e.message ?: "Import error"), Toast.LENGTH_LONG).show()
                    }
                }
                onDismissRequest()
            }) { Text("Confirm") }
        },
        dismissButton = { Button(onClick = { onDismissRequest() }) { Text("Dismiss") } }
    )
}