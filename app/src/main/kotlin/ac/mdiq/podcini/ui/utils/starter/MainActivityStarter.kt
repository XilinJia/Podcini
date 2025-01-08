package ac.mdiq.podcini.ui.utils.starter


import android.content.Context
import android.content.Intent

/**
 * Launches the main activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class MainActivityStarter(private val context: Context) {
    private val intent: Intent = Intent(INTENT)

    init {
        intent.setPackage(context.packageName)
    }

    fun getIntent(): Intent {
        return intent
    }

    fun start() {
        context.startActivity(intent)
    }

    fun withOpenPlayer(): MainActivityStarter {
        intent.putExtra(Extras.open_player.name, true)
        return this
    }

    @Suppress("EnumEntryName")
    enum class Extras {
        open_player,
    }
    companion object {
        const val INTENT: String = "ac.mdiq.podcini.intents.MAIN_ACTIVITY"
    }
}
