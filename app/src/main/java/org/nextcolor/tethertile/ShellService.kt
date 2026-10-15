package org.nextcolor.tethertile

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.IBinder
import android.os.Process
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.system.exitProcess

/**
 * Started by Shizuku in its own process, running as the shell user (uid 2000).
 * The shell user holds TETHER_PRIVILEGED, BLUETOOTH_CONNECT and
 * BLUETOOTH_PRIVILEGED, which is what Settings uses to switch Bluetooth
 * tethering. Hidden APIs are reached by reflection. The process is started with
 * app_process, so hidden API restrictions don't apply here.
 */
class ShellService(context: Context) : IShellService.Stub() {

    // The tethering and Bluetooth services check that the calling package belongs
    // to the calling uid, so every request names the shell package instead of this app.
    private val shell = object : ContextWrapper(context) {
        val source: AttributionSource = AttributionSource.Builder(Process.myUid()).setPackageName(SHELL_PACKAGE).build()
        override fun getOpPackageName() = SHELL_PACKAGE
        override fun getAttributionTag(): String? = null
        override fun getAttributionSource() = source
    }

    private val adapter: BluetoothAdapter by lazy {
        BluetoothAdapter::class.java.getMethod("createAdapter", AttributionSource::class.java)
            .invoke(null, shell.source) as BluetoothAdapter
    }

    private val tethering: Any by lazy {
        val serviceManager = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
        Class.forName("android.net.TetheringManager")
            .getConstructor(Context::class.java, Supplier::class.java)
            .newInstance(shell, Supplier { serviceManager.invoke(null, "tethering") as IBinder? })
    }

    @Volatile
    private var pan: BluetoothProfile? = null

    override fun destroy() {
        exitProcess(0)
    }

    override fun bluetoothTethering(): Int {
        if (adapter.state != BluetoothAdapter.STATE_ON) return 0
        val pan = pan() ?: return -1
        return try {
            if (pan.javaClass.getMethod("isTetheringOn").invoke(pan) == true) 1 else 0
        } catch (e: Exception) {
            -1
        }
    }

    override fun setBluetoothTethering(on: Boolean): Int {
        if (!on) {
            if (adapter.state != BluetoothAdapter.STATE_ON) return 0
            tethering.javaClass.getMethod("stopTethering", Int::class.java).invoke(tethering, TETHERING_BLUETOOTH)
            // stopTethering has no result, so wait for the PAN service to report it.
            waitFor(3_000) { bluetoothTethering() != 1 }
            return 0
        }

        // Same as Settings: switching on Bluetooth tethering also turns on Bluetooth.
        if (adapter.state != BluetoothAdapter.STATE_ON) {
            Runtime.getRuntime().exec(arrayOf("cmd", "bluetooth_manager", "enable")).waitFor()
            if (!waitFor(10_000) { adapter.state == BluetoothAdapter.STATE_ON }) return BLUETOOTH_DID_NOT_TURN_ON
        }

        val callbackClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        var result = TETHER_ERROR_INTERNAL_ERROR
        val done = CountDownLatch(1)
        val callback = Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
            when (method.name) {
                "onTetheringStarted" -> { result = 0; done.countDown() }
                "onTetheringFailed" -> { result = args[0] as Int; done.countDown() }
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "equals" -> return@newProxyInstance proxy === args[0]
                "toString" -> return@newProxyInstance "StartTetheringCallback"
            }
            null
        }
        tethering.javaClass
            .getMethod("startTethering", Int::class.java, Executor::class.java, callbackClass)
            .invoke(tethering, TETHERING_BLUETOOTH, Executor { it.run() }, callback)
        done.await(15, TimeUnit.SECONDS)
        return result
    }

    /** The PAN profile proxy, connecting to it first if needed. Null while Bluetooth is off. */
    private fun pan(): BluetoothProfile? {
        pan?.let { return it }
        val connected = CountDownLatch(1)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                pan = proxy
                connected.countDown()
            }

            override fun onServiceDisconnected(profile: Int) {
                pan = null
            }
        }
        // The listener is called on this process's main looper, which Shizuku keeps running.
        if (!adapter.getProfileProxy(shell, listener, PAN)) return null
        connected.await(3, TimeUnit.SECONDS)
        return pan
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"

        /** BluetoothProfile.PAN, hidden from the SDK. */
        const val PAN = 5

        /** TetheringManager.TETHERING_BLUETOOTH */
        const val TETHERING_BLUETOOTH = 2

        /** TetheringManager.TETHER_ERROR_INTERNAL_ERROR, also used when the callback never came back. */
        const val TETHER_ERROR_INTERNAL_ERROR = 5

        /** Not a TetheringManager code. Bluetooth was off and didn't turn on. */
        const val BLUETOOTH_DID_NOT_TURN_ON = -100
    }
}
