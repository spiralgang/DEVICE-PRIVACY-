package com.example.repackage

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs a (re)packaged APK via the [PackageInstaller] session API. The system
 * shows the user a confirmation prompt — installation is always user-consented,
 * which is the "when prompted yes by user" gate.
 */
class ApkInstaller(private val context: Context) {

    companion object {
        const val ACTION_INSTALL_RESULT = "com.example.repackage.INSTALL_RESULT"
    }

    suspend fun install(apk: File): String = withContext(Dispatchers.IO) {
        if (!apk.exists()) return@withContext "[ERR] apk not found: ${apk.absolutePath}"
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
        "install session committed (id=$sessionId) — confirm the system prompt"
    }
}
