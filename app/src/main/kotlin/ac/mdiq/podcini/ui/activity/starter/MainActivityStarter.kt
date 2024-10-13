package ac.mdiq.podcini.ui.activity.starter


import ac.mdiq.podcini.R
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Launches the main activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class MainActivityStarter(private val context: Context) {
    private val intent: Intent = Intent(INTENT)
    private var fragmentArgs: Bundle? = null
    val pendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_player_activity, getIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    init {
        intent.setPackage(context.packageName)
    }

    fun getIntent(): Intent {
        if (fragmentArgs != null) intent.putExtra(Extras.fragment_args.name, fragmentArgs)
        return intent
    }

    fun start() {
        context.startActivity(getIntent())
    }

    fun withOpenPlayer(): MainActivityStarter {
        intent.putExtra(Extras.open_player.name, true)
        return this
    }

    fun withOpenFeed(feedId: Long): MainActivityStarter {
        intent.putExtra(Extras.fragment_feed_id.name, feedId)
        return this
    }

    fun withAddToBackStack(): MainActivityStarter {
        intent.putExtra(Extras.add_to_back_stack.name, true)
        return this
    }

    fun withFragmentLoaded(fragmentName: String?): MainActivityStarter {
        intent.putExtra(Extras.fragment_tag.name, fragmentName)
        return this
    }

    fun withDrawerOpen(): MainActivityStarter {
        intent.putExtra(Extras.open_drawer.name, true)
        return this
    }

    fun withDownloadLogsOpen(): MainActivityStarter {
        intent.putExtra(Extras.open_logs.name, true)
        return this
    }

    fun withFragmentArgs(name: String?, value: Boolean): MainActivityStarter {
        if (fragmentArgs == null) fragmentArgs = Bundle()
        fragmentArgs!!.putBoolean(name, value)
        return this
    }

    @Suppress("EnumEntryName")
    enum class Extras {
        open_player,
        fragment_feed_id,
        add_to_back_stack,
        fragment_tag,
        open_drawer,
        open_logs,
        fragment_args
    }
    companion object {
        const val INTENT: String = "ac.mdiq.podcini.intents.MAIN_ACTIVITY"
    }
}
