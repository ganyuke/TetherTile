package org.nextcolor.tethertile

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** The app's side of the Shizuku connection. Hands out the [ShellService] binder. */
object Shell {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    enum class Status { NotInstalled, NotRunning, NoPermission, Ready }

    private val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
        .daemon(true)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    @Volatile
    private var service: IShellService? = null
    private var pending: CompletableDeferred<IShellService?>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            val s = binder?.takeIf { it.pingBinder() }?.let { IShellService.Stub.asInterface(it) }
            service = s
            pending?.complete(s)
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun status(context: Context): Status = when {
        Shizuku.pingBinder() -> if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) Status.Ready else Status.NoPermission
        isShizukuInstalled(context) -> Status.NotRunning
        else -> Status.NotInstalled
    }

    /**
     * Like [status], but waits briefly for Shizuku to connect first. Right after this
     * app's process starts, the Shizuku binder takes a moment to arrive.
     */
    suspend fun awaitStatus(context: Context): Status {
        if (!Shizuku.pingBinder() && isShizukuInstalled(context)) {
            val received = CompletableDeferred<Unit>()
            val listener = Shizuku.OnBinderReceivedListener { received.complete(Unit) }
            Shizuku.addBinderReceivedListenerSticky(listener)
            withTimeoutOrNull(1_500) { received.await() }
            Shizuku.removeBinderReceivedListener(listener)
        }
        return status(context)
    }

    fun isShizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** The helper running under Shizuku, started if needed. Null when it can't be reached. */
    suspend fun service(): IShellService? {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        val deferred = withContext(Dispatchers.Main) {
            pending ?: CompletableDeferred<IShellService?>().also {
                pending = it
                Shizuku.bindUserService(args, connection)
            }
        }
        return withTimeoutOrNull(10_000) { deferred.await() }
    }

    /** 1 on, 0 off, -1 unknown. */
    suspend fun bluetoothTethering(): Int = withContext(Dispatchers.IO) {
        try {
            service()?.bluetoothTethering() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /** 0 on success, otherwise an error code from [ShellService.setBluetoothTethering]. */
    suspend fun setBluetoothTethering(on: Boolean): Int = withContext(Dispatchers.IO) {
        try {
            service()?.setBluetoothTethering(on) ?: SERVICE_UNREACHABLE
        } catch (e: Exception) {
            SERVICE_UNREACHABLE
        }
    }

    private const val SERVICE_UNREACHABLE = -101

    /** Message for a failed switch, or null on success. */
    fun errorMessage(code: Int, on: Boolean): String? = when (code) {
        0 -> null
        ShellService.BLUETOOTH_DID_NOT_TURN_ON -> "Couldn't turn on Bluetooth"
        SERVICE_UNREACHABLE -> "Couldn't connect to Shizuku"
        3 -> "Tethering isn't supported"
        11 -> "Tethering isn't allowed by your carrier"
        14 -> "Tethering permission denied"
        else -> "Couldn't turn ${if (on) "on" else "off"} Bluetooth tethering (error $code)"
    }
}
