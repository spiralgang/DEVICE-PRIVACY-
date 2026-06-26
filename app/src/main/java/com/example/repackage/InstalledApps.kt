package com.example.repackage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val apkPath: String,
    val isSystem: Boolean
)

/** Enumerates installed user apps that are candidates for repackaging. */
class InstalledApps(private val context: Context) {

    suspend fun list(includeSystem: Boolean = false): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map {
                    InstalledApp(
                        packageName = it.packageName,
                        label = pm.getApplicationLabel(it).toString(),
                        apkPath = it.sourceDir ?: "",
                        isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
}
