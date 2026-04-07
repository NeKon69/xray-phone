package dev.xrayphone.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log

class PrepareVpnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            setResult(RESULT_OK)
            finish()
            return
        }

        startActivityForResult(prepareIntent, REQUEST_PREPARE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PREPARE) {
            Log.i(TAG, "vpn permission result=$resultCode")
            setResult(resultCode)
            finish()
        }
    }

    companion object {
        private const val TAG = "XrayPhone"
        private const val REQUEST_PREPARE = 1001
    }
}
