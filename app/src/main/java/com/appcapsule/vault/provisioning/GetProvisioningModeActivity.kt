package com.appcapsule.vault.provisioning

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/**
 * Android 12+ asks the DPC which provisioning mode it wants before creating
 * anything. AppCapsule only ever offers one answer: a managed *profile*
 * (never full device ownership), which is what keeps the rest of the phone
 * untouched.
 */
class GetProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = Intent().apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            )
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
