package dev.xrayphone.control

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.VpnService
import android.util.Log
import dev.xrayphone.store.ConfigStore
import dev.xrayphone.vpn.XrayVpnService

class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ConfigStore(context)
        val sender = SenderInfo.from(context, this)

        try {
            if (!sender.isAllowed) {
                Log.w(TAG, "rejected sender uid=${sender.uid} package=${sender.packageName}")
                return replyError("unauthorized sender")
            }

            when (intent.action) {
                Actions.PAIR -> {
                    if (!sender.isTermux && !sender.isShellLike && !sender.isUnknownBridge) {
                        return replyError("pairing is only allowed from Termux")
                    }
                    handlePair(store)
                }
                else -> {
                    if (!sender.isShellLike && !store.verifyControlToken(intent.getStringExtra(Actions.EXTRA_TOKEN))) {
                        return replyError("invalid token")
                    }

                    when (intent.action) {
                        Actions.SYNC_CONFIG -> handleSync(store, intent)
                        Actions.ON -> handleOn(context, store)
                        Actions.OFF -> handleOff(context, store)
                        Actions.STATUS -> replyOk(renderStatus(context, store))
                        Actions.INFO -> replyOk(renderInfo(context, store))
                        Actions.DIAG -> replyOk(renderDiag(context, store))
                        Actions.SET_AUTOSTART -> handleAutostart(store, intent)
                        else -> replyError("unknown action: ${intent.action.orEmpty()}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "control action failed", e)
            store.setLastError(e.message.orEmpty())
            replyError(e.message ?: "unknown error")
        }
    }

    private fun handlePair(store: ConfigStore) {
        val token = store.rotateControlToken()
        replyOk("token=$token")
    }

    private fun handleSync(store: ConfigStore, intent: Intent) {
        val payload = intent.getStringExtra(Actions.EXTRA_CONFIG_BASE64)
            ?: return replyError("missing config_base64")

        store.saveConfigBase64(payload)
        replyOk("config synced: ${store.configFile().absolutePath}")
    }

    private fun handleOn(context: Context, store: ConfigStore) {
        if (!store.hasConfig()) {
            return replyError("missing config: run xrayctl load first")
        }

        if (VpnService.prepare(context) != null) {
            return replyError("vpn permission missing: run xrayctl prepare first")
        }

        val serviceIntent = Intent(context, XrayVpnService::class.java).setAction(Actions.ON)
        context.startForegroundService(serviceIntent)
        replyOk("vpn start requested")
    }

    private fun handleOff(context: Context, store: ConfigStore) {
        val serviceIntent = Intent(context, XrayVpnService::class.java).setAction(Actions.OFF)
        context.startService(serviceIntent)
        store.setServiceState("stopped")
        replyOk("vpn stop requested")
    }

    private fun handleAutostart(store: ConfigStore, intent: Intent) {
        val enabled = intent.getBooleanExtra(Actions.EXTRA_ENABLED, false)
        store.setAutostart(enabled)
        replyOk("autostart=${if (enabled) 1 else 0}")
    }

    private fun renderStatus(context: Context, store: ConfigStore): String {
        val snapshot = store.snapshot()
        val permission = if (VpnService.prepare(context) == null) "granted" else "missing"
        return buildString {
            appendLine("service_state=${snapshot.serviceState}")
            appendLine("autostart=${if (snapshot.autostart) 1 else 0}")
            appendLine("permission=$permission")
            appendLine("config_present=${if (snapshot.hasConfig) 1 else 0}")
            appendLine("token_configured=${if (snapshot.tokenConfigured) 1 else 0}")
            if (snapshot.lastError.isNotBlank()) {
                appendLine("last_error=${snapshot.lastError}")
            }
        }.trimEnd()
    }

    private fun renderInfo(context: Context, store: ConfigStore): String {
        val snapshot = store.snapshot()
        return buildString {
            appendLine(renderStatus(context, store))
            appendLine("config_path=${snapshot.configPath}")
            if (snapshot.xrayVersion.isNotBlank()) {
                appendLine("xray_version=${snapshot.xrayVersion}")
            }
            appendLine("package=dev.xrayphone")
        }.trimEnd()
    }

    private fun renderDiag(context: Context, store: ConfigStore): String {
        val snapshot = store.snapshot()
        return buildString {
            appendLine(renderStatus(context, store))
            appendLine("diag_note=using direct xray tun fd path; no socks bridge mode")
            appendLine("config_path=${snapshot.configPath}")
        }.trimEnd()
    }

    private fun replyOk(message: String) {
        setResultCode(Activity.RESULT_OK)
        setResultData(message)
    }

    private fun replyError(message: String) {
        setResultCode(Activity.RESULT_CANCELED)
        setResultData(message)
    }

    companion object {
        private const val TAG = "XrayPhone"
        private const val ROOT_UID = 0
        private const val SHELL_UID = 2000
        private const val TERMUX_PACKAGE = "com.termux"
    }

    private data class SenderInfo(
        val uid: Int,
        val packageName: String?,
        val isAllowed: Boolean,
        val isShellLike: Boolean,
        val isTermux: Boolean,
        val isUnknownBridge: Boolean,
    ) {
        companion object {
            fun from(context: Context, receiver: BroadcastReceiver): SenderInfo {
                val uid = if (Build.VERSION.SDK_INT >= 34) receiver.sentFromUid else -1
                val packageName = if (Build.VERSION.SDK_INT >= 34) receiver.sentFromPackage else null
                val packagesForUid = if (uid >= 0) context.packageManager.getPackagesForUid(uid)?.toList().orEmpty() else emptyList()
                val isShellLike = uid == ROOT_UID || uid == SHELL_UID
                val isTermux = packageName == TERMUX_PACKAGE || packagesForUid.contains(TERMUX_PACKAGE)
                val isUnknownBridge = uid < 0 && packageName == null
                return SenderInfo(
                    uid = uid,
                    packageName = packageName,
                    isAllowed = isShellLike || isTermux || isUnknownBridge,
                    isShellLike = isShellLike,
                    isTermux = isTermux,
                    isUnknownBridge = isUnknownBridge,
                )
            }
        }
    }
}
