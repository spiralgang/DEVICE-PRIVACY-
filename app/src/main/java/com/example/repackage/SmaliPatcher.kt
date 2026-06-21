package com.example.repackage

import java.io.File

data class PatchReport(
    val androidIdSites: Int,
    val imeiSites: Int,
    val macSites: Int
) {
    val totalPatched: Int get() = androidIdSites + imeiSites + macSites
    fun summary(): String =
        "patched android_id=$androidIdSites, imei=$imeiSites, mac=$macSites sites"
}

/**
 * Injects spoofed identifier values into a decompiled smali tree, the exact
 * technique proven on the emulator: after the system call that reads an
 * identifier returns, overwrite the result register with a `const-string` of
 * the spoofed value, so the rebuilt app reads our value instead of the real one.
 */
object SmaliPatcher {

    private const val ANDROID_ID_CALL =
        "Landroid/provider/Settings\$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;"
    private val IMEI_CALLS = listOf(
        "Landroid/telephony/TelephonyManager;->getImei()Ljava/lang/String;",
        "Landroid/telephony/TelephonyManager;->getImei(I)Ljava/lang/String;",
        "Landroid/telephony/TelephonyManager;->getDeviceId()Ljava/lang/String;",
        "Landroid/telephony/TelephonyManager;->getDeviceId(I)Ljava/lang/String;"
    )
    private const val MAC_CALL =
        "Landroid/net/wifi/WifiInfo;->getMacAddress()Ljava/lang/String;"

    private val MOVE_RESULT = Regex("""^\s*move-result-object\s+([vp]\d+)\s*$""")

    fun patch(decompiledDir: File, spec: SpoofSpec): PatchReport {
        var aid = 0; var imei = 0; var mac = 0
        decompiledDir.walkTopDown()
            .filter { it.isFile && it.extension == "smali" }
            .forEach { f ->
                val lines = f.readLines().toMutableList()
                var changed = false
                var i = 0
                while (i < lines.size) {
                    val line = lines[i]
                    when {
                        spec.androidId != null && line.contains(ANDROID_ID_CALL) &&
                            precededByAndroidIdConst(lines, i) -> {
                            if (injectAfterResult(lines, i, spec.androidId)) { aid++; changed = true; i++ }
                        }
                        spec.imei != null && IMEI_CALLS.any { line.contains(it) } -> {
                            if (injectAfterResult(lines, i, spec.imei)) { imei++; changed = true; i++ }
                        }
                        spec.mac != null && line.contains(MAC_CALL) -> {
                            if (injectAfterResult(lines, i, spec.mac)) { mac++; changed = true; i++ }
                        }
                    }
                    i++
                }
                if (changed) f.writeText(lines.joinToString("\n") + "\n")
            }
        return PatchReport(aid, imei, mac)
    }

    /** ANDROID_ID is read by `getString(resolver, "android_id")`; confirm the key. */
    private fun precededByAndroidIdConst(lines: List<String>, callIdx: Int): Boolean {
        val from = (callIdx - 6).coerceAtLeast(0)
        for (j in callIdx downTo from) {
            if (lines[j].contains("\"android_id\"")) return true
        }
        return false
    }

    /**
     * Find the `move-result-object vN` that follows the call at [callIdx] and
     * insert `const-string vN, "value"` right after it, overwriting the result.
     * Returns true if an insertion was made.
     */
    private fun injectAfterResult(lines: MutableList<String>, callIdx: Int, value: String): Boolean {
        var j = callIdx + 1
        while (j < lines.size && lines[j].isBlank()) j++
        if (j >= lines.size) return false
        val m = MOVE_RESULT.find(lines[j]) ?: return false
        val reg = m.groupValues[1]
        val indent = lines[j].takeWhile { it == ' ' || it == '\t' }
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        lines.add(j + 1, "")
        lines.add(j + 2, "$indent const-string $reg, \"$escaped\"")
        return true
    }
}
