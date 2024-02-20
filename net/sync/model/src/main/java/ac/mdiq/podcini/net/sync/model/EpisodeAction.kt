package ac.mdiq.podcini.net.sync.model

import android.text.TextUtils
import android.util.Log
import ac.mdiq.podcini.model.feed.FeedItem
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class EpisodeAction private constructor(builder: Builder) {
    val podcast: String? = builder.podcast
    val episode: String? = builder.episode
    val guid: String?
    val action: Action?
    val timestamp: Date?

    /**
     * Returns the position (in seconds) at which the client started playback.
     *
     * @return start position (in seconds)
     */
    val started: Int

    /**
     * Returns the position (in seconds) at which the client stopped playback.
     *
     * @return stop position (in seconds)
     */
    val position: Int

    /**
     * Returns the total length of the file in seconds.
     *
     * @return total length in seconds
     */
    val total: Int

    init {
        this.guid = builder.guid
        this.action = builder.action
        this.timestamp = builder.timestamp
        this.started = builder.started
        this.position = builder.position
        this.total = builder.total
    }

    private val actionString: String
        get() = action!!.name.lowercase()

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is EpisodeAction) {
            return false
        }

        val that = o
        return started == that.started && position == that.position && total == that.total && action != that.action && podcast == that.podcast && episode == that.episode && timestamp == that.timestamp && guid == that.guid
    }

    override fun hashCode(): Int {
        var result = podcast?.hashCode() ?: 0
        result = 31 * result + (episode?.hashCode() ?: 0)
        result = 31 * result + (guid?.hashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        result = 31 * result + started
        result = 31 * result + position
        result = 31 * result + total
        return result
    }

    /**
     * Returns a JSON object representation of this object.
     *
     * @return JSON object representation, or null if the object is invalid
     */
    fun writeToJsonObject(): JSONObject? {
        val obj = JSONObject()
        try {
            obj.putOpt("podcast", this.podcast)
            obj.putOpt("episode", this.episode)
            obj.putOpt("guid", this.guid)
            obj.put("action", this.actionString)
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            obj.put("timestamp", formatter.format(this.timestamp))
            if (this.action == Action.PLAY) {
                obj.put("started", this.started)
                obj.put("position", this.position)
                obj.put("total", this.total)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "writeToJSONObject(): " + e.message)
            return null
        }
        return obj
    }

    override fun toString(): String {
        return ("EpisodeAction{"
                + "podcast='" + podcast + '\''
                + ", episode='" + episode + '\''
                + ", guid='" + guid + '\''
                + ", action=" + action
                + ", timestamp=" + timestamp
                + ", started=" + started
                + ", position=" + position
                + ", total=" + total
                + '}')
    }

    enum class Action {
        NEW, DOWNLOAD, PLAY, DELETE
    }

    class Builder(// mandatory
            val podcast: String?, val episode: String?, val action: Action
    ) {
        // optional
        var timestamp: Date? = null
        var started: Int = -1
        var position: Int = -1
        var total: Int = -1
        var guid: String? = null

        constructor(item: FeedItem, action: Action) : this(item.feed!!.download_url,
            item.media!!.download_url,
            action) {
            this.guid(item.itemIdentifier)
        }

        fun timestamp(timestamp: Date?): Builder {
            this.timestamp = timestamp
            return this
        }

        fun guid(guid: String?): Builder {
            this.guid = guid
            return this
        }

        fun currentTimestamp(): Builder {
            return timestamp(Date())
        }

        fun started(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.started = seconds
            }
            return this
        }

        fun position(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.position = seconds
            }
            return this
        }

        fun total(seconds: Int): Builder {
            if (action == Action.PLAY) {
                this.total = seconds
            }
            return this
        }

        fun build(): EpisodeAction {
            return EpisodeAction(this)
        }
    }

    companion object {
        private const val TAG = "EpisodeAction"
        private const val PATTERN_ISO_DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss"
        val NEW: Action = Action.NEW
        val DOWNLOAD: Action = Action.DOWNLOAD
        val PLAY: Action = Action.PLAY
        val DELETE: Action = Action.DELETE

        /**
         * Create an episode action object from JSON representation. Mandatory fields are "podcast",
         * "episode" and "action".
         *
         * @param object JSON representation
         * @return episode action object, or null if mandatory values are missing
         */
        @JvmStatic
        fun readFromJsonObject(`object`: JSONObject): EpisodeAction? {
            val podcast = `object`.optString("podcast", null)
            val episode = `object`.optString("episode", null)
            val actionString = `object`.optString("action", null)
            if (TextUtils.isEmpty(podcast) || TextUtils.isEmpty(episode) || TextUtils.isEmpty(actionString)) {
                return null
            }
            val action: Action
            try {
                action = Action.valueOf(actionString.uppercase())
            } catch (e: IllegalArgumentException) {
                return null
            }
            val builder = Builder(podcast, episode, action)
            val utcTimestamp = `object`.optString("timestamp", null)
            if (!TextUtils.isEmpty(utcTimestamp)) {
                try {
                    val parser = SimpleDateFormat(PATTERN_ISO_DATEFORMAT, Locale.US)
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                    builder.timestamp(parser.parse(utcTimestamp))
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
            val guid = `object`.optString("guid", null)
            if (!TextUtils.isEmpty(guid)) {
                builder.guid(guid)
            }
            if (action == Action.PLAY) {
                val started = `object`.optInt("started", -1)
                val position = `object`.optInt("position", -1)
                val total = `object`.optInt("total", -1)
                if (started >= 0 && position > 0 && total > 0) {
                    builder
                        .started(started)
                        .position(position)
                        .total(total)
                }
            }
            return builder.build()
        }
    }
}
