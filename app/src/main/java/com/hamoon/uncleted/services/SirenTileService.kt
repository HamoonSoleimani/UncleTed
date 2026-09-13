package com.hamoon.uncleted.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.hamoon.uncleted.receivers.WidgetActionReceiver

@RequiresApi(Build.VERSION_CODES.N)
class SirenTileService : TileService() {
    override fun onStartListening() {
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_SIREN"
        }
        sendBroadcast(intent)
        // Show active state briefly to indicate press
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
        // Reset after a delay if desired, or let system handle it
    }
}