package org.nextcolor.tethertile;

// Runs as the shell user under Shizuku. See ShellService.
interface IShellService {
    // Called by the Shizuku server to stop the helper.
    void destroy() = 16777114;

    // 1 when Bluetooth tethering is on, 0 when off, -1 when it can't be read.
    int bluetoothTethering() = 1;

    // Returns 0 on success, or a TetheringManager.TETHER_ERROR_* code.
    // -100 means Bluetooth didn't turn on.
    int setBluetoothTethering(boolean on) = 2;
}
