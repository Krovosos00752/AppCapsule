package com.appcapsule.vault.cross

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.appcapsule.vault.admin.CapsuleDeviceAdminReceiver

/**
 * Lives only inside the isolated profile, where this app is the profile
 * owner. The personal-side instance binds to it cross-profile
 * (DevicePolicyManager#bindDeviceAdminServiceAsUser) and asks it to pull an
 * already-installed personal app into the isolated space via
 * DevicePolicyManager#installExistingPackage - no re-download, no Play
 * Store session, no data ever crossing back out.
 */
class VaultBridgeService : Service() {

    companion object {
        private const val TAG = "VaultBridgeService"

        const val MSG_INSTALL_PACKAGE = 1
        const val MSG_INSTALL_RESULT = 2
        const val MSG_WIPE_VAULT = 3
        const val MSG_WIPE_RESULT = 4

        const val KEY_PACKAGE_NAME = "package_name"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR = "error"
    }

    private lateinit var messenger: Messenger

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_INSTALL_PACKAGE -> handleInstallRequest(msg)
                MSG_WIPE_VAULT -> handleWipeRequest(msg)
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(IncomingHandler(Looper.getMainLooper()))
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handleInstallRequest(msg: Message) {
        val packageName = msg.data?.getString(KEY_PACKAGE_NAME)
        val replyTo = msg.replyTo

        if (packageName.isNullOrBlank()) {
            reply(replyTo, MSG_INSTALL_RESULT, packageName ?: "", success = false, error = "missing package name")
            return
        }

        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = CapsuleDeviceAdminReceiver.componentName(this)

        if (!dpm.isProfileOwnerApp(packageName = this.packageName)) {
            reply(replyTo, MSG_INSTALL_RESULT, packageName, success = false, error = "not running as profile owner")
            return
        }

        try {
            val installed = dpm.installExistingPackage(admin, packageName)
            if (installed) {
                reply(replyTo, MSG_INSTALL_RESULT, packageName, success = true)
            } else {
                reply(replyTo, MSG_INSTALL_RESULT, packageName, success = false, error = "package not present on device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "installExistingPackage failed for $packageName", e)
            reply(replyTo, MSG_INSTALL_RESULT, packageName, success = false, error = e.message ?: "unknown error")
        }
    }

    private fun handleWipeRequest(msg: Message) {
        val replyTo = msg.replyTo
        val dpm = getSystemService(DevicePolicyManager::class.java)

        if (!dpm.isProfileOwnerApp(packageName)) {
            reply(replyTo, MSG_WIPE_RESULT, "", success = false, error = "not running as profile owner")
            return
        }

        try {
            // Wipes only this managed profile - the personal profile is untouched.
            dpm.wipeData(0)
            reply(replyTo, MSG_WIPE_RESULT, "", success = true)
        } catch (e: Exception) {
            Log.e(TAG, "wipeData failed", e)
            reply(replyTo, MSG_WIPE_RESULT, "", success = false, error = e.message ?: "unknown error")
        }
    }

    private fun reply(replyTo: Messenger?, what: Int, packageName: String, success: Boolean, error: String? = null) {
        if (replyTo == null) return
        val reply = Message.obtain(null, what).apply {
            data = Bundle().apply {
                putString(KEY_PACKAGE_NAME, packageName)
                putBoolean(KEY_SUCCESS, success)
                error?.let { putString(KEY_ERROR, it) }
            }
        }
        try {
            replyTo.send(reply)
        } catch (e: RemoteException) {
            Log.w(TAG, "Caller gone before reply could be sent", e)
        }
    }
}
