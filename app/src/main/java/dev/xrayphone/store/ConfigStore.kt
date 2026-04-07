package dev.xrayphone.store

import android.content.Context
import android.os.Build
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

data class StatusSnapshot(
    val serviceState: String,
    val autostart: Boolean,
    val hasConfig: Boolean,
    val lastError: String,
    val configPath: String,
    val xrayVersion: String,
    val tokenConfigured: Boolean,
)

class ConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val storageContext = appContext.createDeviceProtectedStorageContext()
    private val prefs = storageContext.getSharedPreferences("xray_phone", Context.MODE_PRIVATE)
    private val stateDir = File(storageContext.filesDir, "xray")
    private val configFile = File(stateDir, "config.json")

    init {
        migrateLegacyStorage()
    }

    fun configFile(): File {
        if (!stateDir.exists()) {
            stateDir.mkdirs()
        }
        return configFile
    }

    fun hasConfig(): Boolean = configFile().isFile

    fun saveConfigBase64(payload: String) {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        configFile().writeBytes(bytes)
        setLastError("")
    }

    fun setAutostart(enabled: Boolean) {
        prefs.edit().putBoolean("autostart", enabled).apply()
    }

    fun autostart(): Boolean = prefs.getBoolean("autostart", false)

    fun setServiceState(state: String) {
        prefs.edit().putString("service_state", state).apply()
    }

    fun setLastError(message: String) {
        prefs.edit().putString("last_error", message).apply()
    }

    fun snapshot(): StatusSnapshot = StatusSnapshot(
        serviceState = prefs.getString("service_state", "stopped").orEmpty(),
        autostart = autostart(),
        hasConfig = hasConfig(),
        lastError = prefs.getString("last_error", "").orEmpty(),
        configPath = configFile().absolutePath,
        xrayVersion = prefs.getString("xray_version", "").orEmpty(),
        tokenConfigured = !prefs.getString("control_token_sha256", "").isNullOrBlank(),
    )

    fun setXrayVersion(version: String) {
        prefs.edit().putString("xray_version", version).apply()
    }

    fun rotateControlToken(): String {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val token = Base64.encodeToString(random, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        prefs.edit().putString("control_token_sha256", sha256Hex(token)).apply()
        return token
    }

    fun hasControlToken(): Boolean = !prefs.getString("control_token_sha256", "").isNullOrBlank()

    fun verifyControlToken(rawToken: String?): Boolean {
        if (rawToken.isNullOrBlank()) {
            return false
        }

        val expected = prefs.getString("control_token_sha256", "")?.toByteArray(Charsets.UTF_8) ?: return false
        val actual = sha256Hex(rawToken).toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun migrateLegacyStorage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }

        storageContext.moveSharedPreferencesFrom(appContext, "xray_phone")

        val legacyConfig = File(appContext.filesDir, "xray/config.json")
        val targetConfig = configFile()
        if (!targetConfig.exists() && legacyConfig.exists()) {
            targetConfig.parentFile?.mkdirs()
            legacyConfig.copyTo(targetConfig, overwrite = false)
        }
    }
}
