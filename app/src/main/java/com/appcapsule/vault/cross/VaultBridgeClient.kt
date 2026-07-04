package com.appcapsule.vault.cross

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.UserHandle
import com.appcapsule.vault.admin.CapsuleDeviceAdminReceiver

/** Result of a single request made through [VaultBridgeClient]. */
sealed class VaultBridgeResult {
    data class Success(val packageName: String) : VaultBridgeResult()
    data class Failure(val packageName: String, val reason: String) : VaultBridgeResult()
}

/**
 * Personal-side helper that opens a short-lived cross-profile binding to
 * [VaultBridgeService] running inside the isolated space, sends one
 * request, waits for the reply, then tears the binding down. Call from a
 * background thread - binding + reply round-trip is not instant.
 */
class VaultBridgeClient(private val context: Context) {

    private fun serviceIntent(): Intent =
        Intent("com.appcapsule.vault.action.BIND_VAULT_BRIDGE").apply {
            setPackage(context.packageName)
        }

    private fun bindAndSend(vaultUser: UserHandle, request: Message, timeoutMs: Long = 15_000): VaultBridgeResult {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = CapsuleDeviceAdminReceiver.componentName(context)

        val resultBox = java.util.concurrent.SynchronousQueue<VaultBridgeResult>()
        var messenger: Messenger? = null

        val replyHandler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
            val pkg = msg.data?.getString(VaultBridgeService.KEY_PACKAGE_NAME).orEmpty()
            val success = msg.data?.getBoolean(VaultBridgeService.KEY_SUCCESS) ?: false
            val error = msg.data?.getString(VaultBridgeService.KEY_ERROR)
            val result = if (success) {
                VaultBridgeResult.Success(pkg)
            } else {
                VaultBridgeResult.Failure(pkg, error ?: "unknown error")
            }
            resultBox.offer(result)
            true
        }
        val replyMessenger = Messenger(replyHandler)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                messenger = Messenger(binder)
                try {
                    request.replyTo = replyMessenger
                    messenger?.send(request)
                } catch (e: RemoteException) {
                    resultBox.offer(VaultBridgeResult.Failure("", "bridge send failed: ${e.message}"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                messenger = null
            }
        }

        val bound = dpm.bindDeviceAdminServiceAsUser(
            admin,
            serviceIntent(),
            connection,
            Context.BIND_AUTO_CREATE,
            vaultUser
        )

        if (!bound) {
            return VaultBridgeResult.Failure("", "could not bind to isolated space - is it set up?")
        }

        return try {
            resultBox.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                ?: VaultBridgeResult.Failure("", "timed out waiting for isolated space")
        } finally {
            dpm.unbindDeviceAdminServiceAsUser(admin, connection, vaultUser)
        }
    }

    /** Pulls [packageName] (already installed in the personal profile) into the isolated space. */
    fun installIntoVault(vaultUser: UserHandle, packageName: String): VaultBridgeResult {
        val request = Message.obtain(null, VaultBridgeService.MSG_INSTALL_PACKAGE).apply {
            data = Bundle().apply { putString(VaultBridgeService.KEY_PACKAGE_NAME, packageName) }
        }
        return bindAndSend(vaultUser, request)
    }

    /** Wipes the isolated space. The personal profile is never touched. */
    fun wipeVault(vaultUser: UserHandle): VaultBridgeResult {
        val request = Message.obtain(null, VaultBridgeService.MSG_WIPE_VAULT)
        return bindAndSend(vaultUser, request)
    }
}
