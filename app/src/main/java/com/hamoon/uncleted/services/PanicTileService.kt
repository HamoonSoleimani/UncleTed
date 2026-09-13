package com.hamoon.uncleted.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.hamoon.uncleted.LockScreenActivity

@RequiresApi(Build.VERSION_CODES.N)
class PanicTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Uncle Ted Lock"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Action: Lock the device immediately
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivityAndCollapse(lockIntent)
    }
}