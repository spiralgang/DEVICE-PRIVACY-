package com.example.repackage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Outcome of a single backend attempt. */
data class BackendResult(val apk: File?, val detail: String) {
    val ok: Boolean get() = apk != null
}

/**
 * A strategy that turns a staged target APK + [SpoofSpec] into a signed clone.
 * The pipeline holds several and tries each in order ("maximum diversity, highest
 * chance of success"), using whichever first succeeds.
 */
interface BuildBackend {
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun build(projectDir: File, stagedApk: File, spec: SpoofSpec, log: (String) -> Unit): BackendResult
}

/**
 * Routes the heavy decompile/patch/rebuild/sign to a remote Linux worker
 * (Google Cloud Shell / GCE / any online machine running apktool + a JDK).
 * Most reliable path because it sidesteps the on-device JDK problem entirely.
 *
 * Protocol: multipart POST of the APK + spoof fields; the worker returns the
 * signed clone as the response body (application/vnd.android.package-archive).
 */
class RemoteBuildBackend(private val endpointProvider: () -> String) : BuildBackend {
    override val name = "remote-linux"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    override suspend fun isAvailable(): Boolean = endpointProvider().isNotBlank()

    override suspend fun build(
        projectDir: File,
        stagedApk: File,
        spec: SpoofSpec,
        log: (String) -> Unit
    ): BackendResult = withContext(Dispatchers.IO) {
        val endpoint = endpointProvider()
        if (endpoint.isBlank()) return@withContext BackendResult(null, "no remote endpoint configured")
        log("[remote] uploading ${stagedApk.length()} bytes to $endpoint")
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("apk", "target.apk", stagedApk.asRequestBody())
                .apply {
                    spec.androidId?.let { addFormDataPart("androidId", it) }
                    spec.imei?.let { addFormDataPart("imei", it) }
                    spec.mac?.let { addFormDataPart("mac", it) }
                }
                .build()
            val req = Request.Builder().url(endpoint).post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext BackendResult(null, "remote HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val bytes = resp.body?.bytes() ?: return@withContext BackendResult(null, "empty remote response")
                val out = File(projectDir, "clone.apk")
                out.writeBytes(bytes)
                log("[remote] received signed clone ${out.length()} bytes")
                BackendResult(out, "built remotely on Linux worker")
            }
        } catch (e: Exception) {
            BackendResult(null, "remote backend error: ${e.message}")
        }
    }

    @Suppress("unused")
    private fun stringPart(value: String) = value.toRequestBody()
}

/**
 * On-device path: decompile → patch smali → rebuild → sign, all inside the
 * proot rootfs (a JDK + apktool). Works offline; requires the toolchain to be
 * provisioned first.
 */
class ProotBuildBackend(
    private val shell: ShellEngine,
    private val provisioner: ProotProvisioner,
    private val workspace: File
) : BuildBackend {
    override val name = "proot-ondevice"

    override suspend fun isAvailable(): Boolean = provisioner.isReady() && provisioner.prootBinary.exists()

    override suspend fun build(
        projectDir: File,
        stagedApk: File,
        spec: SpoofSpec,
        log: (String) -> Unit
    ): BackendResult {
        val decompiled = File(projectDir, "src")
        val d = shell.exec(decompileCmd(stagedApk, decompiled))
        if (!d.ok || !decompiled.exists()) return BackendResult(null, "decompile failed: ${d.output.takeLast(200)}")
        log("[proot] decompiled")

        val patch = SmaliPatcher.patch(decompiled, spec)
        if (patch.totalPatched == 0) return BackendResult(null, "no ID read sites found (${patch.summary()})")
        log("[proot] ${patch.summary()}")

        val rebuilt = File(projectDir, "rebuilt.apk")
        val b = shell.exec(rebuildCmd(decompiled, rebuilt))
        if (!b.ok || !rebuilt.exists()) return BackendResult(null, "rebuild failed: ${b.output.takeLast(200)}")
        log("[proot] rebuilt")

        val signed = File(projectDir, "clone.apk")
        val s = shell.exec(signCmd(rebuilt, signed, File(workspace, "debug.keystore")))
        if (!s.ok || !signed.exists()) return BackendResult(null, "sign failed: ${s.output.takeLast(200)}")
        log("[proot] signed")
        return BackendResult(signed, "built on-device via proot (${patch.summary()})")
    }

    private fun proot(inner: String): String {
        val p = provisioner.prootBinary.absolutePath
        val rootfs = provisioner.rootfsDir.absolutePath
        return "$p -0 -r $rootfs -b ${workspace.absolutePath}:/work -w /work " +
            "/usr/bin/env PATH=/usr/bin:/bin sh -c '$inner'"
    }

    private fun rel(f: File): String = "/work/" + f.relativeTo(workspace).path
    private fun decompileCmd(apk: File, out: File) = proot("java -jar /opt/apktool.jar d -f -o ${rel(out)} ${rel(apk)}")
    private fun rebuildCmd(src: File, out: File) = proot("java -jar /opt/apktool.jar b ${rel(src)} -o ${rel(out)}")
    private fun signCmd(input: File, output: File, keystore: File) = proot(
        "if [ ! -f ${rel(keystore)} ]; then keytool -genkeypair -keystore ${rel(keystore)} " +
            "-alias d -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname CN=DevicePrivacy; fi; " +
            "apksigner sign --ks ${rel(keystore)} --ks-pass pass:android --key-pass pass:android --out ${rel(output)} ${rel(input)}"
    )
}
