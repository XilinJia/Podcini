# Podcini

<img width="100" src="https://raw.githubusercontent.com/xilinjia/podcini/main/images/icon 256x256.png" align="left" style="margin-right:15px"/>
Podcini is an open source podcast manager/player project.

This project is a fork of [AntennaPod](<https://github.com/AntennaPod/AntennaPod>) as of Feb 5 2024.

Compared to AntennaPod this project:
1. Migrated the media player to `androidx.media3`,
2. Added `AudioOffloadMode` support, which is supposed to be kind to device battery,
3. Relies on the most recent dependencies,
4. Is __purely__ Kotlin based,
4. Targets Android 14,
5. Aims to improve efficiency and provide more user-friendly features

## Version 4

Some drastic changes are made in the project since version 4.0. There is now a whole new interface of the Subscriptions page showing only the feeds with tags as filters, no longer having tags as folders in the page. And the default page of the app is changed to the Subscriptions page.  Feed list are no longer shown in the drawer.  Access to statistics is in the drawer.  Alongside, the Home, Echo and Inbox pages are removed from the project. OnlineFeedView activity is stripped down to only receive externally shared feeds. Also, viewbindings are enabled for most views, the project becomes mono-modular, containing only the app module.

Even so, the database remains backward compatible, and AntennaPod's db can be easily imported.

Other notable features and changes include:

* A more convenient player control displayed on all pages
* A revamped and more efficient expanded player view showing episode description on the front
* External player class is merged into the player
* New and efficient ways of click and long-click operations on lists:
	* click on title area opens the podcast/episode
	* long-press on title area automatically enters in selection mode
	* options to select all above or below are shown action bar together with Select All
	* operations are only on the selected (single or multiple)
* A new share notes menu option on various episode views
* List info is shown in Queue and Downloads views
* Left and right swipe actions on lists now have telltales and can be configured on the spot
* Played episodes have clearer markings
* Sort dialog no longer dims the main view
* Play speed setting has been straightened up, three speed can be set separately or combined: current audio, podcast, and global.  The order of precedence is in that same order.
* Added preference "Fast Forward Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a float number (capped between 0.0 and 10.0)
* The "Skip to next episode" button on the player
	* long-press moves to the next episode
	* by default, single tap does nothing
	* if the user customize "Fast Forward Speed" to a value greater than 0.1, it behaves in the following way:
		* single tap during play, the set speed is used to play the current audio
		* single tap again, the original play speed resumes
		* single tap not during play has no effect
* Added preference "Fallback Speed" under "Playback" in settings with default value of 0.0, dialog allows setting a float number (capped between 0.0 and 1.5)
* the Play button on the player
	* by default, it behaves the same as usual
	* if the user customize "Fallback speed" to a value greater than 0.1, long-press the button during play enters the fallback mode and plays at the set fallback speed, single tap exits the fallback mode
* Subscriptions view has sorting by "Unread publication date"
* Feed info view offers a link for direct search of feeds related to author 
* More info about feeds are shown in the online search view
* Ability to open podcast from webpage address
* Online feed info display is handled in similar ways as any local feed, and offers options to subscribe or view episodes
* Online feed episodes can be freely played (streamed) without a subscription
* Youtube channels are accepted from external share or paste of address in podcast search view, and can be subscribed as a normal podcast, though video play is handled externally
* usesCleartextTraffic (for non-secure content transmission) is now disabled in the project
* Various efficiency improvements, including removal of
	* redundant media loadings and ui updates
	* frequent list search during audio play
* externally shared feed opens in the new online feed view fragment

For more details of the changes, see the [Changelog](changelog.md)

## Screenshots

<img src="./images/1_drawer.jpg" width="238" /> <img src="./images/2_setting.jpg" width="238" /> <img src="./images/2_setting1.jpg" width="238" />

<img src="./images/3_subscriptions.jpg" width="238" /> <img src="./images/4_queue.jpg" width="238" />  

<img src="./images/5_podcast_0.jpg" width="238" /> <img src="./images/5_podcast_1.jpg" width="238" /> <img src="./images/5_podcast_setting.jpg" width="238" /> 

<img src="./images/6_episode.jpg" width="238" /> <img src="./images/7_speed.jpg" width="238" /> 

<img src="./images/8_swipe_setting.jpg" width="238" /> <img src="./images/8_swipe_setting1.jpg" width="238" /> 

<img src="./images/9_feed_search.jpg" width="238" /> <img src="./images/9_online_feed_info.jpg" width="238" /> <img src="./images/91_online_episodes.jpg" width="238" />




## Links

- [Changelog](changelog.md)
- [Privacy Policy](PrivacyPolicy.md)
- [Contributing](CONTRIBUTING.md)
- [Translation (Transifex)](https://app.transifex.com/xilinjia/podcini/dashboard/)

## License

Podcini, same as its forked project AntennaPod, is licensed under the GNU General Public License (GPL-3.0).
You can find the license text in the LICENSE file.

## Copyright

New files and modifications in the project is copyrighted in 2024 by Xilin Jia.

Original contents from the forked project maintains copyrights of the AntennaPod team.
