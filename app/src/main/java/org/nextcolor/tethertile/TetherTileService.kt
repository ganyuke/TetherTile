package org.nextcolor.tethertile

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile. Taps work on the lock screen too: this service never
 * calls unlockAndRun, except to open the app when setup isn't finished.
 */
class TetherTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var switching: Job? = null

    override fun onTileAdded() {
        Prefs.setTileAdded(this, true)
    }

    override fun onTileRemoved() {
        Prefs.setTileAdded(this, false)
    }

    override fun onStartListening() {
        if (switching?.isActive == true) return
        // Show the last known state at once, then check.
        show(Prefs.lastState(this))
        scope.launch { refresh() }
    }

    override fun onClick() {
        if (switching?.isActive == true) return
        if (Shell.status(this) != Shell.Status.Ready) {
            openApp()
            return
        }
        val on = qsTile.state != Tile.STATE_ACTIVE
        switching = scope.launch {
            show(if (on) 1 else 0, if (on) "Turning on…" else "Turning off…")
            val error = Shell.errorMessage(Shell.setBluetoothTethering(on), on)
            if (error != null) Toast.makeText(this@TetherTileService, error, Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refresh() {
        if (Shell.awaitStatus(this) != Shell.Status.Ready) {
            show(0, "Set up")
            return
        }
        val state = Shell.bluetoothTethering()
        if (state >= 0) Prefs.setLastState(this, state)
        show(if (state >= 0) state else Prefs.lastState(this))
    }

    private fun show(state: Int, subtitle: String? = null) {
        val tile = qsTile ?: return
        tile.state = if (state == 1) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = subtitle ?: if (state == 1) "On" else "Off"
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }
}
