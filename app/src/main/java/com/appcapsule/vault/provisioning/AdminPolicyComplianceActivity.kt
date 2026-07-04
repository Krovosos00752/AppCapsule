package com.appcapsule.vault.provisioning

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import com.appcapsule.vault.admin.CapsuleDeviceAdminReceiver

/**
 * Last stop in the Android 12+ DPC-first provisioning wizard. The isolated
 * profile already exists by the time this runs; we just label it and hand
 * control back to the system, which returns the user to their home screen.
 */
class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = CapsuleDeviceAdminReceiver.componentName(this)

        if (dpm.isProfileOwnerApp(packageName)) {
            dpm.setProfileEnabled(admin)
            dpm.setProfileName(admin, "AppCapsule")
        }

        setResult(Activity.RESULT_OK)
        finish()
    }
}
