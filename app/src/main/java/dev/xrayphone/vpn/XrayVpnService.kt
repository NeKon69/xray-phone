package dev.xrayphone.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.IBinder
import android.util.Log
import dev.xrayphone.control.Actions
import dev.xrayphone.store.ConfigStore
import java.io.File

class XrayVpnService : VpnService() {
    private val store by lazy { ConfigStore(this) }
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        XrayRuntime.init(this) {
            stopVpn("stopped by core")
        }
        store.setXrayVersion(XrayRuntime.version())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.OFF -> stopVpn("stopped by user")
            else -> startVpn()
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        closeInterface()
        super.onDestroy()
        store.setServiceState("stopped")
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun startVpn() {
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.build(this, getString(dev.xrayphone.R.string.notif_text)),
        )

        if (prepare(this) != null) {
            fail("vpn permission missing")
            return
        }

        if (!store.hasConfig()) {
            fail("config missing")
            return
        }

        if (XrayRuntime.isRunning()) {
            store.setServiceState("running")
            store.setLastError("")
            return
        }

        val established = establishVpn()
        if (established == null) {
            fail("failed to establish vpn interface")
            return
        }

        val configText = runCatching { File(store.snapshot().configPath).readText() }
            .getOrElse {
                fail("failed to read config: ${it.message}")
                return
            }

        runCatching {
            XrayRuntime.start(configText, established.fd)
        }.onSuccess {
            store.setServiceState("running")
            store.setLastError("")
            Log.i(TAG, "xray vpn started")
        }.onFailure {
            fail("xray start failed: ${it.message}")
        }
    }

    private fun stopVpn(reason: String) {
        runCatching { XrayRuntime.stop() }
        store.setServiceState("stopped")
        store.setLastError(reason)
        closeInterface()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        store.setServiceState("error")
        store.setLastError(message)
        closeInterface()
        Log.e(TAG, message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        closeInterface()

        val builder = Builder()
            .setSession("Xray Phone")
            .setMtu(1500)
            .addAddress("10.111.222.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        runCatching {
            builder.addAddress("fd00:1:fd00:1::1", 126)
            builder.addRoute("::", 0)
            builder.addDnsServer("2606:4700:4700::1111")
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
        }

        vpnInterface = builder.establish()
        return vpnInterface
    }

    private fun closeInterface() {
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    companion object {
        private const val TAG = "XrayPhone"
    }
}
