package dev.xrayphone.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import dev.xrayphone.store.ConfigStore
import dev.xrayphone.vpn.XrayVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ConfigStore(context)
        if (!store.autostart() || !store.hasConfig()) {
            return
        }

        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "boot start skipped: vpn permission not granted")
            return
        }

        val serviceIntent = Intent(context, XrayVpnService::class.java).setAction(Actions.ON)
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "XrayPhone"
    }
}
