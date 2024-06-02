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




