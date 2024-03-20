
## 3.2.3

* First Podcini app
* removed unnecessary network access when screen is on to save more battery
* fixed issue with changing playback speed on S21 Android 14

## 3.2.4

* minor efficiency improvements

# 3.2.5

* fixed feed refresh and info bugs

## 4.0.1

* project restructured as a single module
* new Subscriptions screen
* default page to Subscriptions
* Home removed

## 4.1.0

* New convenient player control
* tags enhancements
* bug fixes
* View binding enabled for mode views in code

## 4.2.0

* Removed InBox feature
* improvement on player UI
* episode description now on first page of player popup page
* localization updates

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
	
## 4.2.2

* bug fix on auto-download mistakenly set in 4.2.1
* Sorry for another change in click operation 
 	* long-press on an icon would be the same as a click
	* click on title area allows operation on the single item
	* long-press on title area would allow for multi-select

## 4.2.3

* fixed bug [Inbox still set as default first tab](https://github.com/XilinJia/Podcini/issues/10)
* cleaned up Inbox related resources
* removed info button in FeedItemList header
* added items count in FeedItemList header
* fixed bug in FeedItemList when filtered list has no items
* buildConfig is set in build.gradle instead of gradle.properties

## 4.2.4

* fixed the "getValue() can not be null" bug
* enabled ksp for Kotlin builds
* cleaned up build.gradle files

## 4.2.5

* change in click operation
	* click on title area opens the podcast/episode
	* long-press on title area automatically enters in selection mode
	* select all above or below are put to action bar together with select all
	* operations are only on the selected (single or multiple)
	* popup menus for single item operation are disabled
* in podcast view, the title bar no longer scrolls off screen

## 4.2.6

* corrected action icons for themes
* revealed info bar in Downloads view
* revealed info bar in Subscriptions view
* reset tags list in Subscriptions when new tag is added

## 4.2.7

* disabled drag actions when in multi-select mode (fixed crash bug)
* renewed PodcastIndex API keys
* added share notes menu option in episode view
* press on title area of an episode now opens the episode info faster and more economically - without horizontal swipe
* press on the icon of an episode opens the episode info the original way - with horizontal swipe