package org.nextcolor.tethertile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.nextcolor.tethertile.ui.SetupScreen
import org.nextcolor.tethertile.ui.SetupState
import org.nextcolor.tethertile.ui.TetherTileTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var status by mutableStateOf(Shell.Status.NotRunning)
    private var tileAdded by mutableStateOf(false)
    private var tethering by mutableIntStateOf(-1)
    private var switching by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onBinderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addBinderReceivedListener(onBinder)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermission)
        setContent {
            val scope = rememberCoroutineScope()
            TetherTileTheme {
                SetupScreen(
                    SetupState(status, tileAdded, tethering, switching, error),
                    onGetShizuku = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD))) },
                    onOpenShizuku = { packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let(::startActivity) },
                    onAllow = { Shizuku.requestPermission(0) },
                    onAddTile = ::addTile,
                    onTethering = { on ->
                        scope.launch {
                            switching = true
                            error = Shell.errorMessage(Shell.setBluetoothTethering(on), on)
                            switching = false
                            refreshTethering()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onBinderDead)
        Shizuku.removeRequestPermissionResultListener(onPermission)
        super.onDestroy()
    }

    private fun refresh() {
        lifecycleScope.launch {
            status = Shell.awaitStatus(this@MainActivity)
            tileAdded = Prefs.tileAdded(this@MainActivity)
            refreshTethering()
        }
    }

    private suspend fun refreshTethering() {
        tethering = if (status == Shell.Status.Ready) Shell.bluetoothTethering() else -1
        if (tethering >= 0) Prefs.setLastState(this, tethering)
    }

    private fun addTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, TetherTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                Prefs.setTileAdded(this, true)
                tileAdded = true
            }
        }
    }

    companion object {
        const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"
    }
}
