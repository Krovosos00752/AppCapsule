package com.appcapsule.vault.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The admin component backing the isolated space. This app becomes the
 * *profile owner* of the managed profile it creates - it never requests
 * Device Owner, so it has no authority over the rest of the phone.
 */
class CapsuleDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "CapsuleDeviceAdmin"

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, CapsuleDeviceAdminReceiver::class.java)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    /**
     * Fires on Android versions that skip the DPC-first
     * GET_PROVISIONING_MODE / ADMIN_POLICY_COMPLIANCE handshake and hand
     * control back to us directly once the profile exists.
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = componentName(context)
        dpm.setProfileEnabled(admin)
        Log.i(TAG, "Isolated profile provisioning complete, profile enabled")
    }
}
