package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.util.Logd
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent

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

    private fun updateTile() {
        val qsTile = qsTile
        if (qsTile == null) Logd(TAG, "Ignored call to update QS tile: getQsTile() returned null.")
        else {
            val isPlaying = (PlaybackService.isRunning && curState.curPlayerStatus == PLAYER_STATUS_PLAYING)
            qsTile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }

    companion object {
        private val TAG: String = QuickSettingsTileService::class.simpleName ?: "Anonymous"
    }
}
