package com.example.repackage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Real on-device shell, backed by [ProcessBuilder]. Unlike the cosmetic
 * `root@device-privacy` prompt in [com.example.control.ControlServer], this
 * actually executes commands in the app's own sandbox (its private files dir).
 *
 * It runs unprivileged as the app UID — exactly the "virtual root / near edge"
 * environment available without a genuinely rooted device. Commands are confined
 * to what the app process is allowed to do; this is what the proot toolchain and
 * the repackaging pipeline drive on-device.
 */
class ShellEngine(context: Context) {

    private val workDir: File = File(context.filesDir, "shell").apply { mkdirs() }

    /** Native lib dir holds executables we ship as `lib*.so` (proot, busybox, …). */
    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir

    val homePath: String get() = workDir.absolutePath

    /** A real prompt reflecting the app-sandbox shell (not a cosmetic string). */
    fun homePathPrompt(): String = "device-privacy:${workDir.name} $ "

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /**
     * Execute [command] via `/system/bin/sh -c` and collect combined output.
     * [onLine] is invoked for each output line as it arrives (for live terminals).
     */
    suspend fun exec(command: String, onLine: ((String) -> Unit)? = null): Result =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            try {
                val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(workDir)
                    .redirectErrorStream(true)
                pb.environment().apply {
                    put("HOME", workDir.absolutePath)
                    put("TMPDIR", workDir.absolutePath)
                    put("PREFIX", nativeLibDir)
                    put("PATH", "$nativeLibDir:/system/bin:/system/xbin:" + (get("PATH") ?: ""))
                }
                val process = pb.start()
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        sb.append(line).append('\n')
                        onLine?.invoke(line)
                        line = reader.readLine()
                    }
                }
                val code = process.waitFor()
                Result(code, sb.toString())
            } catch (e: Exception) {
                Result(127, sb.toString() + "[shell error] ${e.message}\n")
            }
        }

    /** A one-line probe of the live environment (verifies this is a real shell). */
    suspend fun environmentReport(): String {
        val cmd = "echo \"uid: \$(id -u 2>/dev/null) ($(id -un 2>/dev/null))\"; " +
            "echo \"kernel: \$(uname -srm 2>/dev/null)\"; " +
            "echo \"abi: \$(getprop ro.product.cpu.abi 2>/dev/null)\"; " +
            "echo \"home: $homePath\"; " +
            "echo \"toolchain dir: $nativeLibDir\""
        return exec(cmd).output
    }
}
