package com.appcapsule.vault.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

/** Lists user-launchable apps installed in the *current* profile. */
class PersonalAppsRepository(private val context: Context) {

    fun listLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

        return resolveInfos
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { appInfo: ApplicationInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
