package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.net.feed.searcher.PodcastSearcher
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavHostController

var episodeOnDisplay by mutableStateOf<Episode>(Episode())

var feedOnDisplay by mutableStateOf<Feed>(Feed())

var curSearchString by mutableStateOf("")
var searchInFeed by mutableStateOf<Feed?>(null)
fun setSearchTerms(query: String, feed: Feed? = null) {
    curSearchString = query
    searchInFeed = feed
}

var onlineSearchText by mutableStateOf("")
var onlineSearcherName by mutableStateOf("")
fun setOnlineSearchTerms(searchProvider: Class<out PodcastSearcher?>, query: String? = null) {
    onlineSearchText = query ?: ""
    onlineSearcherName = searchProvider.name
}

var onlineFeedUrl by mutableStateOf("")
var onlineFeedSource by mutableStateOf("")
var isOnlineFeedShared by mutableStateOf(false)
fun setOnlineFeedUrl(url: String, source: String = "", shared: Boolean = false) {
    onlineFeedUrl = url
    onlineFeedSource = source
    isOnlineFeedShared = shared
}

var onlineEpisodes = mutableListOf<Episode>()