package com.hamoon.uncleted.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.hamoon.uncleted.receivers.WidgetActionReceiver

@RequiresApi(Build.VERSION_CODES.N)
class WipeTileService : TileService() {
    override fun onStartListening() {
        qsTile.state = Tile.STATE_INACTIVE // Default state
        qsTile.updateTile()
    }

    override fun onClick() {
        // Trigger the wipe action
        val intent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_WIPE"
        }
        sendBroadcast(intent)

        // Show active briefly
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }
}