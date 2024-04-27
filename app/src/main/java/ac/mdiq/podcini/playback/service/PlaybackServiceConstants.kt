package ac.mdiq.podcini.playback.service

object PlaybackServiceConstants {
    const val EXTRA_PLAYABLE: String = "PlaybackService.PlayableExtra"
    const val EXTRA_ALLOW_STREAM_THIS_TIME: String = "extra.ac.mdiq.podcini.service.allowStream"
    const val EXTRA_ALLOW_STREAM_ALWAYS: String = "extra.ac.mdiq.podcini.service.allowStreamAlways"

    const val ACTION_PLAYER_NOTIFICATION: String = "action.ac.mdiq.podcini.service.playerNotification"
    const val EXTRA_NOTIFICATION_CODE: String = "extra.ac.mdiq.podcini.service.notificationCode"
    const val EXTRA_NOTIFICATION_TYPE: String = "extra.ac.mdiq.podcini.service.notificationType"
    const val NOTIFICATION_TYPE_PLAYBACK_END: Int = 7
    const val NOTIFICATION_TYPE_RELOAD: Int = 3
    const val EXTRA_CODE_AUDIO: Int = 1 // Used in NOTIFICATION_TYPE_RELOAD
    const val EXTRA_CODE_VIDEO: Int = 2
    const val EXTRA_CODE_CAST: Int = 3

    const val ACTION_SHUTDOWN_PLAYBACK_SERVICE: String = "action.ac.mdiq.podcini.service.actionShutdownPlaybackService"
}
