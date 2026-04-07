package dev.xrayphone.control

object Actions {
    private const val PREFIX = "dev.xrayphone.action"

    const val PAIR = "$PREFIX.PAIR"
    const val ON = "$PREFIX.ON"
    const val OFF = "$PREFIX.OFF"
    const val STATUS = "$PREFIX.STATUS"
    const val INFO = "$PREFIX.INFO"
    const val DIAG = "$PREFIX.DIAG"
    const val SYNC_CONFIG = "$PREFIX.SYNC_CONFIG"
    const val SET_AUTOSTART = "$PREFIX.SET_AUTOSTART"

    const val EXTRA_CONFIG_BASE64 = "config_base64"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_TOKEN = "token"
}
