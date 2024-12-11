# 6.15.6

* tuned video and audio players
* made loading of detailed info more efficient in both video and audio player
* fixed null pointer issue when writing OPML file
* EpisodeHome view is in Compose, has added ability to show hidden text in transcripts (by tapping the eye-glasses on action bar)
* some more components are in Compose
* removed dependency iconics

# 6.15.5

* added note "Media files can only be imported after the DB" on combo import dialog
* fixed VideoMode dialog not being initialized and selection not shown
* revamped video player in Compose
	* switching between full screen and window modes only needs rotating the phone
* reduced heading font size from 28 to 24, and title font size from 22 to 18
* some dialogs are converted to Compose
* removed some unused layout files

# 6.15.4

* fixed issue of export dialog not closing
* fixed progress dialog not showing up in Import/Export preferences
* converted some confirmation dialogs to Compose

# 6.15.3

* setting play state menu is reversed (Ignored on top, Unplayed at bottom)
* when setting an episode state to Played or Ignored, the played position is reset to 0
* in Import/Export settings, there is a new Combo Import/Export
	* it handles Preferences, Database, and Media files combined or selectively
	* all are saved to "Podcini-Backups-(date)" directory under the directory you pick
	* on import, Media files have to be done after the DB is imported (the option is disabled when importing DB is selected)
	* individual import/export functions for Preferences, Database, and Media files are removed
	* if in case one wants to import previously exported Preferences, Database, or Media files, 
		* manually create a directory named "Podcini-Backups"
		* copy the previous .realm file into the above directory
		* copy the previous directories "Podcini-Prefs" and/or "Podcini-MediaFiles" into the above directory
		* no need to copy all three, only the ones you need
		* then do the combo import
* import/export of OPML, Progress, and HTML stay as individual operations
	* Note, progress import/export was useful when migrating from Podcini 5 as the DB can not be imported into Podcini.R, it's not needed when you can import a DB
	* I'm not sure how useful are the HTML exports
* navigations in PreferenceActivity are performed in Compose
* VideoMode dialog is in Compose
* removed the need for versions catalogue for gradle

# 6.15.2.1

* same as previous release, only to change signing key on Google Play

# 6.15.2

* FeedEpisodes is remembered as last opened page and can be opened if default page set to "Remember"
* revamped SubscriptionShortcut activity in Compose to create proper shortcuts on home screen of the phone
* feed shortcut on home screen opens the feed (with FeedEpisodes) in Podcini
* when playing a youtube audio, bitrate is shown in PlayerUI

# 6.15.1

* Consolidated Compose code blocks in PreferenceActivity with function calls
* removed support for widget
* some minor cleanups

# 6.15.0

* added a combo Epiosdes fragment with easy access to various filters
	* merged AllEpisodes, History and Download into Episodes
	* other easy accesses include: New, Planned (for Soon and Later), Repeats (for Again and Forever), Liked (for Good and Super)
	* New episodes can be cleared via the manu
* drawer items customization is disabled in Settings
* drawer includes 5 most recent feeds
* rearranged routines in ImportExportPreferences
* importing OPML file is done within the activity, no longer starts OpmlImportActivity
* removed OpmlImportActivity
* getting shared youtube media has a new interface to select feed
* cleaned up SwipeActions, removed the need for filter
* more Compose migrations
* media3 update to 1.5.0

# 6.14.8

* fixed issues in tags setting
* fixed initial sort order not taking correctly in FeedEpisodes
* fixed Compose switches not settable in Preferences
* skip dialog is in Compose and features number input rather than multiple choice
* made the numbers under rewind and forward buttons in PlayerUI react to changes
* all preferences are in Compose except some dialogs
* some class restructuring

# 6.14.7

* corrected some deeplinks in manifest file on OPMLActivity
* added commentTime in Episode and Feed to record the time of comment/opinion added
* when adding/editing a comment/opinion, a time stamp is automatically added in the text field
* when removing a feed or an episode, a time stamp is automatically added in the text field
* a new sorting item on episodes based on commentTime
* in episodes list, if an episode belongs to a synthetic feed, tapping on the image will bring up EpisodeInfo instead of FeedInfo
* some class restructuring
* more preferences fragments are in Compose

# 6.14.6

* fixed issue of unable to input in rename feed
* fixed issue of marking played after playing even when the episode has been marked as Again or Forever
* fixed duration not shown in narrow episode lists
* some preferences fragments are in Compose
* temp message on NavDrawer for closed testing

# 6.14.5

* minor adjustments in episode lists layout
* improved Tags setting a bit
* adjusted colors on the multi-selection menus

# 6.14.4

* a new speedometer on the player UI
* adjusted padding in advanced options in OnlineSearch view
* in episode lists, added view count (available in Youtube contents)
* amended episode lists layout
	* playState marking on the image is removed
	* title is at the top and takes full length to the right of the image
	* the action button (play/pause etc) is at the lower right corner may overlay on other text if any
* colored some actionable icons
* created a new layout for FeedEpsiodes with a larger image, more suitable for video contents
* in feed settings created useWideLayout for choosing the desired layout
* reworked speed setting dialogs in Compose and removed unused old codes

# 6.14.3

* fixed crash when constructing TTS
* enhanced some TTS handlings including getting the duration after TTS audio is constructed
* issue: some shared playlists from YT Music are circular
	* getting mostly duplicates of over 2000 episodes when subscribed
	* checking on unique url seems to miss some episodes
* Tags setting and AutoDownload filer dialogs are in Compose

# 6.14.2

* in feed settings, added audio type setting (Speech, Music, Movie) for improved audio processing from media3
* improved the behavior of the cast player in the Play app
	* casting youtube audio appears working fine

# 6.14.1

* changed the term "virtual queue" to "natural queue" in the literature to refer to the list of episodes in a given feed
* ignore updating episodes with duration of less than 1 second
* start using Podcini's own cast provider in the play app
	* audio-only youtube media can now be cast to speaker (experimental for now)
	* cast of any video is disabled for now
* OnlineSearch fragment is in Compose
* minor Compose amendments in fragments of Search, About
* updates in documents of required licenses

# 6.14.0

* fixed crash when adding podcast (introduced since 6.13.11)
* naming changes in PlayState: InQueue -> Queue, InProgress -> Progress
* PlayState Queue is user settable, once set, the episode is put to associated queue of the feed
* in getting next to play in a natural queue, PlayStates Again and Forever are included
* fixed the not-updating queue and tag spinners in Subscriptions
* various dates display are in flex format
* in Statistics, data for today are shown in the HH:mm format
* added view count for Youtube and YT Music media
* reworked episodes sort routines in Compose
* re-colored border color for Compose dialogs
* changed sort items' direction icon
* QuickDiscovery fragment is in Compose

# 6.13.11

* created private shared preferences for Subscriptions view and moved related properties there from the apps prefs
* persisted settings of tag spinner and queue spinner in Subscriptions view
* fixed again the incorrect initial text on Spinner in Queues view
* save played duration and time spent when playback of an episode is completed
* some code cleaning and restructuring
* gradle update

# 6.13.10

* fixed Spinner in Subscriptions: All tags vs Untagged irregularity
* fixed video mode setting not available in first created synthetic feed
* created a new dialog to rename or create synthetic feed
* in Subscriptions menu, "New synthetic Youtube" is merged into "New synthetic feed"
* removed "Switch queue" option from all menus (not necessary)
* fixed Spinner in Queues: default text not updating
* fixed menu in Statistics
* corrected duration and timeSpent numbers for today in Statistics
* added a Statistics section in README.md
* added "Never ask again" in dialog for unrestricted background permission
* various prefs accesses are made lazy
* build apk dropped to target Android 14 due to some reported issued with Android 15.

# 6.13.9

* made Spinner in Queues view update accordingly
* if playing from the natural queue in FeedEpisodes, the next episode comes from the filtered list
* when playing another media, post playback is performed on the current media so as to set the right state
* fixed timeSpent not being correctly recorded
* further enhanced efficiency of statistics calculations
	* in Statistics, feeds with no media started or downloaded are not shown
* furthered Statistics view into Compose
* added online homepage item in Settings->About to open the Github page

# 6.13.8

* Subscriptions sorting added the negative sides of downloaded and commented
* added border to Compose dialogs
* in EpisodeMedia added timeSpent to measure the actual time spent playing the episode
	* upon migration, it's set the same as playedDuration, but it will get its own value when any episode is played
* redid and enhanced Statistics, it's in Compose
	* pie chart is replaced with line chart
	* in Subscriptions, in the header and every feed, added timeSpent
	* in Subscriptions header, added usage of today
	* on the popup of a feed statistics, also added "duration of all started"
	* the "Years" tab is now "Months", showing played time for every month, and added timeSpent for every month
* enhanced efficiency of getting statistics for single feed
* in FeedInfo details, added "view statistics of this feed" together with "view statistics of all feeds"

# 6.13.7

* likely fixed getting duplicate episodes on updates (youtube feeds)
* in Queues view, set the spinner default to the active queue
* an all new sorting dialog and mechanism for Subscriptions
* removed subscriptions sort order in Settings
* set PlayerUI position on start play
* large number of unused string resources are cleaned out

# 6.13.6

* created a fast update routine to improve performance
	* all updates (swipe or scheduled etc) run the fast update routine
	* full update (for which I don't see the reason) can be run from the menu of Subscriptions view
* the full update routine is also enhanced for performance
* enhanced youtube update routines to include infinite new episodes updates
* added update routine for youtube channel tabs
* added audio and video quality settings in Feed Preferences (Youtube feeds only): Global, Low, Medium, High
	* these settings take precedence over global situations
	* when Global is set, video is at lowest quality, and audio is at highest quality (except when prefLowQualityMedia is set for metered networks)
* fixed mis-behavior of multi-selection filters

# 6.13.5

* hopefully fixed youtube playlists/podcasts no-updating issue
* in OnlineFeed, when subscribing to a Youtube channel, a popup allows to choose a tab (if multiples are available)
	* tab options include Videos (or customized name, the default tab) and Streams (live tab)
	* only one tab can be chosen at a time
	* the tab name will be appended to the feed title
	* each tab results in a feed/podcast in Podcini
* the shortcut way of subscribing (long-click on a search result) only subscribes to the default tab
* episodes limits on subscribing to YT channels/playlists/podcasts are raised to 2000
* fixed toolbar contrasts on Queues view

# 6.13.4

* in Queues view, reworked the spinner in Compose and added associated feeds toggle
* added info bar and swipe actions to Search fragment
* spinners in Subscriptions view are in Compose
* removed the "add" floating button in Subscriptions view
* removed some unused dependencies and references to their licenses

# 6.13.3

* on playing the next episode in queue, if its state is lower than InProgress, the state is set as such
* added "add commment" in swipe actions
* in FeedEpisodes, fixed incorrect ordering of episodes after filtering
* when removing episode media, if remove-from-queue is set, the episode is removed from all queues rather than only the active queue
* removed the @UnstableAPI annotations previously required for Media3
* updated Contributing.md
* various dependencies update

# 6.13.2

* replaced the setting of prefSmartMarkAsPlayedSecs by an adaptive internal val smartMarkAsPlayedPercent = 95
	* if the episode is played to over 95% of its duration, it's considered played
* reworked the chain actions of set play state, remove from queue, and delete media
* fixed the issue of fully played episode being marked as Skipped
* fixed speed indicator not updating issue in PlayerUI
* added Again and Forever states to episode filter
* in Settings, "Keep favorite episode" is changed to "Keep important episodes", and it applies to episodes set as Super, Again, or Forever
* some reduction in access to Preferences files for improved efficiency

# 6.13.1

* fixed the misbehavior (from 6.13.0) of rewind/forward/progress in PlayerUI
* added Again and Forever in PlayState, and changed some PlayState icons
* in episodes list, when an episode's play state is higher than Skipped, state icon (rather than only a check) is on the cover image
* in Combo swipe actions, removed NoAction and ComboAction

# 6.13.0

* updates playback position adaptively (in app and in widget) in an interval being the longer of 5 seconds and 2 percent of the media duration
	* this can be enabled/disabled in Settings->Playback->"Update progress adaptively", default to true
	* unadaptive interval, same as the previous, is 2 seconds
* added volume adaptation control to player detailed view to set for current media and it takes precedence over that in feed settings
* tuned the AudioPlayer fragment
* added a few new actions to swipe, bring it essentially equivalent to multi-select menus
* added auto-download policy: "Marked as Soon"
* when deleting media file, set the playState to Skipped only if the current state is lower than Skipped
* during cast to speaker (in the Play app), tap on the position bar in the PlayerUI changes the position
* avoided the snack message "can't delete file ..." after streaming an episode
* fixed (again, sorry) the action button not updating issue after download in episodes lists
* google cast framework is updated to 22.0 (in the Play apk)

# 6.12.8

* set episode's playState to Skipped when its media file is removed
* in AutoDownloadPolicy, Newer or Older now include episodes with playState of New, Unplayed, Later and Soon
* naming change in Rating: Neutral -> OK, Favorite -> Super
* in EpisodeInfo header, only show "add to queue" icon when the episode is not in queue
* in-queue icon is no longer shown on episodes lists
* reduced start lag when opening a large episodes list
* likely fixed the action button not updating issue after download in episodes lists
* in grid layout of Subscriptions view, moved the rating icon to the lower left corner
* in FeedEpisodes view, enabled Downloaded and HasMedia filters
* changed in Settings -> Downloads, "Mobile updates" to "Metered network settings"
	* the setting applies to metered mobile and wifi networks

# 6.12.7

* fixed issue in auto-download where number of played is not correctly counted
* in AutoDownloadPolicy, Newer or Older now include episodes with playState not being Skipped, Played, and Ignored
* in Subscriptions sorting, Played means Skipped, Played, or Ignored, Unplayed means any state lower than Skipped
* fixed high CPU usage in Download view
* removed a few useless classes and the useless test modules

# 6.12.6

* in SearchFragment, made search criteria options are togglable, and added back search online option
* further enhanced filtering routines
	* categories with multi-selections are expandable/collapsable
	* once expanded, added three icons: <<<, X, >>>
	* tapping on <<< will select all lower items, >>> all higher items, X to clear selection in the category
* manual sorting of Queues is back.
	* The way to enable/disable it, uncheck/check "Lock queue" in menu
	* if "Lock queue" doesn't appear, top "Sort" in the menu and then, uncheck "Keep sorted" in the popup
* in episodes lists, swipe dialog shows up when the swipe has not been configured
* changed the info shared from FeedInfo and FeedEpisodes, now it shares simply the download url of the feed

# 6.12.5

* fixed a long-standing issue in the play apk where rewind/forward buttons don't work during cast

# 6.12.4

* bug fixes and enhancements in filters routines
* in SearchFragment, added search criteria options: title, author(feed only), description(including transcript in episodes), and comment (My opinion)
* feed list in SearchFragment is in Compose

# 6.12.3

* reworked and expanded the filters routines for episodes and feeds
	* various criteria have been added
	* if a group of criteria has two options, they are mutually exclusive
	* if there are more options in a group, multi-select is allowed and filter uses OR for the intra-group selections
	* on selections across groups, the filter uses AND

# 6.12.2

* fixed play not resuming after interruption (watch for any side effects)
* fixed incorrect initial play button on PlayerUI
* fixed startup delay when curMedia is null
* rating list in popup for Subscriptions is reversed (favorite on top)
* first migration of Episodes and Feeds filters to Jetpack Compose
* added has/no comments in the filters
* fixed some errors in Episodes filter

# 6.12.1

* fixed circular calling functions when PlayerDetailed view is open
* in the Play apk, added back the cast button that's been missing after 6.10.0

# 6.12.0

* created a new play state system: Unspecified, Building, New, Unplayed, Later, Soon, InQueue, InProgress, Skipped, Played, Ignored,
	* among which Unplayed, Later, Soon, Skipped, Played, Ignored are settable by the user
	* when an episode is started to play, its state is set to InProgress
	* when episode is added to a queue, its state is set to InQueue, when it's removed from a queue, the state (if lower than Skipped) is set to Skipped
	* in Episode filter, for now, Unplayed is all lower than Played, while Played includes Played, Ignore
* fixed issues from last release: action button not update, icon at PlayUI not update
* re-worked the header layouts of FeedInfo and FeedEpisodes
* tuned to show PlayerUI in proper conditions
* enabled open NavDrawer in LogsFragment
* in list view of Subscriptions, moved the rating icon to the title
* NavDrawer is fully in Compose
* cleaned out some unused or redundant stuff

# 6.11.7

* added author and title info in SharedLog
* when shared channel, playlist or podcast from Youtube, double checks if existing and records SharedLog accordingly
* in Shared LogsFragment, tap on a successful or existing item (media or feed) opens the corresponding fragment
* in Subscriptions view, added total duration for every feed
* hasEmbeddedPicture in EpisodeMedia is set to not persist for now
* tuned Compose routines ti reduce recomposition and improve efficiency

# 6.11.6

* fixed a serious performance issue when scrolling list of episode having no defined image url

# 6.11.5

* back to be built to target Android 15
* request for permission for unrestricted background activity
* requests for permission are now less aggressive: if cancelled, Podcini does not quit
* reversed rating list in popup (favorite on top)
* if a shared Youtube media already exists, record shared log as such
* in episodes list, made the progress bar taking the full width
* in FeedEpisodes header, the count now reflects filter
* in Subscriptions, feed count now reflects filter
* minor layout adjustments

# 6.11.4

* corrected color contrast in SwipeActions dialog
* removed the empty space on top of playerUI
* largely improved scroll performance of episodes list caused by image loading
* fixed title text out of screen issue in some headers
* fixed swipe actions not initialized in Queue Bin
* in FeedInfo details, removed inapplicable items for synthetic feeds
* when removing synthetic feed, record all episodes in the feed in SubscriptionLog
* updated some Compose dependencies
* speed-dial dependency removed and old bottomSheet multi-select codes cleaned up

# 6.11.3

* supports Youtube live episodes received from share
* fixed info not showing when playing video in window mode
* AudioPlayer is fully in Compose, fixed the issue of top menu sometimes not shown
* if you have podcast set to AudioOnly, you can tap on the square icon on the top bar of PlayerDetailed to force play video
	* this will re-construct the media item for the current episode to include video and plays audio-video together
	* it continues this way even after you close the video view and only listen
	* during this mode, you can switch between video and audio and the play is uninterrupted
	* it will resume playing audio only when you switch episodes and comeback to it

# 6.11.2

* fixed PlayerDetailed view not showing full info on Youtube media
* further revamped SwipeActionsDialog, now in Jetpack Compose, and related old files are removed
* fixed again issue of rating not updating on PlayerDetailed view when episode changes
* enabled "Erase episodes" in multi-select menu when episodes from a synthetic feed are selected
* erased episodes are recorded in SubscriptionLog for future reference

# 6.11.1

* made rating icon consistent with other views and fixed issue of rating not updating on PlayerDetailed view when episode changes
* fixed remote episode info not shown in OnlineFeed
* fixed and enhanced chapters control in PlayerDetailed view
* ChaptersDialog is in Jetpack Compose
* fixed not being able to set video mode in newly created synthetic feed
* fixed buttons contrast on FeedEpisodes hearder
* reworked SwipeActionsDialog layout, removed the mock episode view
* removed ChaptersFragment and related
* reorganized some class structures

# 6.11.0

* added SubscriptionLog to record unsubscribe history
* in online SearchResults, if an item has been subscribed but removed, a X mark appears on the cover image,
* in OnlineFeed, added prior rating, opinion and cancelled date on a feed previously unsubscribed
* renamed SharedLog fragment to LogsFragment and merged shared, subscription and download logs into the fragment
	* the count of LogsFragment on NavDrawer is the sum of the three logs
* added Unrated to the rating system and set episodes default rating to Unrated
* added the same rating system to podcast/subscription/feed
* added comment/opinion to podcast/subscription/feed
* in FeedInfo, added rating telltale in the header and "My opinion" section under the Description text
* in Subscriptions view, added rating on the icon of every podcast
* in Subscriptions view, added set rating in multi-selection menu
* in RemoveFeedDialog, added delete reason text input for SubscriptionLog
* added Combo swipe action with ability to choose any specific action
* changed shift rating action to set ration action with a popup menu
* in Subscriptions grid view is set adaptive with min size of 80 with equal image size
* on the header of FeedInfo and FeedEpisodes, added background image and removed the dark bar
* in EpisodeInfo, show current status with telltale icons of played and inQueue (rather than reversed in prior version)
* various minor fixes with selections
* DownloadLog fragment removed

# 6.10.0

* in Subscriptions, added menu items to create normal or Youtube synthetic feeds for better organization
* added "Shelve to synthetic" in multi-selection menu to move/copy the selected to a synthetic feed
	* episodes from normal podcasts can only be copied, while those from synthetic podcasts can be moved
* clicking on the image in Player UI toggles expand and collapse of the player detailed view
* when receiving shared single media from Youtube, wait for episode construction before dismissing the confirm dialog
* fixed Reconcile crash when episode.media is null
* in OnlineFeed, button "Subscribing" is changed to "Subscribe"
* tunes color contrast on some Compose buttons
* in EpisodeInfo, menu items "mark played" and "add to queue" are made as buttons and telltales
* cleaned up menu items handling in EpisodeInfo and AudioPlayer, removed EpisodeMenuHandler
* fixed a bug of episode properties possibly getting overwritten when changing episode play status
* in NavDrawer, the count for Queues is from all queues (previously from curQueue only)
* count of shared logs is shown on NavDrawer
* set app icon as default when cover images are unavailable

# 6.9.3

* fixed app quit issue when repairing a shared item
* fixed custom queue spinner not showing up (issue introduced in 6.8.3 when migrating to Material3)
* updated dialog popup mechanism in FeedSettings
* updated UI to reflect the new rating system
* a couple dialog converted to Compose and removed EpisodeMultiSelectHandler

# 6.9.2

* fixed getting 0 episodes with Youtube playlist etc
* added new ratings for episodes: Trash, Bad, Neutral, Good, Favorite
* previous Favorite is migrated to the new ratings
* "Add to favorite" SwipeActions is changed to "Switch rating" SwipeActions, each swipe shifts the rating in circle
* "Add to favorite" in multi-selection is changed to "Set rating"
* in EpisodeInfo, added "My opinion" section under the Description text,
	* by clicking on it you can add personal comments/notes on the episode, text entered is auto-saved every 10 seconds
* adopted Material3's built-in color scheme

# 6.9.1

* added logging for shared actions
* added simple fragment for viewing shared logs and repairing failed share actions
* likely fixed the abnormal behavior of currently playing in Queues
* in NavDrawer, added three recently played podcast for easy access
	* the play time of a podcast is recorded when an episode in the podcast starts playing with FeedEpisodes view of the podcast open
* fixed color contract on info bar of FeedEpisodes
* NavDrawer and DownloadLog are in Jetpack Compose

# 6.9.0

* re-worked Compose states handling for Episodes lists, likely fixed related issues
* opening OnlineFeed of Youtube channel is made more responsive with more background processing on constructing episodes
	* you can subscribe at any time
	* if you open Episodes, you will see the episodes constructed at the moment
* episodes limit for Youtube channel, playlist and YTMusicplaylist is now at 1000
* in OnlineFeed view, after subscribe, the FeedEpisode view does not open automatically, presenting options to open it or return to the SearchResults view
* in online SearchResults, if an item is already subscribed, a check mark appears on the cover image, and when clicked, FeedEpisodes view is opened.
* added FlowEvent posting when adding shared youtube media or reserved online episodes
* receiving shared contents from Youtube now should support hostnames youtube.com, www.youtube.com, m.youtube.com, music.youtube.com, and youtu.be
* when reserving episodes from a Youtube channel list, like receiving shared media from Youtube, you can choose for "audio only"
	* the reserved episodes will be added into synthetic podcast of either "Youtube Syndicate" or "Youtube Syndicate Audio" rather than "Misc Syndicate" for other types of episodes
* fixed independence of swipe actions in Queues Bin
* OnlineFeed is in Jetpack Compose
* in SharedReceiver activity, added error notice for shared Youtube media

# 6.8.7

* clear history really clears it
* fixed deselect all in episodes and podcasts lists
* consolidated OnlineFeed and SearchResults classes to use the common FeedBuilder class
* cleared the error icon on subscription grid
* in Grid view of Subscriptions, click and long-click is received on the entire block of a podcast
* in episodes list of an online feed (unsubscribed), multi-selection of episodes now allows to reserve them
	* once reserved, the episodes are added to a synthetic podcast named "Misc Syndicate"
* fixed PlayerDetailed view showing wrong information or even crashing
* tuned Material3 colorscheme

# 6.8.6

* Queues Bin view now has separate swipe actions independent from the Queues view
* SearchResults and Discovery fragments are in Jetpack Compose
* in online search result list, long pressing on a feed will pop up dialog to confirm direct subscription
* fixed crash when clearing history
* combined mixed ways of recognizing episodes in history: a) by last played time and b) by completion date
* swipe to remove an item from history actually removes it
* fixed a bug in episodes and subscriptions lists, where exiting select mode or deselecting all not resetting all selected episodes
* OnlineFeedsAdapter etc are removed
* various dependencies update

# 6.8.5

* migrated mostly the following view to Jetpack Compose:
	* Queues, AudioPlayer, Subscriptions, FeedInfo, EpisodeInfo, FeedEpisodes, AllEpisodes, History, Search, and OnlineFeed
* to counter this nasty issue that Google can't fix over 2 years: ForegroundServiceStartNotAllowedException
	* for this and near future releases, target SDK is set to 30 (Android 11), though built with SDK 35 and tested on Android 14
	* supposedly notification will not disappear and play will not stop through a playlist
	* please voice any irregularities you may see
* on episode lists, show duration on the top row
* added toggle grid and list views in the menu of Subscriptions
* added option to refresh all subscriptions in menu of Queues view
* added telltale of subscription updating status "U" on infobar in Queues view
* AudioPlayer got overhauled. with PlayUI and PlayerDetailed fragments Removed
* Episodes viewholder, feed viewholder, and related adapters etc are removed
* SwipeActions class stripped out of View related operations
* migrated reliance on compose.material to compose.material3
* tuned and corrected some Compose issues

# 6.8.4 (Release candidate)

* on episode lists, show duration on the top row
* added option to refresh all subscriptions in menu of Queues view
* most of FeedInfo fragment is in Jetpack Compose
* selectable adapter etc are removed
* more Compose enhancements and bug fixes
* might be a release candidate

# 6.8.3 (Preview release)

* most of Subscriptions view are in Jetpack Compose, feed viewholder and adapters etc are removed
* added toggle grid and list views in the menu of Subscriptions
* migrated reliance on compose.material to compose.material3
* not yet for prime time

# 6.8.2 (Preview release)

* AudioPlayerFragment got overhauled. migrated to Jetpack Compose and PlayUI and PlayerDetailed fragments are Removed
* EpisodeInfo is now in Compose
* SearchFragment shows episodes list in Compose
* Episodes viewholder and adapter etc are removed
* SwipeActions class stripped out of View related operations
* more enhancements in Compose functionalities
* still have known issues

# 6.8.1 (Preview release)

* made Queues view in Jetpack Compose
* enhanced various Compose functionalities
* not yet ready for serious usage

# 6.8.0 (Preview release)

* the Compose class of DownloadsC replaces the old Downloads view
* FeedEpisodes, AllEpisodes, History, and OnlineFeed mostly migrated to Jetpack Compose
* there are still known issues and missing functions

# 6.7.3

* fixed bug in nextcloud auth: thanks to Dacid99's PR
* fixed "filtered" always shown in Downloads info bar
* minor enhancement in multi-select actions handling
* on-going work to replace recycler view, recycler adapter and view holder for Episodes with Jetpack Compose routines
* introduced "DownloadsC", an early preview (not fully implemented) of the Compose construction

# 6.7.2

* added menu item for removing feed in FeedInfo view
* menu item "Switch queue" is changed to "Switch active queue"
* Youtube and YT Music podcasts can be shared to Podcini
* initial max number of loaded items for Youtube and YT Music playlist and podcast is set 500
* initial max number of loaded items for Youtube channel is set 500
* added some error dialogs when handling shared links
* updated some dependencies including Compose
* compile and target SDK's are upped to 35

# 6.7.1

* ensured duplicate episodes are removed from secondary checking during refresh
* refresh progress is updated in notification

# 6.7.0

* largely improved efficiency of podcasts refresh, no more massive list searches

# 6.6.7

* volume adaptation numbers were changed to 0.2, 0.5, 1, 1.6, 2.4, 3.6 to avoid much distortion
* sleep timer and related menu item are enhanced

# 6.6.6

* fixed difference between the count of episodes and the actual number of episodes (seen when the number is small) in History, AllEpisodes, and OnlineFeed views
* fixed an error (introduced in 6.6.3) in setting feed preferences when subscribing one
* upon start, if no previously played media, the player is set invisible
* fixed an issue of fully played episode not correctly recorded as such when Podcini is in the background
* volume adaptation numbers were changed from 0.2, 0.5, 1, 1.5, 2, 2.5 to 0.2, 0.5, 1, 2, 4, 7 (the max boost being 6dB)
* Kotlin upped to 2.0.20
* Realm upped to 2.3.0

# 6.6.5

* fixed the issue of subscriptions not filtered correctly.

# 6.6.4

* added ability to receive music or playlist shared from YT Music
* single music received from YT Music is added to "YTMusic Syndicate"
* in newly subscribed Youtube channel or playlist, prefStreamOverDownload is set to true
* ensured natural queue (continuous streaming) for previously subscribed "Youtube" type feed when the associated queue is set to None
* the max number of media is dropped to 60 on subscribing a Youtube type feed (higher number may cause delay or even failure due to network issues, will investigate other ways to handle)
* fixed a bug in Reconcile that can cause downloaded files to be mistakenly deleted
* action button on each episode now reacts to LongClick to provide more options

# 6.6.3

* added ability to receive shared Youtube playlist, quite similar to receiving shared Youtube channel
* increased the max number of media to 300 on subscribing a Youtube channel or a Youtube playlist
* ensures to get and save full description when opening a Youtube media from a list

# 6.6.2

* fixed issue of filter "never auto delete" in Subscriptions view
* corrected issue of "queued/not queued" filter for episodes
* filter on "queued/not queued" is checked for all queues (not only the active queue)
* added filtering feature to Downloads view
* changed associated queue of Youtube Syndicate to None (rather than active set previously)
* in Subscriptions view, the "search" box is changed to a "Queues" spinner to filter by associated queue

# 6.6.1

* the confirm dialog is more responsive when receiving a youtube media share
* receiving a youtube media share can be dismissed by not pressing the confirm button
* in Subscriptions and FeedEpisodes views, swipe down to refresh no longer has the progress circle blocking UI, only shows "Refreshing" status on the info bar
* added preference "Prefer low quality on mobile" under Settings -> Playback -> Playback control, and default it to false,
	* if set true, Youtube media will use low quality audio on mobile network (which has been the default way of handling)

# 6.6.0

* added ability to receive shared Youtube media,
	* once received, the user can choose to set it as "audio only" before confirm
	* the media is then added as an episode to one of the two synthetic podcasts: "Youtube Syndicate" or "Youtube Syndicate Audio" (for audio-only media)
	* the two synthetic podcasts behave as normal Youtube-channel podcasts except that they can not be updated, and video mode and authentication can not be changed,
	* the episodes can be handled in the same fashion as normal podcast episodes, except that those in "Youtube Syndicate Audio" can not be played with video
* fixed info display on notification panel for Youtube episodes
* added a setting to disable "swipe to refresh all subscriptions" under Settings -> Interface -> Subscriptions
	* even when disabled, subscriptions can be refreshed from the menu in Subscriptions view
	* this doesn't affect "swipe to refresh" in FeedEpisodes view for single podcast
* updated various compose dependencies

# 6.5.10

* fixed crash when switching to a newly created queue in Queues view
* reset temp speed when manually playing a new episode to ensure preset speed is used

# 6.5.9

* partially fixed an issue seen on Samsung Android 14 device where after playing the user-started episode, subsequent episode in the queue is not played in foreground service and there is not notification panel and can get stopped by the system.
	* the current fix is though the subsequent episodes are still played without notification, the play is not stopped by the system.
* if videoMode of a feed is set to "audio only",
	* press on icon in the player UI will expand the player detailed view (rather than video view)
	* "show video" on the menu of AudioPlayer view is hidden
* some class restructuring

# 6.5.8

* corrected mis-behavior of speed settings for video media
* likely fixed issue of duplicates or absence of playing episode seen sometimes in Queues view
* reduced some unnecessary posting of events
* removed setting of videoPlaybackSpeed, audio and video speed how handled in the same way
* removed incomplete handling of flash media previously used to handle youtube media

# 6.5.7

* in every feed settings, in case the preferences are not properly set, auto-download is by default disabled
* fixed mis-behavior of entering number in textfield in FeedSettings
* fixed the issue of ShareReceiver not detecting url or plain text correctly

# 6.5.6

* in feed preferences, the setting "play audio only" for video feed is replaced with the setting of a video mode.  If you set the previous setting, you need to redo with the new setting.
* added some extra permission requests when exporting/importing various files, maybe needed in some system
* re-enabed use of http traffic to work with relevant podcasts

# 6.5.5

* corrected issue of Youtube channel being set for auto-download when subscribing
* fixed various issues on video sizing and further refined the video player
* some class restructuring and refactoring and nullalability adjustments
* updated various dependencies

# 6.5.4

* in the search bar of OnlineSearch view, search button is moved to the end of the bar
* new way of handling sharing of youtube channels from other apps
* normal text (other than url) shared from other apps is taken by OnlineSearch view for podcast search
* preparing mediaSouces is done in IO scope preventing network access blocking Main scope
* some class restructuring and refactoring

# 6.5.3

* properly assigning ids to remote episodes in OnlineFeedView to resolve the issue of duplicates
* fixed possible startup hang when previous media was Youtube media
* the fixed for random starts in 6.4.0 conflicts with notification play/pause button, narrowed handling to only KEYCODE_MEDIA_STOP
* some fragment class restructuring

# 6.5.2

* replaced all url of http to https
* resolved the nasty issue of Youtube media not properly played in release app

# 6.5.1

* further improved behavior in video player, seamless switch among audio-only, window and fullscreen modes, and automatical switch to audio when exit
* fixed issue of OnlineFeed view not transition to FeedEpisodes view when subscribing a Youtube channel

# 6.5.0.1

* fixed release app being improperly stripped of classes by R8

# 6.5.0

* media3 upped to 1.4.1
* removed dependency of androidx.media
* minSDK bumped to 24 (Marshmallow no longer supported, sorry)
* in OnlineSearch view, info about subscriber counts (when available) are added to podcasts
* likely fixed video fragment crash due to: java.lang.IllegalStateException: Fragment VideoEpisodeFragment
* improved behavior of toggling video view modes
* added setting in feed preferences to play audio only for video feeds
* added feature to truely handling of Youtube channels
* Youtube channels can be searched in OnlineSearch view and browsed, played or subscribed in OnlineFeed view
* Youtube media are played within Podcini, in video pr audio modes
* in feed setting of Youtube channels, "Prefer streaming" and "Auto download" options are disabled
* limitations:
	* Youtube channels search currently only showing up to 20 results
	* A YouTube channel is only fetching up to 30 media
	* Youtube media can only be streamed (not downloadable for now)
	* video quality is set to lowest, audio quality is set to highest on wifi and lowest on mobile (not customizable now)
	* Youtube feed is not auto-downloadable

# 6.4.0

* PlaybackService is now a MediaLibraryService and also takes over many functionalities from PlaybackController
* PlaybackController is renamed to ServiceStatusHandler and is stripped to bare bones
* enabled Android Auto UI, currently playing episode and episodes in the active queue are shown in the Auto UI
* added code to handle case where keycode=-1 and keyEvent!=null, attempting to resolve the occassional issue of random start playing

# 6.3.7

* inlined some DB writes of Episodes in some routines
* enhanced DB writes in download routine, fixed a write error
* added a couple more Log.d statements in hope for tracking down the mysterious random playing
* Kotlin upped to 2.0.10

# 6.3.6

* upgraded gradle to 8.9 and Android Gradle Plugin to 8.5.2
* minor dependencies updates
* corrected the count of total episodes in History view
* corrected the count of selections in AllEpisodes, History views
* fixed "add selected to queue" in History, AllEpisodes views
* known issue: in History and AllEpisodes views, the play button on every episode is not convertible
* added history count in NavDrawer
* in adapters, made selectedItems type explicit (rather than of Any), either of Episode or of Feed
* explicitly revoked DB monitoring in episodes list views at exit
* fixed issue of IndexOutOfBoundsException when adding to an empty queue

# 6.3.5

* added more Log.d statements in hope for tracking down the mysterious random playing
* FeedSettings view is all in Jetpack Compose, FeedSettingsPreferenceFragment removed
* in FeedSettings, added "Auto add new to queue" (accessible when associated queue not set to "None")
	* when set, new episodes during refresh will be added to the associated queue, regardless of being downloaded
* use adaptive date formats (stripped time) in Subscriptions view

# 6.3.4

* fixed mis-behavior of setting associated queue to Active in FeedSettings
* items on dialog for "Auto delete episodes" are changed to checkbox
* playState Int variables have been put into enum PlayState
* corrected an error in incomplete reconsile
* fixed the nasty mis-behavior in Queues view when removing episodes
* updated feed in FeedInfo view when feed preferences change
* enhanced feed setting UI, added display of current queue preference
* added "prefer streaming over download" in feed setting. ruling along the global setting, streaming is preferred when either one is set to true
* added None in associated queue setting of any feed
	* if set, episodes in the feed are not automatically added to any queue, but are used as a natural queue for getting the next episode to play
	* the next episode is determined in such a way:
		* if the currently playing episode had been (manually) added to the active queue, then it's the next in queue
		* else if "prefer streaming" is set, it's the next unplayed episode in the feed episodes list based on the current sort order
		* else it's the next downloaded unplayed episode

# 6.3.3

* fixed crash when setting as Played/Unplayed in EpisodeInfo view
* various changes in writing to DB in write block
* Queue view is renamed to Queues view
* in Queues view, when opening/closing Bin, the queues spinner on ToolBar is toggled with a title
* added various Log.d statements in seeking to trace down the occasional random playing behavior

# 6.3.2

* fixed crash of opening FeedEpisode view when "Use episode cover" is set
* fixed crash of opening EpisodeInfo view on episode with unknown media size
* fixed crash when cancelling download in a auto-download enabled feed
* fixed mis-behavior of "Untagged" filter in combination with other filters in Subscriptions view
* added "export selected feeds" in multi-select menu in Subscriptions view

# 6.3.1

* fixed crash when playing episode with missing media file
* fixed Queue view not opening the current queue

# 6.3.0

* improved handling of playing next in queue when some items have been removed from the queue
* changed toggle play state logic: New or Unplayed -> Played, Played -> Unplayed
* episodes list views are more efficient and less error-prone, reduced dependence on events and relying more on DB live updates
* added episode counts in queues list spinner in Queue view
* resorted handling of various DB objects to managed type
* took out the long-useless context menu codes from episodes list views
* fixed crash when toggling Podcini during downloads
* calmed some complaints on StrictMode policy UntaggedSocketViolation
* adjusted some Composable sizes

# 6.2.2

* added EpisodeMedia null relationship handling
* in sleep timer setting, added "to end of episode" option
* frequency of sleep timer check is reduced to every 10 seconds (from 1 second)
* in Queue bin view, items' order is changed to descending
* in Queue bin view, disabled some menu items
* refined "remove from queue" operation when an episode ended playing
* eliminated the double starts when playing the next episode in queue
* re-ensured circular queue

# 6.2.1

* likely fixed crash issue in Queue view during download
* fully disabled down-swipe in Queue, AllEpisodes, History, and Downloads views

# 6.2.0

* first foot into Jetpack Compose, various views are in Compose
* in Queue view, added menu option to change the name of the active queue, with a Compose dialog
	* Default queue can not be changed, only unique names are allowed
* in Queue view, added menu option to add a new queue, maximum number of queues allowed is 10
* in Downloads view, revealed the message toaster for Reconsile operation
* added associated queue setting for feed, with three choices: Default, Active and Custom
	* the default queue for every feed is Default
* episodes added to queue upon downloading are added to the respective queues associated with the feed
* fixed crash issue when multi-select actions in Subscriptions
* added associated queue setting in multi-select actions in Subscriptions
* playing an episode from an episodes list other than the queue is now a one-off play: no "next episode" to play, unless the episode is on the active queue, in which case, the next episode in the queue will be played
* corrected the wrong displays in DownloadLog view
* likely fixed crash issue when Podcini comes back from background during download
* likely fixed a crash issue when deleting the last playing media in queue
* updated various dependencies, media3 is up at 1.4.0

# 6.1.6

* enabled swipe actions in Queue bin view (same actions as in Queue)
* both icons of show bin and back to queue are changed to be more intuitive
* bin items are sorted based on the update time
* added a Reconsile feature in Downloads view that verifies episodes' download status with media files in system and performs cleanup
* likely fixed syncing with nextcloud and gpoddernet servers.

# 6.1.5

* minor adjustments on FeedInfo page, especially for handling long feed title
* disabled feed updates via menu or down-swipe in Queue, AllEpisodes, History, and Downloads views
* in multi-select menu, replaced add/remove favorite with "toggle favorites" and mark read/unread with "Toggle played"
* added a bin for every queue for all removed episodes
* added Bin view in Queue and action bar item to switch to/from Bin view.
* in menu of Queue view added "Clean bin"  and removed "Switch queue"
* updated some dependencies

# 6.1.4

* fixed issue of "mark excluded episodes played" checkbox not being reflected from the setting
* in FeedSetting, fixed issue of auto-download options being enabled even if auto-download is not enabled
* "Put in queue" changed to "Add to queue..." and added checkbox for removing from other queues
* refined some handling on events
* fixed issue of deleted episodes not being correctly handled in some lists
* fixed issue of current media kept being played when removed or when removed from queue
* in FeedEpisodes view, fixed (mostly) issue of not being promptly filtered when an episode state changes
* when screen is turned back on during playback, PlayerUI is promptly updated

# 6.1.3

* added feed setting in the header of FeedInfo view
* in all episodes list views, click on an episode image brings up the FeedInfo view
* added countingPlayed for auto download in feed setting, when set to false, downloaded episodes that have been played are not counted as downloaded to the limit of auto-download
* fixed possible mal-function of feed sorting
* improved feed sorting efficiency
* improved feed update efficiency
* in Subscriptions view added sorting info on every feed (List Layout only)
* "Put to queue" text changed to "Put in queue"
* in dialogs "Put in queue" and "Switch queue" the spinner is changed to lisst of radio buttons
* likely fixed hang when switching queue sometimes

# 6.1.2

* fixed crash issue when setting the inclusive or exclusive filters in feed auto-download setting
* fixed player UI not updating on change of episode
* changed title of Queues view to a spinner for easily switching queues
* added "Put to queue" in multi-select menu putting selected episodes to a queue, this would also remove the episodes from any previous queues.
* added condition checks for preparing enqueuing sync actions
* in Subscriptions view added feeds filter based on feed preferences, in the same style as episodes filter

# 6.1.1

* fixed player UI not updating on change of episode
* fixed the mal-function of restoring previously backed-up OPML
* reduced reactions to PlaybackPositionEvent
* tuned AutoCleanup a bit
* tuned and fixed some some issues in auto-downloaded

# 6.1.0

* in FeedEpisode view fixed filtering after an episode's play state is changed
* fixed refreshing a feed causes duplicate episodes in FeedEpisodes view
* fixed the non-functioning of "Set navigation drawer items"
* fixed crash when changing filter during media playing
* item selection in list views only updates the selected item (rather than the whole list)
* fixed episode action button not updated when deleting the media in episode list views
* skipped concurrent calls for loading data in multiple views
* toggle "Auto backup of OPML" in Settings will restart Podcini
* automatically restoring backup of OPML upon new install is disabled. Instead, in AddFeed view, when subscription is empty and OPML backup is available, a dialog is shown to ask about restoring.
* added auto downloadable to episodes filter
* added download date to episodes sorting
* added download date to feed sorting
* auto download algorithm is changed to individual feed based.
	* When auto download is enabled in the Settings, feeds to be auto-downloaded need to be separately enabled in the feed settings.
	* Each feed also has its own download policy (only new episodes, newest episodes, and oldest episodes. newest episodes meaning most recent episodes new or old)
	* Each feed has its own limit (Episode cache) for number of episodes downloaded, this limit rules in combination of the overall limit  for the app.
	* After auto download run, episodes with New status is changed to Unplayed.
	* auto download feed setting dialog is also changed:
		* there are now separate dialogs for inclusive and exclusive filters where filter tokens can be specified independently
		* on exclusive dialog, there are optional check boxes "Exclude episodes shorter than" and "Mark excluded episodes played"
* set default value of "Include in auto downloads" in feed setting to false
* remove an episode from queue no longer triggers auto download
* got rid of many string delegates, in favor of enums
* some class restructuring

# 6.0.13

* removed from preferences "Choose data folder", it's not suitable for newer Androids
* some class restructuring, especially some functions moved out of Userpreferences
* fixed issue of early termination when exporting a large set of media files
* fixed the mal-functioning feeds and episodes search
* updated realm.kotlin to 2.1.0

# 5.5.5

* this is an extra minor release for better migration to Podcini 6
* fixed issue (in 5.5.4) of terminating pre-maturely when exporting a large set of media files
* this release is not updated to F-Droid due to Podcini 6 listing in progress

# 6.0.12

* re-enabled import of downloaded media files, which can be used to migrate from 5.5.4. Media files imported are restricted to existing feeds and episodes.
* importing media files is only allowed from a directory with name containing "Podcini-MediaFiles"
* if you have imported some media files exported from 5.5.4 using version 6.0.10, these files are junk. You can opt for clearing storage or re-installing version 6, or you can ignore them for now as I plan to provide media file management functionality in the near future.

# 6.0.11

* This is a minor release of subtraction: import of downloaded media files is temporarily disabled as it's more complicated than I thought. Imported file names from earlier versions aren't easily recognizable.

# 6.0.10

* for better migrating from version 5, added export/import of downloaded media files: inter-operable with Podcini 5.5.4
* importing media files is restricted to directory with name containing "Podcini-MediaFiles"
* importing preferences is restricted directory with name containing "Podcini-Prefs"

# 5.5.4

* this is an extra minor release for better migration to Podcini 6
* added export/import of downloaded media files which can then be imported in Podcini 6
* this release is not updated to F-Droid due to Podcini 6 listing in progress

# 6.0.9

This is the first release of Podcini.R version 6. If you have an older version installed, this release installs afresh in parallel with and is not compatible with older versions.  Main changes are:

* complete overhaul of database and routines, ditched the iron-age celebrity SQLite and entrusted the modern object-based Realm
* export/import DB is supported for the Realm DB with file extension .realm
* DB from Podcini version 5 and below can not be imported, if you need migration, see migrationTo6.md file on github for instructions
* components rely more on objects for communication, unnecessary DB access is reduced
* remove feeds is performed promptly in blocking way
* feeds sorting is more explicit and bi-directional and in the same style as episodes sorting
* feed order names changed in English (other languages need update)
* grid view is enabled for Subscriptions and can be switched on in Settings->User interface
* in Subscriptions view, click on cover image of a feed opens the FeedInfo view (not FeedEpisodes view)
* in Subscriptions and episodes list views, corrected the issue of wrong images of episodes being shown when scrolling
* receiving flow events are strictly tied to life cycles of the components
* re-worked some events related to feed changes
* queue is circular: if the final item in queue finished, the first item in queue (if exists) will get played
* NavDrawer no longer gets updated in the background but only upon open
* player control UI is more efficient
* PlaybackController is further enhanced for multiple access
* non-essential instantiations of PlaybackController are stripped
* AudioPlayer view is hidden when there is no media set to play
* playback routines are extensively tuned and cleaned, less layered
* in any episode list views, swipe with NO_ACTION defined pops up the swipe config dialog
* date of new episode is highlighted in episodes list views
* in episodes sort dialog, "Date" is changed to "Publish date"
* added more garbage collections
* in preference "Delete Removes From Queue" is set to true by default
* added in preference "Remove from queue when marked as played" and set it to true by default
* added episode counts in Episodes and History views
* enhanced play position updates in all episodes list views
* added "Remove from favorites" in speed-dial menu
* on PlayerDetailed and EpisodeHome views the home button on action bar has a toggle
* FeedInfo view has button showing number of episodes to open the FeedEpisodes view
* on action bar of FeedEpisodes view there is a direct access to Queue
* tidied up the header of FeedEpisodes view
* media size is shown on episode info view
* net-fetching of media size for not-downloaded media is removed for episode list views
* there is a setting to disable/enable auto backup OPML files to Google
* InTheatre object is now the center reference for all currently playing stuff including the current play queue
* 5 queues are provided by default: Default queue, and Queues 1-4
	* all queue operations are on the curQueue, which can be set in all episodes list views
	* on app startup, the most recently updated queue is set to curQueue
* progressive loading in some episodes list views are more efficient
* on importing preferences, PlayerWidgetPrefs is ignored
* position updates to widget is also set for every 5 seconds
* in FeedEpisodes view, refresh only performs on the single feed (rather than all feeds)
* VariableSpeed dialog is no longer depends on the controller
* Tapping the media playback notification opens Podcini
* Long-press filter button in FeedEpisode view enables/disables filters without changing filter settings
* in wifi sync and episode progress export/import, changed start position and added played duration for episodes (compatible with 5.5.3), this helps for the statistics view at the importer to correctly show imported progress without having to do "include marked played"
* the Counter and its seetings are removed
* all RxJava code was replaced with coroutines, RxJava dependency is kept only for using fyyd search
* PlaybackPreferences using SharePreferences was removed and related info is handled by the DB as CurrentState
* decade-old joanzapata iconify is replaced with mikepenz iconics
* removed the need for support libraries and the need for the jetifier
* Java tools checkstyle and spotbus are removed
* the clumsy FeedDrawerItem class was removed and related components are based purely on feed objects
* code is now built with Kotlin 2.0.0
* for more details, see the changelogs in pre-release versions

## 6.0.8

* feeds sorting dialog layout is set to single column due to potential long text issue
* fixed issue of not being able to copy text from player detailed view
* fixed issue of some menu icon not correctly shown in dark theme
* fixed issue of results not updated in views when deleting episodes
* fixed issue of downloaded episode still showing as New
* fixed sorting on All Episodes view
* added more garbage collections

## 6.0.7

* feeds sorting is bi-directional and in the same style as episodes sorting
* feed order names changed in English (other languages need update)
* date of new episode is highlighted in episodes list views
* fixed issue of tags not being properly handled

## 6.0.6

* minor class re-structuring
* adjusted FeedPreferences to incorporate some previously ignored properties
* enabled selection of .json files when importing progress
* in wifi sync and episode progress export/import, changed start position and added played duration for episodes (available from 5.5.3), this helps for the statistics view at the importer to correctly show imported progress without having to do "include marked played"

## 5.5.3

* this is an extra minor release for better migration to Podcini 6
* in wifi sync and episode progress export/import, changed start position and added played duration for episodes
* this helps for the statistics view at the importer to correctly show imported progress without having to do "include marked played"
* this release is not updated to F-Droid due to Podcini 6 release in progress

## 6.0.5

* fixed threading issue of downloading multiple episodes
* tidied up and fixed the mal-functioning statistics view
* tidied up routine of delete media
* fixed issue of episode not properly marked after complete listening
* fixed redundant double-pass processing in episodes filter
* in episodes sort dialog, "Date" is changed to "Publish date"
* in preference "Delete Removes From Queue" is set to true by default
* added in preference "Remove from queue when marked as played" and set it to true by default
* added episode counts in Episodes and History views
* enhanced a bit on progress import
* restricted file types for DB import to only a .realm file and Progress import to a .json file
* enhanced play position updates in all episodes list views
* remove feeds is performed in blocking way

## 6.0.4

* bug fix on ShareDialog having no argument
* tuned and fixed menu issues in EpisodeIndo and PlayerDetailed views
* corrected current order in FeedEpisode sort dialog
* fixed sorting in Subscriptions view
* fixed tags spinner update issue in Subscriptions view
* made various episodes list views to reflect change on status changes of episodes
* fixed DB write error when deleting a feed
* fixed illegal index error in AllEpisodes and History views when the list is empty
* synchronized feeds list update when adding or deleting multiple feeds
* added "Remove from favorites" in speed-dial menu

## 6.0.3

* minor class restructuring
* PlayerDetailed view updates properly when new episode starts playing
* on PlayerDetailed and EpisodeHome views the home button on action bar has a toggle
* progressive loading in some episodes list views are more efficient
* live monitoring feed changes in DB
* re-worked some events related to feed changes
* fixed issue of player skipping to next or fast-forwarding past the end
* fixed issue of not properly handling widgets (existing since some release of version 5)
* grid view is enabled for Subscriptions and can be switched on in Settings->User interface
* on importing preferences, PlayerWidgetPrefs is ignored
* position updates to widget is also set for every 5 seconds
* further class restructuring and code cleaning

## 6.0.2

* filtered query for episodes is more efficient
* fixed performance and crash issues in AllEpisodes view with large dataset
* fixed performance issue in Downloads view
* in FeedEpisodes view, refresh only performs on the single feed (rather than all feeds)
* VariableSpeed dialog is no longer depends on the controller

## 6.0.1

* removing a list of feeds is speedier
* fixed issue of not starting the next in queue
* queue is circular: if the final item in queue finished, the first item in queue (if exists) will get played
* fixed crash issue in AudioPlayer due to view uninitialized
* improved efficiency of getFeed
* Tapping the media playback notification opens Podcini
* Long-press filter button in FeedEpisode view enables/disables filters without changing filter settings

## 6.0.0

* complete overhaul of database and routines, ditched the iron-age celebrity SQLite and entrusted the modern object-based Realm
* export/import DB is supported for the Realm DB with file extension .realm
* DB from Podcini version 5 and below can not be imported, see migrationTo6.md file on github for instructions
* deleting feeds is performed promptly
* components rely more on objects for communication, unnecessary DB access is reduced
* subscriptions sorting is more explicit
* in Subscriptions view, click on cover image of a feed opens the FeedInfo view (not FeedEpisodes view)
* in Subscriptions and episodes list views, corrected the issue of wrong images of episodes being shown when scrolling
* the Counter and its seetings are removed
* flow event additions and improvements
* receiving flow events are strictly tied to life cycles of the components
* NavDrawer no longer gets updated in the background but only upon open
* player control UI is more efficient
* PlaybackController is further enhanced for multiple access
* non-essential instantiations of PlaybackController are stripped
* AudioPlayer view is hidden when there is no media set to play
* playback routines are extensively tuned and cleaned, less layered
* in any episode list views, swipe with NO_ACTION defined pops up the swipe config dialog
* episodes marked played will be removed from all queues
* in EpisodeInfo view, "mark played/unplayed", "add to/remove from queue", and "favoraite/unfovorite" are at the action bar
* decade-old joanzapata iconify is replaced with mikepenz iconics
* removed the need for support libraries and the need for the jetifier
* Java tools checkstyle and spotbus are removed
* the clumsy FeedDrawerItem class was removed and related compponents are based purely on feed objects
* FeedInfo view has button showing number of episodes to open the FeedEpisodes view
* on action bar of FeedEpisodes view there is a direct access to Queue
* tidied up the header of FeedEpisodes view
* media size is shown on episode info view
* net-fetching of media size for not-downloaded media is removed for episode list views
* there is a setting to disable/enable auto backup OPML files to Google
* all RxJava code was replaced with coroutines, RxJava dependency is kept only for using fyyd search
* PlaybackPreferences using SharePreferences was removed and related info is handled by the DB as CurrentState
* InTheatre object is now the center reference for all currently playing stuff including the current play queue
* 5 queues are provided by default: Default queue, and Queues 1-4
	* all queue operations are on the curQueue, which can be set in all episodes list views
	* on app startup, the most recently updated queue is set to curQueue
* extensive adjustments project class structures
* code is now built with Kotlin 2.0.0

## 5.5.2

* another minor release for better migration to Podcini 6
* in wifi sync and episode progress export/import, included favorites info for episodes

## 5.5.1

* a minor release for better migration to Podcini 6
* in wifi sync and episode progress export/import, included the info for episodes not played or not finished playing but marked as played

## 5.5.0

* likely fixed Nextcloud Gpoddersync fails
* fixed text not accepted issue in "add podcast using rss feed"
* removed kotlin-stdlib dependency to improve build speed
* cleaned out the commented-out RxJava code
* added export/import of episode progress for migration to future versions. the exported content is the same as with instant sync: all the play progress of episodes (completed or not)
* this is likely the last release of Podcini 5, sauf perhaps any minor bugfixes.
* the next Podcini overhauls the entire DB routines, SQLite is replaced with the object-based Realm, and is not compatible with version 5 and below.

## 5.4.2

* likely fixed crash issue when the app is restarted after long idle
* fixed null pointer crash issue in when trying to report player error
* fixed null pointer crash issue when open player detailed view with online episode
* likely fixed the audio break issue when streaming some podcasts, particularly those related to "iHeart" (actually the server has some invalid settings, someone should notify them).
this should take effect with episodes in both subscribed or online feeds

## 5.4.1

* fixed occasional crash of detecting existing OPML file for new install
* should have fixed the mal-functioning earphone buttons

## 5.4.0

* replaced thread with coroutines in DBWrite
* reduced startup lags of the app, various views, and media playing
* tidied up preferences accesses
* updated SwipeActions to the new DefaultLifecycleObserver
* added more options for default page
* added link info in episode info view

## 5.3.1

* fixed crash issue on some device when attempting to remove item from queue multiple times
* improved efficiency of getNextInQueue in DBReader

## 5.3.0

* change normal scope to life cycle scope when possible
* replaced EventBus with Kotlin SharedFlow, be mindful of possible issues
* added sort order based on episode played or completed times, accessible from various episodes' list views
* in history view, dates shown on items are last-played dates
* in history view added sort and date filter
* more conversion of RxJava routines to Kotlin coroutines
* fixed crash issue sometimes when importing OPOL files
* fixed crash issue in About view

## 5.2.1

* fixed issue of play/pause button not correctly updated in Queue

## 5.2.0

* suppressed log.d messages in release app (unbelievable incomplete suppression last time)
* made loadAdditionalFeedItemListData of DBReader a bit more efficient
* reduced unnecessary loading of media info in AudioPlayer
* tuned playback controller for efficiency
* improved player details view and corrected issue of showing wrong info
* tuned episode info view for efficiency
* tuned feed info view for start efficiency
* tuned and slimmed down the drawer: number of Subscriptions now shows the number of subscriptions
* replaced many RxJava stuff with Kotlin Coroutines

## 5.1.0

* properly destroys WebView objects
* fixed issue loading image lacking a header
* preferences now can be exported/imported

## 5.0.1

* fixed crash when opening Import/Export in settings

## 5.0.0

* experimental (back up before use) release of instant sync between devices on the same wifi network without a server
* have media3 exoplayer taking over all audio focus controls to better cooperate with other media players. be mindful of any possible issues
* fixed some icon handling issues noticed on Android 14
* overhauled image handling routines, replaced Glide with Coil: efficiency improved, app lighter, and lots of exception issues eliminated, though appearance not fully replicated
* suppressed log.d messages in release app
* fixed search failure bug introduced with adding of transcript column
* fixed a null pointer exception when opening online feed providing null connection type
* fixed bug of episodes being mostly duplicated in online feed episodes view
* fixed play/pause button not correctly set sometimes
* fixed position not correctly shown sometimes
* fixed position not correctly set on starting streaming
* fixed issue with long-press on the Skip button
* player status has now a single point of reference
* player detailed view is only initialized at first open
* instant (or wifi) sync can be accessed from Settings->Synchronization, where an instruction is shown
* it syncs the play states (position and played) of episodes that exist in both devices (ensure to refresh first) and that have been played (completed or not) on at least one device
* so far, every sync is a full sync, no subscription feeds sync, and no media files sync

## 4.10.1

* fixed crash issue when TTS engine is not available on device
* in feed item list view, only start TTS engine when some episodes have no media

## 4.10.0

* fixed media info on notification widget
* added in DB column "transcript" in feed items table to store episode home reader text fetched from the website
* added TTS button for fetching transcript (if not exist) and then generating audio file from the transcript
* in episode list view, if episode has no media, TTS button is shown if audio file is not yet generated, otherwise Play/Pause button is shown
* TTS audio files are playable in the same way as local media (with speed setting, pause and rewind/forward)
* when a no-media episode is deleted, the audio file is deleted
* episode home view opens reader mode first
* button1 in episode info view is set to invisible if no media
* fixed feed sorting "unread publication date"

## 4.9.6

* fixed the nasty bug of marking multiple items played when one is finished playing
* merged PlayerWrapper class into LocalMediaPlayer

## 4.9.5

* added action bar option in episode home view to switch on/off JavaScript
* added share notes menu item in reader mode of episode home view
* TTS speed uses playback speed of the feed or 1.0
* on player detailed view, if showing episode home reader content, then "share notes" shares the reader content
* fixed bug of not re-playing a finished episode
* fixed (possibly) bug of marking multiple items played when one is finished playing

## 4.9.4

* fixed non-functioning pause button in queue list
* fixed player control buttons not being properly activated
* enabled the function for auto downloading of feeds
	* when global auto download setting is enabled, no existing feed is automatically included for auto download
	* when subscribing a new feed, there an option for auto download
	* new episode of a feed is auto downloaded at a feed refresh only when both global and feed settings for auto download are enabled
	
## 4.9.3

* show toast message when episode home views are not available
* fixed crash bug on app startup when player buttons are clicked before play
* large codebase cleaning
* episode home menu item is disable on video player

## 4.9.2

* fixed the action buttons on notification widget. bit strange with the order though as they appear different on my Android 9 and Android 14 devices
* media3 requires quite some logic change, so be mindful with any issues

## 4.9.1

* reader mode of episode home view observes the theme of the app
* reader mode content of episode home view is cached so that subsequent loading is quicker
* episode home reader content can be switched on in player detailed view from the action bar

## 4.9.0

* fixed bug of player always expands when changing audio
* migrated to media3's MediaSession and MediaLibraryService though no new features added with this. some behavior might change or issues might arise, need to be mindful
* when video mode is temporarily audio only, click on image on audio player on a video episode also brings up the normal player detailed view
* added a menu action item in player detailed view to turn to fullscreen video for video episode
* added episode home view accessible right from episode info view. episode home view has two display modes: webpage or reader. 
* added text-to-speech function in the reader mode. there is a play/pause button on the top action bar, when play is pressed, text-to-speech will be used to play the text.  play features now are controlled by system setting of the TTS engine. Advanced operations in Podcini are expected to come later.
* RSS feeds with no playable media can be subscribed and read/listened via the above two ways

## 4.8.0

* fixed empty player detailed view on first start
* player detailed view scrolls to the top on a new episode
* created video episode view, with video player on top and episode descriptions in portrait mode
* added video player mode setting in preferences, set to small window by default
* added on video controller easy switches to other video mode or audio only
* when video mode is set to audio only, click on image on audio player on a video episode brings up the normal player detailed view
* webkit updated to Androidx
* fixed bug in setting speed to wrong categories
* improved fetching of episode images when invalid addresses are given

## 4.7.1

* enabled speed setting for podcast in video player
* fixed bug of receiving null view in function requiring non-null in subscriptions page
* set Counter default to number of SHOW_UNPLAYED
* removed FeedCounter.SHOW_NEW, NewEpisodesNotification, and associated notifications settings

## 4.7.0

* large code and directory refactoring
* removed all checks of SDK_INT vs Build.VERSION_CODES.M or 23: devices with Android 5.1 or lower are not supported, sorry.
* made streamed media somewhat equivalent to downloaded media
	* enabled episode description on player detailed view
	* enabled intro- and end- skipping
	* mark as played when finished
	* streamed media is added to queue and is resumed after restart
* changes in text of share feed link
* disabled some undesired deep links

## 4.6.2

* min SDK version bumped to 23 for Android Auto support: : devices with Android 5.1 or lower are not supported, sorry.
* it should now work on Android Auto

## 4.6.1

* fixed bug on intro- and end- skipping
* new notice on need of notifications for Android 13 and newer (in selected languages only )

## 4.6.0

* added more info in online feed view
* added ability to open podcast from webpage address, reduced the error of "The podcast host\'s server sent a website, not a podcast"
* allows importing podcast from a web address, either through copy/paste or share.
* Youtube channels are accepted from external share or paste of address in podcast search view, and can be subscribed as a normal podcast. A channel is handled as a podcast feed, and videos in the channel are as episodes. Drawbacks now are playing of the video is not handled inside Podcini, but in Youtube app or the browser, and the play speed is not controlled by Podcini.

## 4.5.4

* fixed crash bug when setting fallback or fast-forward speeds with some Locales
* further enlarged height of the bottom player control to improve on missing pixels at the bottom
* on speed setting dialog, only tap on a preset chip sets the speed, only selected options will be set
* corrected the handling of current audio speed:
	* when speed for current audio is not set, podcast speed takes precedence
	* when speed for current audio is set, it takes precedence for the current audio (episode), even with pause/re-play

## 4.5.3

* corrected wrong caption of "Edit fallback speed"
* adjusted layout and button dimension and alignments in the bottom player control
* fallback speed setting is now capped at 0.0 and 3.0 and allows for 2-digit precision
* corrected episode count display in subscriptions list when the feed has 0 episodes

## 4.5.2

* revamped audio player class, merged external player in
* speed setting now allows setting with three options: current audio, podcast, and global.
* added a bit bottom margin for the numbers in player

## 4.5.1

* fixed bug in subscription sorting

## 4.5.0

* fixed bug of sort order not stable in feed item list
* in Subscriptions view added sorting of "Unread publication date"
* added preference "Fast Forward Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a float number (capped between 0.0 and 10.0)
* added preference "Fallback Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a float number (capped between 0.0 and 1.5)
* added new ways to manipulate play speed
* the "Skip to next episode" button
	* long-press moves to the next episode
	* by default, single tap does nothing
	* if the user customize "Fast Forward Speed" to a value greater than 0.1, it behaves in the following way:
		* single tap during play, the set speed is used to play the current audio
		* single tap again, the original play speed resumes
		* single tap not during play has no effect
* the Play button
	* by default, it behaves the same as usual
	* if the user customize "Fallback speed" to a value greater than 0.1, long-press the button during play enters the fallback mode and plays at the set fallback speed, single tap exits the fallback mode
	
## 4.4.3

* created online feed view fragment
* online episodes list view is no longer restricted to 50 episodes
* online episodes list view now better handles icons
* online episodes list view goes back to the online feed view
* the original online feed view activity is stripped and now only preserved for receiving shared feed
* externally shared feed opens in the online feed view fragment

## 4.4.2

* converted ksp back to kapt
* unrestricted the titles to 2 lines in player details view
* fixed once again the bug of player disappear on first play
* some code refactoring

## 4.4.1

* fixed bug of app crash on stream episode customization
* disabled usesCleartextTraffic, connection to http sites appear OK, report if you find an issue
* enforced non-null load location for most Glide calls
* avoided redundant media loadings and ui updates when a new audio starts
* eliminated frequent list search during audio play, a serious energy waste
* icons in online episode list, when unavailable, are set to app logo 

##  4.4.0

* added direct search of feeds related to author in feed info view
* added a new episodes list fragment for arbitrary list
* revamped online feed view activity
* episodes (50 most recent) of any online feed can be viewed and played (streamed) directly without subscribing to the feed
* bug fixes on passing Glide with null addresses
* null safety enhancements in code

## 4.3.4

* fixed bug player disappear on first play
* more viewbinding GC enhancements
* added sort by feed title in downloads view
* more items on action bar in feed item list view
* some cleaning of redundant qualifiers
* sort dialog no longer dims the main view
* added random sort to feed items view

## 4.3.3

* fixed bug in adding widget to home screen
* minor adjustment of widget layout
* added "audio only" to action bar in video player
* added "mark favorite" to action bar in episode view
* revamped and enhanced expanded view of the player
* vertical swipe no longer collapses the expanded view
* only the down arrow on top left page collapses the expanded view
* share notes directly from expanded view of the player
* in episode info, changed rendering of description, removed nested scroll

## 4.3.2

* further optimized efficiencies of episode info view
* episode info view opened from icon is now the same as that opened from title area, no long supports horizontal swipes (change from 4.2.7)
* enhanced viewbingding GC
* some code cleaning

## 4.3.1

* titles of played episodes are made brighter
* icons of played episodes are marked with a check
* icons of swipe telltales are clickable for setting
* Straightened up play speed setting
	* three places to set the play speed:
	* global setting at the preference
	* setting for a feed: either use global or customize
	* setting at the player: set for current playing and save for global
	* feed setting takes precedence when playing an episode

## 4.3.0

* added more info about feeds in the online search view
* fixed bug of preview not playing
* disabled feed filters setting in preference
* "open feed" is an action item on audio player top bar
* added swipe action telltales in all episode lists
* added NO_ACTION swipe action
* all default swipe actions are set to NO_ACTION
* cleaned up swipe preferences ui: statistics removed

## 4.2.7

* disabled drag actions when in multi-select mode (fixed crash bug)
* renewed PodcastIndex API keys
* added share notes menu option in episode view
* https://github.com/XilinJia/Podcini/issues/20
	* press on title area of an episode now opens the episode info faster and more economically - without horizontal swipe
	* press on the icon of an episode opens the episode info the original way - with horizontal swipe

## 4.2.6

* corrected action icons for themes
* revealed info bar in Downloads view
* revealed info bar in Subscriptions view
* reset tags list in Subscriptions when new tag is added

## 4.2.5

* change in click operation
	* click on title area opens the podcast/episode
	* long-press on title area automatically enters in selection mode
	* select all above or below are put to action bar together with select all
	* operations are only on the selected (single or multiple)
	* popup menus for single item operation are disabled
* in podcast view, the title bar no longer scrolls off screen
	
## 4.2.4

* fixed the "getValue() can not be null" bug
* enabled ksp for Kotlin builds
* cleaned up build.gradle files

## 4.2.3

* fixed bug [Inbox still set as default first tab](https://github.com/XilinJia/Podcini/issues/10)
* cleaned up Inbox related resources
* removed info button in FeedItemList header
* added items count in FeedItemList header
* fixed bug in FeedItemList when filtered list has no items
* buildConfig is set in build.gradle instead of gradle.properties

## 4.2.2

* bug fix on auto-download mistakenly set in 4.2.1
* Sorry for another change in click operation 
 	* long-press on an icon would be the same as a click
	* click on title area allows operation on the single item
	* long-press on title area would allow for multi-select

## 4.2.1

* Statistics moved to the drawer
* tuned down color of player controller
* Subscriptions menu adjustment
* Subscriptions filter is disabled for now
* more null safety tuning
* fixed the refresh bug related to permissions
* long-press operation has changed
	* long-press on title area would be the same as a click
	* click on an icon allows operation on the single item
	* long-press on an icon would allow for multi-select
	
## 4.2.0

* Removed InBox feature
* improvement on player UI
* episode description now on first page of player popup page
* localization updates

## 4.1.0

* New convenient player control
* tags enhancements
* bug fixes
* View binding enabled for mode views in code

## 4.0.1

* project restructured as a single module
* new Subscriptions screen
* default page to Subscriptions
* Home removed

# 3.2.5

* fixed feed refresh and info bugs

## 3.2.4

* minor efficiency improvements

## 3.2.3

* First Podcini app
* removed unnecessary network access when screen is on to save more battery
* fixed issue with changing playback speed on S21 Android 14




