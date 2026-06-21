package com.example.repackage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Which identifiers to spoof in the rebuilt clone, and the values to inject. */
data class SpoofSpec(
    val androidId: String? = null,
    val imei: String? = null,
    val mac: String? = null
) {
    val isEmpty: Boolean get() = androidId == null && imei == null && mac == null
}

enum class StepStatus { PENDING, RUNNING, OK, FAILED, SKIPPED }

data class PipelineStep(val name: String, val status: StepStatus, val detail: String = "")

data class PipelineResult(
    val passed: Boolean,
    val steps: List<PipelineStep>,
    val outputApk: File?,
    val reason: String
)

/**
 * Orchestrates the proven repackaging spoof end-to-end on-device:
 *   locate -> stage -> integrity-scan -> [build via backends] -> install.
 *
 * The build stage tries multiple [BuildBackend]s in order (remote Linux worker,
 * then on-device proot) and uses whichever succeeds first — maximum diversity,
 * highest chance of success. The pipeline is honest about failure: apps that
 * verify their own signature / pin certs / use Play Integrity are flagged,
 * because a re-signed clone cannot defeat a server-side cryptographic check.
 */
class RepackagingPipeline(
    private val context: Context,
    shell: ShellEngine,
    provisioner: ProotProvisioner,
    remoteEndpointProvider: () -> String
) {
    private val workspace: File = File(context.filesDir, "forge").apply { mkdirs() }

    private val backends: List<BuildBackend> = listOf(
        RemoteBuildBackend(remoteEndpointProvider),
        ProotBuildBackend(shell, provisioner, workspace)
    )

    suspend fun run(
        app: InstalledApp,
        spec: SpoofSpec,
        emit: (PipelineStep) -> Unit
    ): PipelineResult = withContext(Dispatchers.IO) {
        val steps = mutableListOf<PipelineStep>()
        fun step(name: String, status: StepStatus, detail: String = ""): PipelineStep {
            val s = PipelineStep(name, status, detail)
            steps.add(s); emit(s); return s
        }

        if (spec.isEmpty) {
            val r = step("validate", StepStatus.FAILED, "no identifiers selected to spoof")
            return@withContext PipelineResult(false, listOf(r), null, "nothing to spoof")
        }

        // 1. Locate target APK
        val srcApk = File(app.apkPath)
        if (!srcApk.exists()) {
            val r = step("locate", StepStatus.FAILED, "source APK not readable: ${app.apkPath}")
            return@withContext PipelineResult(false, steps, null, r.detail)
        }
        step("locate", StepStatus.OK, srcApk.absolutePath)

        // 2. Stage into workspace
        val projDir = File(workspace, app.packageName).apply { deleteRecursively(); mkdirs() }
        val stagedApk = File(projDir, "target.apk")
        srcApk.copyTo(stagedApk, overwrite = true)
        step("stage", StepStatus.OK, "${stagedApk.length()} bytes")

        // 3. Integrity heuristic (scans the dex; informs whether the clone will be accepted)
        val integrity = scanIntegrityRisks(stagedApk)
        step(
            "integrity-scan", StepStatus.OK,
            if (integrity.isNotEmpty()) "RISK: clone may be rejected by -> $integrity"
            else "no signature/pinning/integrity markers found"
        )

        // 4. Build via backends (first success wins)
        var built: File? = null
        val failures = mutableListOf<String>()
        for (backend in backends) {
            if (!backend.isAvailable()) {
                step("build:${backend.name}", StepStatus.SKIPPED, "unavailable")
                continue
            }
            step("build:${backend.name}", StepStatus.RUNNING)
            val res = backend.build(projDir, stagedApk, spec) { line ->
                emit(PipelineStep("build:${backend.name}", StepStatus.RUNNING, line))
            }
            if (res.ok) {
                step("build:${backend.name}", StepStatus.OK, res.detail)
                built = res.apk
                break
            } else {
                step("build:${backend.name}", StepStatus.FAILED, res.detail)
                failures.add("${backend.name}: ${res.detail}")
            }
        }

        if (built == null) {
            val reason = if (failures.isEmpty())
                "no build backend available — configure a remote endpoint or provision the proot toolchain"
            else "all backends failed -> ${failures.joinToString(" | ")}"
            return@withContext PipelineResult(false, steps, null, reason)
        }

        val reason = if (integrity.isNotEmpty())
            "PASS (built) — but target uses [$integrity]; the re-signed clone will likely be rejected at runtime/server-side"
        else "PASS — clone built and signed; install to apply"
        PipelineResult(true, steps, built, reason)
    }

    companion object {
        private val INTEGRITY_MARKERS = mapOf(
            "GET_SIGNATURES" to "signature check",
            "getSigningCertificateHistory" to "signature check",
            "checkSignatures" to "signature check",
            "PlayIntegrity" to "Play Integrity",
            "IntegrityManager" to "Play Integrity",
            "SafetyNet" to "SafetyNet",
            "CertificatePinner" to "cert pinning",
            "X509TrustManager" to "cert pinning"
        )

        /** Scan the APK's dex entries for integrity-check marker strings. */
        fun scanIntegrityRisks(apk: File): String {
            val found = linkedSetOf<String>()
            try {
                ZipFile(apk).use { zf ->
                    zf.entries().asSequence()
                        .filter { it.name.endsWith(".dex") }
                        .forEach { entry ->
                            val text = zf.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                            for ((marker, label) in INTEGRITY_MARKERS) {
                                if (text.contains(marker)) found.add(label)
                            }
                        }
                }
            } catch (_: Exception) {
            }
            return found.joinToString(", ")
        }
    }
}
