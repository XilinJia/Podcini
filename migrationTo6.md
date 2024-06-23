 


Podcini 6 has a different applicationId of Podcini.R, so it installs in parallel with earlier versions of Podcini on any device.
Once installed, it starts afresh, without any podcasts subscribed.

Podcini 6 has overhauled database and routines completely, the iron-age celebrity SQLite is replaced with the modern object-based Realm.
While Podcini 6 can export/import the Realm DB, the SQLite DB from version 5 and below can not be imported.

To update it with your current subscriptions, listening progress, episodes marked favorite, and app preferences,
the following can be imported to it from Settings -> Import/Export:

* preferences files
* OPML file
* json file of episodes progress

An OPML file should be imported before importing episodes progress, but you can always re-do any of the above

These files can be best exported in version 5.5.2 in Settings -> Import/Export

Then your subscriptions, listening progress, favorites, and app preferences will be updated.

Unfortunately, media files will need to be separately downloaded.
