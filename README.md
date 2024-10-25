# Podcini

<img width="100" src="https://raw.githubusercontent.com/xilinjia/podcini/main/images/icon 256x256.png" align="left" style="margin-right:15px"/>

An open source podcast instrument, attuned to Puccini ![Puccini](./images/Puccini.jpg), adorned with pasticcini ![pasticcini](./images/pasticcini.jpg) and aromatized with porcini ![porcini](./images/porcini.jpg), invites your harmonious heartbeats.

### Rendezvous chez:

[<img src="./images/external/getItGithub.png" alt="GitHub" height="50">](https://github.com/XilinJia/Podcini/releases/latest)
[<img src="./images/external/getItIzzyOnDroid.png" alt="IzzyOnDroid" height="50">](https://apt.izzysoft.de/fdroid/index/apk/ac.mdiq.podcini.R)
[<img src="./images/external/getItf-droid.png" alt="F-Droid" height="50">](https://f-droid.org/packages/ac.mdiq.podcini.R/)
[<img src="./images/external/amazon.png" alt="Amazon" height="40">](https://www.amazon.com/%E8%B4%BE%E8%A5%BF%E6%9E%97-Podcini-R/dp/B0D9WR8P13)

#### Podcini.R 6.10 allows creating synthetic podcast and shelving any episdes to any synthetic podcasts
#### Podcini.R version 6.5 as a major step forward brings YouTube contents in the app.  Channels can be searched, received from share, subscribed. Podcasts, playlists as well as single media from Youtube and YT Music can be shared to Podcini. For more see the Youtube section below or the changelogs
That means finally: [Nessun dorma](https://www.youtube.com/watch?v=cWc7vYjgnTs)
#### For Podcini to show up on car's HUD with Android Auto, please read AnroidAuto.md for instructions.
#### If you need to cast to an external speaker, you should install the "play" apk, not the "free" apk, that's about the difference between the two.
#### Since version 6.11.5, Podcini.R is back to be built to target SDK 35 (Android 15), but requests for permission for unrestricted background activities for uninterrupted background play of a playlist.  For more see [this issue](https://github.com/XilinJia/Podcini/issues/88)
#### If you are migrating from Podcini version 5, please read the migrationTo5.md file for migration instructions.

This project was developed from a fork of [AntennaPod](<https://github.com/AntennaPod/AntennaPod>) as of Feb 5 2024.

Compared to AntennaPod this project:

1. Migrated all media routines to `androidx.media3`, with `AudioOffloadMode` enabled, nicer to device battery,
2. Is purely `Kotlin` based and mono-modular, and multiple views are in Jetpack Compose,
3. modern object-base Realm DB replaced SQLite, Coil replaced Glide, coroutines replaced RxJava and threads, and SharedFlow replaced EventBus.
4. Boasts new UI's including streamlined drawer, subscriptions view and player controller,
5. Supports multiple, virtual and circular play queues associable to any podcast
6. Auto-download is governed by policy and limit settings of individual feed
7. Features synthetic podcasts and allows episodes to be shelved to any synthetic podcast
8. Supports channels, playlists, single media from YouTube and YT Music, as well as normal podcasts and plain RSS,
9. Allows adding personal notes and 5-level rating on every episode
10. Offers Readability and Text-to-Speech for RSS contents,s
11. Features `instant sync` across devices without a server.

The project aims to profit from modern frameworks, improve efficiency and provide more useful and user-friendly features.

While podcast subscriptions' OPML files (from AntennaPod or any other sources) can be easily imported, Podcini can not import DB from AntennaPod.

## Notable new features & enhancements

### Player and Queues

* More convenient player control displayed on all pages
* Revamped and more efficient expanded player view showing episode description on the front
* Playback speed setting has been straightened up, three speed can be set separately or combined: current audio, podcast, and global
* Added preference "Fast Forward Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a number between 0.0 and 10.0
* The "Skip to next episode" button on the player
  * long-press moves to the next episode
  * by default, single tap does nothing
  * if the user customize "Fast Forward Speed" to a value greater than 0.1, it behaves in the following way:
    * single tap during play, the set speed is used to play the current audio
    * single tap again, the original play speed resumes
    * single tap not during play has no effect
* Added preference "Fallback Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a float number (capped between 0.0 and 1.5)
* if the user customizes "Fallback speed" to a value greater than 0.1, long-press the Play button during play enters the fallback mode and plays at the set fallback speed, single tap exits the fallback mode
* streamed media somewhat equivalent to downloaded media
  * enabled episode description on player detailed view
  * enabled intro- and end- skipping
  * mark as played when finished
  * streamed media is added to queue and is resumed after restart
* new video episode view, with video player on top and episode descriptions in portrait mode
* easy switches on video player to other video mode or audio only, in seamless way
* video player automatically switch to audio when app invisible
* when video mode is set to audio only, click on image on audio player on a video episode brings up the normal player detailed view
* "Prefer streaming over download" is now on setting of individual feed
* added setting in individual feed to play audio only for video feeds,
  * an added benefit for setting it enables Youtube media to only stream audio content, saving bandwidth.
  * this differs from switching to "Audio only" on each episode, in which case, video is also streamed
* Multiple queues can be used: 5 queues are provided by default, user can rename or add up to 10 queues
  * on app startup, the most recently updated queue is set to curQueue
  * any episodes can be easily added/moved to the active or any designated queues
  * any queue can be associated with any feed for customized playing experience
* Every queue is circular: if the final item in queue finished, the first item in queue (if exists) will get played
* Every queue has a bin containing past episodes removed from the queue, useful for further review and handling
* Feed associated queue can be set to None, in which case:
  * episodes in the feed are not automatically added to any queue, but are used as a natural queue for getting the next episode to play
  * the next episode is determined in such a way:
    * if the currently playing episode had been (manually) added to the active queue, then it's the next in queue
    * else if "prefer streaming" is set, it's the next unplayed episode in the feed episodes list based on the current sort order
    * else it's the next downloaded unplayed episode
* Otherwise, episode played from a list other than the queue is now a one-off play, unless the episode is on the active queue, in which case, the next episode in the queue will be played


### Podcast list and Episode list

* Subscriptions page by default has a list layout and can be opted for a grid layout
* New and efficient ways of click and long-click operations on lists:
  * click on title area opens the podcast/episode
  * long-press on title area automatically enters in selection mode
  * options to select all above or below are shown action bar together with Select All
  * operations are only on the selected (single or multiple)
* List info is shown in Queue and Downloads views
* Local search for feeds or episodes can be separately specified on title, author(feed only), description(including transcript in episodes), and comment (My opinion)
* Left and right swipe actions on lists now have telltales and can be configured on the spot
* Played or new episodes have clearer markings
* Sort dialog no longer dims the main view
* An all new way of filtering for both podcasts and episodes with expanded criteria
* Subscriptions sorting is now bi-directional based on various explicit measures, and sorting info is shown on every feed (List Layout only)
* in Subscriptions view, click on cover image of a feed opens the FeedInfo view (not FeedEpisodes view)
* in all episodes list views, click on an episode image brings up the FeedInfo view
* in episode list view, if episode has no media, TTS button is shown for fetching transcript (if not exist) and then generating audio file from the transcript. TTS audio files are playable in the same way as local media (with speed setting, pause and rewind/forward)
* on action bar of FeedEpisodes view there is a direct access to Queue
* Long-press filter button in FeedEpisodes view enables/disables filters without changing filter settings
* Long-press on the action button on the right of any episode in the list brings up more options
* History view shows time of last play, and allows filters and sorts

### Podcast/Episode

* New share notes menu option on various episode views
* Every feed (podcast) can be associated with a queue allowing downloaded media to be added to the queue
* FeedInfo view offers a link for direct search of feeds related to author
* FeedInfo view has button showing number of episodes to open the FeedEpisodes view
* instead of isFavorite, there is a new rating system for every episode: Trash, Bad, Neutral, Good, Favorite
* instead of Played or Unplayed, there is a new play state system Unspecified, Building, New, Unplayed, Later, Soon, InQueue, InProgress, Skipped, Played, Ignored
  	* among which Unplayed, Later, Soon, Skipped, Played, Ignored are settable by the user
	* when an episode is started to play, its state is set to InProgress
	* when episode is added to a queue, its state is set to InQueue, when it's removed from a queue, the state (if lower than Skipped) is set to Skipped
* in EpisodeInfo view, one can enter personal comments/notes under "My opinion" for the episode
* in FeedInfo view, one can enter personal comments/notes under "My opinion" for the feed
* New episode home view with two display modes: webpage or reader
* In episode, in addition to "description" there is a new "transcript" field to save text (if any) fetched from the episode's website
* RSS feeds with no playable media can be subscribed and read/listened (via TTS)
* deleting feeds is performed promptly

### Online feed

* Long-press on a feed in online feed list prompts to subscribe it straight out.
* More info about feeds are shown in the online search view
* Ability to open podcast with webpage address
* Online feed info display is handled in similar ways as any local feed, and offers options to subscribe or view episodes
* Online feed episodes can be freely played (streamed) without a subscription
* Online feed episodes can be selectively reserved into synthetic podcasts

### Youtube & Youtube Music

* Youtube channels can be searched in podcast search view, can also be shared from other apps (such as Youtube) to Podcini
* Youtube channels can be subscribed as normal podcasts
* Playlists and podcasts on Youtube or Youtube Music can be shared to Podcini, and then can be subscribed in similar fashion as the channels
* Single media from Youtube or Youtube Music can also be shared from other apps, can be accepted as including video or audio only, are added to synthetic podcasts such as "Youtube Syndicate"
* All the media from Youtube or Youtube Music can be played (only streamed) with video in fullscreen and in window modes or in audio only mode in the background
* These media are played with the lowest video quality and highest audio quality
* If a subscription is set for "audio only", then only audio stream is fetched at play time for every media in the subscription
* accepted host names include: youtube.com, www.youtube.com, m.youtube.com, music.youtube.com, and youtu.be

### Instant (or Wifi) sync

* Ability to sync between devices on the same wifi network without a server (experimental feature)
* It syncs the play states (position and played) of episodes that exist in both devices (ensure to refresh first) and that have been played (completed or not)
* So far, every sync is a full sync, no sync for subscriptions and media files

### Automation

* auto download algorithm is changed to individual feed based.
  * When auto download is enabled in the Settings, feeds to be auto-downloaded need to be separately enabled in the feed settings.
  * Each feed also has its own download policy (only new episodes, newest episodes, and oldest episodes. "newest episodes" meaning most recent episodes, new or old)
  * Each feed has its own limit (Episode cache) for number of episodes downloaded, this limit rules in combination of the overall limit  for the app.
  * Auto downloads run feeds or feed refreshes, scheduled or manual
  * auto download always includes any undownloaded episodes (regardless of feeds) added in the Default queue
  * After auto download run, episodes with New status is changed to Unplayed.
  * auto download feed setting dialog is also changed:
    * there are now separate dialogs for inclusive and exclusive filters where filter tokens can be specified independently
    * on exclusive dialog, there are optional check boxes "Exclude episodes shorter than" and "Mark excluded episodes played"
* Sleep timer has a new option of "To the end of episode"

### Security and reliability

* Disabled `usesCleartextTraffic`, so that all content transmission is more private and secure
* Settings/Preferences can now be exported and imported
* Play history/progress can be separately exported/imported as Json files
* there is logging for every content shared to Podcini, which can be reviewed and repaired if error
* Downloaded media files can be exported/imported
* Reconsile feature (accessed from Downloads view) is added to ensure downloaded media files are in sync with specs in DB
* Podcasts can be selectively exported from Subscriptions view
* There is a setting to disable/enable auto backup of OPML files to Google
* Upon re-install of Podcini, the OPML file previously backed up to Google is not imported automatically but based on user confirmation.

For more details of the changes, see the [Changelog](changelog.md)

## Screenshots

### Settings
<img src="./images/1_drawer.jpg" width="238" /> <img src="./images/2_setting.jpg" width="238" /> <img src="./images/2_setting1.jpg" width="238" />

### Import/Export
<img src="./images/2_setting2.jpg" width="238" /> <img src="./images/2_setting3.jpg" width="238" />

### Subscriptions
<img src="./images/3_subscriptions.jpg" width="238" /> <img src="./images/3_subscriptions1.jpg" width="238" /> <img src="./images/3_subscriptions2.jpg" width="238" /> 

### Multiple Queues
<img src="./images/4_queue.jpg" width="238" /> <img src="./images/4_queue1.jpg" width="238" />  

### Podcast
<img src="./images/5_podcast_0.jpg" width="238" /> <img src="./images/5_podcast_1.jpg" width="238" /> 

### Podcast settings
<img src="./images/5_podcast_setting.jpg" width="238" /> <img src="./images/5_podcast_setting1.jpg" width="238" /> 

### Episode and player details
<img src="./images/6_episode.jpg" width="238" /> <img src="./images/6_player_details.jpg" width="238" /> 

### Usage customization
<img src="./images/7_speed.jpg" width="238" /> <img src="./images/8_swipe_setting.jpg" width="238" /> <img src="./images/8_swipe_setting1.jpg" width="238" />

### Get feeds online
<img src="./images/9_feed_search.jpg" width="238" /> <img src="./images/9_online_feed_info.jpg" width="238" /> <img src="./images/91_online_episodes.jpg" width="238" />

### Android Auto
<img src="./images/92_Auto_list.png" width="238" /> <img src="./images/92_Auto_player.png" width="238" />

## Links

* [Changelog](changelog.md)
* [Privacy Policy](PrivacyPolicy.md)
* [Contributing](CONTRIBUTING.md)
* [Translation (Transifex)](https://app.transifex.com/xilinjia/podcini/dashboard/)

## License

Podcini, same as the project it was forked from, is licensed under the GNU General Public License (GPL-3.0).
You can find the license text in the LICENSE file.

## Copyright

New files and contents in the project are copyrighted in 2024 by Xilin Jia and related contributors.

Original contents from the forked project maintain copyrights of the AntennaPod team.

## Licenses and permissions

[Licenses and permissions](Licenses_and_permissions.md)
