package ac.mdiq.podcini.ui.activity.appstartintent


import ac.mdiq.podcini.R
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Launches the main activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class MainActivityStarter(private val context: Context) {
    private val intent: Intent = Intent(INTENT)
    private var fragmentArgs: Bundle? = null

    init {
        intent.setPackage(context.packageName)
    }

    fun getIntent(): Intent {
        if (fragmentArgs != null) intent.putExtra(EXTRA_FRAGMENT_ARGS, fragmentArgs)
        return intent
    }

    val pendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_player_activity, getIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun start() {
        context.startActivity(getIntent())
    }

    fun withOpenPlayer(): MainActivityStarter {
        intent.putExtra(EXTRA_OPEN_PLAYER, true)
        return this
    }

    fun withOpenFeed(feedId: Long): MainActivityStarter {
        intent.putExtra(EXTRA_FEED_ID, feedId)
        return this
    }

    fun withAddToBackStack(): MainActivityStarter {
        intent.putExtra(EXTRA_ADD_TO_BACK_STACK, true)
        return this
    }

    fun withFragmentLoaded(fragmentName: String?): MainActivityStarter {
        intent.putExtra(EXTRA_FRAGMENT_TAG, fragmentName)
        return this
    }

    fun withDrawerOpen(): MainActivityStarter {
        intent.putExtra(EXTRA_OPEN_DRAWER, true)
        return this
    }

    fun withDownloadLogsOpen(): MainActivityStarter {
        intent.putExtra(EXTRA_OPEN_DOWNLOAD_LOGS, true)
        return this
    }

    fun withFragmentArgs(name: String?, value: Boolean): MainActivityStarter {
        if (fragmentArgs == null) fragmentArgs = Bundle()

        fragmentArgs!!.putBoolean(name, value)
        return this
    }

    companion object {
        const val INTENT: String = "ac.mdiq.podcini.intents.MAIN_ACTIVITY"
        const val EXTRA_OPEN_PLAYER: String = "open_player"
        const val EXTRA_FEED_ID: String = "fragment_feed_id"
        const val EXTRA_ADD_TO_BACK_STACK: String = "add_to_back_stack"
        const val EXTRA_FRAGMENT_TAG: String = "fragment_tag"
        const val EXTRA_OPEN_DRAWER: String = "open_drawer"
        const val EXTRA_OPEN_DOWNLOAD_LOGS: String = "open_download_logs"
        const val EXTRA_FRAGMENT_ARGS: String = "fragment_args"
    }
}
