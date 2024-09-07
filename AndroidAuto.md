Since version 6.4.0, Podcini has been back to run on Android Auto.  It runs fine on "desktop head unit" and the screenshots are on Readme.

So this appears the issue with Podcini not showing up on car display, from Google document:

> To test your app in real vehicles, it must be installed from a trusted source such as the Play Store, with one exception detailed in [Allow unknown sources](https://developer.android.com/training/cars/testing#unknown-sources).

I know "such as" indicates there are other trusted sources, but I don't know which are deemed as such.  Podcini is currently not listed yet on Google Play.  To work around it, again from Google document:

> On Android Auto, there is also a [developer option](https://developer.android.com/training/cars/testing#developer-mode) to enable running apps not installed from a trusted source. This setting only applies to [media](https://developer.android.com/training/cars/media) and [messaging](https://developer.android.com/training/cars/messaging) apps

Details here: https://developer.android.com/training/cars/testing#developer-mode

This has been tested as working.
