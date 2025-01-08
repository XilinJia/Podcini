package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.Feeds.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedFunding
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.setOnlineSearchTerms
import ac.mdiq.podcini.util.*
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.content.*
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.ExecutionException


class FeedInfoVM(val context: Context, val lcScope: CoroutineScope) {
    internal var feed: Feed

    internal var isCallable by mutableStateOf(false)
    internal var showRemoveFeedDialog by mutableStateOf(false)
    internal var txtvAuthor by mutableStateOf("")
    internal var txtvUrl by mutableStateOf<String?>(null)
    internal var rating by mutableStateOf(Rating.UNRATED.code)
    internal val showConnectLocalFolderConfirm = mutableStateOf(false)

    init {
        feed = feedOnDisplay
    }

    internal var eventSink: Job? = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedChangeEvent -> setFeed(feed)    // reload from DB
                    else -> {}
                }
            }
        }
    }
    fun setFeed(feed_: Feed) {
        feed = realm.query(Feed::class).query("id == $0", feed_.id).first().find()!!
        rating = feed.rating
    }

    internal fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    feed.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
                    updateFeed(context, feed, true)
                }
//                withContext(Dispatchers.Main) { (context as MainActivity).showSnackbarAbovePlayer(string.ok, Snackbar.LENGTH_SHORT) }
            } catch (e: Throwable) { withContext(Dispatchers.Main) {
                Log.e(TAG, e.localizedMessage?:"No message")
//                (context as MainActivity).showSnackbarAbovePlayer(e.localizedMessage?:"No message", Snackbar.LENGTH_LONG)
            } }
        }
    }
}

@Composable
fun FeedInfoScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(feedOnDisplay.id) { FeedInfoVM(context, scope) }

    val addLocalFolderLauncher: ActivityResultLauncher<Uri?> = rememberLauncherForActivityResult(contract = AddLocalFolder()) { uri: Uri? -> vm.addLocalFolderResult(uri) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
                    vm.txtvAuthor = vm.feed.author ?: ""
                    vm.txtvUrl = vm.feed.downloadUrl
                    if (!vm.feed.link.isNullOrEmpty()) vm.isCallable = IntentUtils.isCallable(context, Intent(Intent.ACTION_VIEW, Uri.parse(vm.feed.link)))
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                    vm.procFlowEvents()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                    vm.cancelFlowEvents()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feed = Feed()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (vm.showRemoveFeedDialog) RemoveFeedDialog(listOf(vm.feed), onDismissRequest = { vm.showRemoveFeedDialog = false }) {
        mainNavController.navigate("DefaultPage")
//        (context as MainActivity).loadFragment(AppPreferences.defaultPage, null)
//        context.supportFragmentManager.executePendingTransactions()   // Make sure fragment is hidden before actually starting to delete
    }
    ComfirmDialog(0, stringResource(R.string.reconnect_local_folder_warning), vm.showConnectLocalFolderConfirm) {
        try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
    }

    @Composable
    fun HeaderUI() {
        val textColor = MaterialTheme.colorScheme.onSurface
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.feed)) {
            showChooseRatingDialog = false
            vm.setFeed(vm.feed)
        }
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val (bgImage, bgColor, controlRow, imgvCover) = createRefs()
            AsyncImage(model = vm.feed.imageUrl ?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds,
                error = painterResource(R.drawable.teaser),
                modifier = Modifier.fillMaxSize().blur(radiusX = 15.dp, radiusY = 15.dp)
                    .constrainAs(bgImage) {
                        bottom.linkTo(parent.bottom)
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .constrainAs(bgColor) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                .constrainAs(controlRow) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                }, verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                val ratingIconRes = Rating.fromCode(vm.rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(30.dp).height(30.dp).clickable(onClick = {
                        showChooseRatingDialog = true
                    }))
                Spacer(modifier = Modifier.weight(0.2f))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), tint = textColor, contentDescription = "butShowSettings",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                        feedOnDisplay = vm.feed
                        mainNavController.navigate(Screens.FeedSettings.name)
//                    (context as MainActivity).loadChildFragment(FeedSettingsFragment.newInstance(vm.feed), TransitionEffect.SLIDE)
                    }))
                Spacer(modifier = Modifier.weight(0.2f))
                Button(onClick = {
                    feedOnDisplay = vm.feed
                    mainNavController.navigate(Screens.FeedEpisodes.name)
//                (context as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(vm.feed.id))
                }) {
                    Text(vm.feed.episodes.size.toString() + " " + stringResource(R.string.episodes_label))
                }
                Spacer(modifier = Modifier.width(15.dp))
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).constrainAs(imgvCover) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                width = Dimension.fillToConstraints
            }) {
                AsyncImage(model = vm.feed.imageUrl ?: "", contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(100.dp).height(100.dp).padding(start = 16.dp, end = 16.dp))
                Column(Modifier.padding(top = 10.dp)) {
                    Text(vm.feed.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = vm.txtvAuthor, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    fun DetailUI() {
        val context = LocalContext.current
        val scrollState = rememberScrollState()
        var showEditComment by remember { mutableStateOf(false) }
        val localTime = remember { System.currentTimeMillis() }
        var editCommentText by remember { mutableStateOf(TextFieldValue((if (vm.feed.comment.isBlank()) "" else vm.feed.comment + "\n") + fullDateTimeString(localTime) + ":\n")) }
        var commentTextState by remember { mutableStateOf(TextFieldValue(vm.feed.comment)) }
        if (showEditComment) LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                runOnIOScope {
                    vm.feed = upsert(vm.feed) {
                        it.comment = editCommentText.text
                        it.commentTime = localTime
                    }
                    vm.rating = vm.feed.rating
                }
            })
        var showFeedStats by remember { mutableStateOf(false) }
        if (showFeedStats) FeedStatisticsDialog(vm.feed.title?: "No title", vm.feed.id) { showFeedStats = false }

        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Text(vm.feed.title ?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            Text(vm.feed.author ?:"", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Text(HtmlToPlainText.getPlainText(vm.feed.description?:""), color = textColor, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isEmpty()) " (Add)" else "",
                color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
            Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))

            if (!vm.feed.isSynthetic()) {
                Text(stringResource(R.string.url_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(text = vm.txtvUrl ?: "", color = textColor, modifier = Modifier.clickable {
                    if (vm.feed.downloadUrl != null) {
                        val url: String = vm.feed.downloadUrl!!
                        val clipData: ClipData = ClipData.newPlainText(url, url)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clipData)
//                    if (Build.VERSION.SDK_INT <= 32) (context as MainActivity).showSnackbarAbovePlayer(R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT)
                    }
                })
                if (vm.feed.paymentLinks.isNotEmpty()) {
                    Text(stringResource(R.string.support_funding_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    fun fundingText(): String {
                        val fundingList: ArrayList<FeedFunding> = vm.feed.paymentLinks
                        // Filter for duplicates, but keep items in the order that they have in the feed.
                        val i: MutableIterator<FeedFunding> = fundingList.iterator()
                        while (i.hasNext()) {
                            val funding: FeedFunding = i.next()
                            for (other in fundingList) {
                                if (other.url == funding.url) {
                                    if (other.content != null && funding.content != null && other.content!!.length > funding.content!!.length) {
                                        i.remove()
                                        break
                                    }
                                }
                            }
                        }
                        val str = StringBuilder()
                        for (funding in fundingList) {
                            str.append(if (funding.content == null || funding.content!!.isEmpty()) context.resources.getString(
                                R.string.support_podcast)
                            else funding.content).append(" ").append(funding.url)
                            str.append("\n")
                        }
                        return StringBuilder(StringUtils.trim(str.toString())).toString()
                    }
                    val fundText = remember { fundingText() }
                    Text(fundText, color = textColor)
                }
                Button(modifier = Modifier.padding(top = 10.dp), onClick = {
                    setOnlineSearchTerms(CombinedSearcher::class.java, "${vm.txtvAuthor} podcasts")
                    mainNavController.navigate(Screens.SearchResults.name)
//                val fragment = SearchResultsFragment.newInstance(CombinedSearcher::class.java, "${vm.txtvAuthor} podcasts")
//                (context as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
                }) { Text(stringResource(R.string.feeds_related_to_author)) }
            }
            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Row {
                Button({ showFeedStats = true }) { Text(stringResource(R.string.statistics_view_this)) }
                Spacer(Modifier.weight(1f))
                Button({
                    mainNavController.navigate(Screens.Statistics.name)
//                (context as MainActivity).loadChildFragment(StatisticsFragment(), TransitionEffect.SLIDE)
                }) { Text(stringResource(R.string.statistics_view_all)) }
            }
        }
    }

    var showEidtConfirmDialog by remember { mutableStateOf(false) }
    var editedUrl by remember { mutableStateOf("") }
    @Composable
    fun EditUrlSettingsDialog(onDismiss: () -> Unit) {
        var url by remember { mutableStateOf(vm.feed.downloadUrl ?: "") }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
            text = {
                TextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    editedUrl = url
                    showEidtConfirmDialog = true
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
    @Composable
    fun EidtConfirmDialog(onDismiss: () -> Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
            text = {
                Text(stringResource(R.string.edit_url_confirmation_msg))
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        runBlocking { updateFeedDownloadURL(vm.feed.downloadUrl?:"", editedUrl).join() }
                        vm.feed.downloadUrl = editedUrl
                        runOnce(context, vm.feed)
                    } catch (e: ExecutionException) { throw RuntimeException(e)
                    } catch (e: InterruptedException) { throw RuntimeException(e) }
                    vm.feed.downloadUrl = editedUrl
                    vm.txtvUrl = vm.feed.downloadUrl
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showEditUrlSettingsDialog by remember { mutableStateOf(false) }
    if (showEditUrlSettingsDialog) EditUrlSettingsDialog { showEditUrlSettingsDialog = false }
    if (showEidtConfirmDialog) EidtConfirmDialog { showEidtConfirmDialog = false }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
            }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                if (vm.feed.link != null && vm.isCallable) IconButton(onClick = { IntentUtils.openInBrowser(context, vm.feed.link!!)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                if (!vm.feed.isLocalFeed) IconButton(onClick = { ShareUtils.shareFeedLinkNew(context, vm.feed)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), contentDescription = "web") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vm.feed.isLocalFeed) DropdownMenuItem(text = { Text(stringResource(R.string.reconnect_local_folder)) }, onClick = {
                        vm.showConnectLocalFolderConfirm.value = true
                        expanded = false
                    })
                    if (!vm.feed.isLocalFeed) DropdownMenuItem(text = { Text(stringResource(R.string.edit_url_menu)) }, onClick = {
                        showEditUrlSettingsDialog = true
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed_label)) }, onClick = {
                        vm.showRemoveFeedDialog = true
                        expanded = false
                    })
                }
            }
        )
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            HeaderUI()
            DetailUI()
        }
    }
}

private const val TAG: String = "FeedInfoScreen"
