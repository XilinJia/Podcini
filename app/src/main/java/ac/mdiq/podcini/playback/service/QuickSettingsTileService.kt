package ac.mdiq.podcini.playback.service

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.preferences.PlaybackPreferences
import ac.mdiq.podcini.receiver.MediaButtonReceiver

@UnstableApi
@RequiresApi(api = Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {
    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MediaButtonReceiver::class.java)
        intent.setAction(MediaButtonReceiver.NOTIFY_BUTTON_RECEIVER)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        sendBroadcast(intent)
    }

    // Update the tile status when TileService.requestListeningState() is called elsewhere
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // Without this, the tile may not be in the correct state after boot
    override fun onBind(intent: Intent): IBinder? {
        requestListeningState(this, ComponentName(this, QuickSettingsTileService::class.java))
        return super.onBind(intent)
    }

    fun updateTile() {
        val qsTile = qsTile
        if (qsTile == null) Log.d(TAG, "Ignored call to update QS tile: getQsTile() returned null.")
        else {
            val isPlaying = (PlaybackService.isRunning && PlaybackPreferences.currentPlayerStatus == PlaybackPreferences.PLAYER_STATUS_PLAYING)
            qsTile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }

    companion object {
        private const val TAG = "QuickSettingsTileSvc"
    }
}
