package org.nextcolor.tethertile

import android.content.Context

/** Small bits of state the tile and the setup screen share. */
object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("tile", Context.MODE_PRIVATE)

    /** Last Bluetooth tethering state read from the helper: 1 on, 0 off. */
    fun lastState(context: Context) = prefs(context).getInt("last_state", 0)

    fun setLastState(context: Context, state: Int) = prefs(context).edit().putInt("last_state", state).apply()

    fun tileAdded(context: Context) = prefs(context).getBoolean("tile_added", false)

    fun setTileAdded(context: Context, added: Boolean) = prefs(context).edit().putBoolean("tile_added", added).apply()
}
