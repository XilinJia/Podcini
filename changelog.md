# 6.1.6

* enabled swipe actions in Queue bin view (same actions as in Queue)
* both icons of show bin and back to queue are changed to be more intuitive
* bin items are sorted based on the update time
* added a reconsile feature in Downloads view that verifies episodes' download status with media files in system and performs cleanup
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




