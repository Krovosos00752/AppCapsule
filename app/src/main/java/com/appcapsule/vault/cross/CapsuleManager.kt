package com.appcapsule.vault.cross

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.UserHandle
import com.appcapsule.vault.admin.CapsuleDeviceAdminReceiver

/** Where this running instance of the app stands relative to the isolated space. */
sealed class VaultState {
    /** Running on the personal side, no isolated space exists yet. */
    object NotCreated : VaultState()

    /** Running on the personal side; the isolated space exists at [vaultUser]. */
    data class ReadyFromPersonalSide(val vaultUser: UserHandle) : VaultState()

    /** Running as the profile owner *inside* the isolated space itself. */
    object RunningInsideVault : VaultState()
}

/** Central facade for creating, inspecting, and populating the isolated space. */
class CapsuleManager(private val context: Context) {

    private val dpm: DevicePolicyManager
        get() = context.getSystemService(DevicePolicyManager::class.java)

    private val crossProfileApps: CrossProfileApps
        get() = context.getSystemService(CrossProfileApps::class.java)

    fun currentState(): VaultState {
        if (dpm.isProfileOwnerApp(context.packageName)) {
            return VaultState.RunningInsideVault
        }
        val vaultUser = crossProfileApps.targetUserProfiles.firstOrNull()
        return if (vaultUser != null) {
            VaultState.ReadyFromPersonalSide(vaultUser)
        } else {
            VaultState.NotCreated
        }
    }

    /** Intent that kicks off the system's managed-profile provisioning wizard. */
    fun buildCreateVaultIntent(): Intent {
        val admin = CapsuleDeviceAdminReceiver.componentName(context)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
        }
    }

    // We deliberately do not launch arbitrary third-party apps cross-profile
    // ourselves: CrossProfileApps.startMainActivity is only permitted for the
    // caller's own package unless the caller holds the default-launcher role.
    // Once an app is cloned into the vault, the phone's own launcher already
    // shows a badged icon for it in the app drawer / "Work" tab, and opens it
    // using the launcher's elevated LauncherApps access.
}
