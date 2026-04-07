package dev.xrayphone.vpn

import android.content.Context
import android.util.Base64
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object XrayRuntime {
    private const val TAG = "XrayPhone"
    private val initialized = AtomicBoolean(false)
    private var controller: CoreController? = null

    fun init(context: Context, onShutdown: () -> Unit) {
        if (initialized.compareAndSet(false, true)) {
            Seq.setContext(context.applicationContext)
            val key = Base64.encodeToString(
                context.packageName.toByteArray(Charsets.UTF_8).copyOf(32),
                Base64.NO_PADDING or Base64.URL_SAFE,
            )
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, key)
            controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long {
                    Log.i(TAG, "xray core startup callback")
                    return 0
                }

                override fun shutdown(): Long {
                    Log.i(TAG, "xray core shutdown callback")
                    onShutdown()
                    return 0
                }

                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    Log.i(TAG, "xray status: ${p1.orEmpty()}")
                    return 0
                }
            })
        }
    }

    fun isRunning(): Boolean = controller?.isRunning ?: false

    fun version(): String = Libv2ray.checkVersionX()

    fun start(configContent: String, tunFd: Int) {
        val runtimeConfig = ensureTunInbound(configContent)
        controller?.startLoop(runtimeConfig, tunFd)
            ?: error("xray controller is not initialized")
    }

    fun stop() {
        controller?.stopLoop()
    }

    private fun ensureTunInbound(configContent: String): String {
        val root = JSONObject(configContent)
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray().also { root.put("inbounds", it) }

        val hasTunInbound = (0 until inbounds.length()).any { index ->
            val inbound = inbounds.optJSONObject(index) ?: return@any false
            inbound.optString("protocol").equals("tun", ignoreCase = true)
        }

        if (!hasTunInbound) {
            val tunInbound = JSONObject()
                .put("port", 0)
                .put("protocol", "tun")
                .put(
                    "settings",
                    JSONObject()
                        .put("name", "xray0")
                        .put("MTU", 1500)
                        .put("userLevel", 0),
                )
            inbounds.put(tunInbound)
        }

        return root.toString()
    }
}
