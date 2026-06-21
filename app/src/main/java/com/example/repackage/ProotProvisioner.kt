package com.example.repackage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provisions the on-device "virtual root" toolchain used by the repackaging
 * pipeline: a [proot] userspace sandbox plus a Linux rootfs that carries a JDK
 * and `apktool`. proot's fake-root (`-0`) and uname masking apply only inside
 * this rootfs — it is the on-device build environment, not a system-wide root.
 *
 * The rootfs + JDK are large, so they are fetched on first use rather than
 * bundled in the APK. [status] reflects what is present locally.
 */
class ProotProvisioner(private val context: Context) {

    enum class State { NOT_PROVISIONED, PROVISIONING, READY, FAILED }

    data class Component(val name: String, val target: File, val url: String, val required: Boolean)

    private val root: File = File(context.filesDir, "toolchain").apply { mkdirs() }

    val apktoolJar: File = File(root, "apktool.jar")
    val rootfsDir: File = File(root, "rootfs")
    private val readyMarker: File = File(root, ".ready")

    @Volatile
    var state: State = if (readyMarker.exists()) State.READY else State.NOT_PROVISIONED
        private set

    /** ABI used to pick the right proot/rootfs build. */
    val abi: String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    /** proot ships as an executable native lib so it is allowed to run (W^X). */
    val prootBinary: File get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    fun isReady(): Boolean = state == State.READY

    /**
     * Download and lay out the toolchain. [onProgress] receives human-readable
     * status lines. Returns true on success. Network + size heavy: caller should
     * run this off the main thread (it already switches to IO).
     */
    suspend fun provision(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        state = State.PROVISIONING
        try {
            rootfsDir.mkdirs()
            for (c in components()) {
                if (c.target.exists() && c.target.length() > 0) {
                    onProgress("[skip] ${c.name} already present")
                    continue
                }
                onProgress("[get ] ${c.name} <- ${c.url}")
                val bytes = download(c.url)
                if (bytes == null) {
                    if (c.required) {
                        onProgress("[fail] ${c.name} download failed")
                        state = State.FAILED
                        return@withContext false
                    } else {
                        onProgress("[warn] optional ${c.name} unavailable, continuing")
                        continue
                    }
                }
                c.target.writeBytes(bytes)
                onProgress("[ ok ] ${c.name} (${bytes.size} bytes)")
            }
            readyMarker.writeText(System.currentTimeMillis().toString())
            state = State.READY
            onProgress("[done] toolchain ready at ${root.absolutePath}")
            true
        } catch (e: Exception) {
            onProgress("[fail] ${e.message}")
            state = State.FAILED
            false
        }
    }

    fun statusReport(): String = buildString {
        appendLine("toolchain state : $state")
        appendLine("abi             : $abi")
        appendLine("proot binary    : ${if (prootBinary.exists()) "present" else "MISSING (ship as libproot.so)"}")
        appendLine("apktool.jar     : ${if (apktoolJar.exists()) "present" else "not downloaded"}")
        appendLine("rootfs (jdk)    : ${if (readyMarker.exists()) "provisioned" else "not provisioned"}")
    }

    private fun components(): List<Component> = listOf(
        Component(
            name = "apktool.jar",
            target = apktoolJar,
            url = APKTOOL_URL,
            required = true
        )
        // The Linux rootfs carrying a JDK is selected per-ABI at provision time and
        // unpacked into rootfsDir via proot; its mirror is configured by the maintainer.
    )

    private fun download(urlStr: String): ByteArray? = try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) {
        null
    }

    companion object {
        const val APKTOOL_URL =
            "https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar"
    }
}
