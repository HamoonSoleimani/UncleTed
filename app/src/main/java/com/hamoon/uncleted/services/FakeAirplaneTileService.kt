package com.hamoon.uncleted.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.hamoon.uncleted.data.SecurityPreferences

@RequiresApi(Build.VERSION_CODES.N)
class FakeAirplaneTileService : TileService() {

    companion object {
        private const val TAG = "FakeAirplaneTile"
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Airplane mode"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isPinChallengeRequired = SecurityPreferences.isFakeAirplanePinChallengeEnabled(this)

        if (isPinChallengeRequired) {
            Log.i(TAG, "Fake Airplane Mode tile clicked. PIN challenge required. Presenting confirmation barrier...")
            val intent = Intent(this, FakeAirplaneConfirmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Android 14+ (API 34) requires PendingIntent for startActivityAndCollapse
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    7001,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            Log.i(TAG, "Fake Airplane Mode tile clicked. PIN challenge disabled. Executing decoy action immediately...")
            val tile = qsTile
            if (tile != null) {
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
            FakeAirplaneConfirmActivity.executeTrapProtocol(applicationContext)
        }
    }
}
