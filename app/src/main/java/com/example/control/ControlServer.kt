package com.example.control

import android.util.Log
import com.example.data.DeviceProfile
import com.example.data.PrivacyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Lightweight loopback control terminal for live, on-device modification of the
 * app without decompiling it. Binds to 127.0.0.1:[port] only.
 *
 * Reach it externally with:
 *   adb forward tcp:8765 tcp:8765
 *   nc localhost 8765        (or: telnet localhost 8765)
 *
 * Then issue line commands (type HELP). State changes are reflected live in the UI.
 */
class ControlServer(
    private val repository: PrivacyRepository,
    private val port: Int = DEFAULT_PORT
) {
    companion object {
        const val DEFAULT_PORT = 8765
        private const val TAG = "ControlServer"
        private const val BANNER =
            "== DEVICE-PRIVACY CONTROL TERMINAL ==\n" +
            "loopback control link established. type HELP for commands.\n"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    var lastError: String? = null
        private set

    fun start() {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch {
            try {
                val socket = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                Log.i(TAG, "Control terminal listening on 127.0.0.1:$port")
                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "accept failed: ${e.message}")
                        null
                    } ?: continue
                    scope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "Control server failed to bind on $port: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        scope.cancel()
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val writer = PrintWriter(sock.getOutputStream(), true)
            writer.print(BANNER)
            writer.print(prompt())
            writer.flush()
            while (true) {
                val line = reader.readLine() ?: break
                val cmd = line.trim()
                if (cmd.isEmpty()) {
                    writer.print(prompt()); writer.flush(); continue
                }
                if (cmd.equals("QUIT", true) || cmd.equals("EXIT", true)) {
                    writer.println("bye."); writer.flush(); break
                }
                val response = try {
                    dispatch(cmd)
                } catch (e: Exception) {
                    "[ERR] ${e.message}"
                }
                writer.println(response)
                writer.print(prompt())
                writer.flush()
            }
        }
    }

    private fun prompt() = "root@device-privacy:~# "

    private fun dispatch(input: String): String {
        val parts = input.split(Regex("\\s+"), limit = 2)
        val verb = parts[0].uppercase()
        val arg = parts.getOrNull(1)?.trim() ?: ""
        return when (verb) {
            "HELP" -> helpText()
            "STATUS" -> statusJson()
            "APPS" -> appsList()
            "SPOOF" -> {
                if (arg.isBlank()) return "usage: SPOOF <package>"
                val match = repository.targetApps.value.firstOrNull {
                    it.packageName.equals(arg, true) || it.appName.equals(arg, true)
                } ?: return "[ERR] unknown app: $arg"
                repository.toggleAppSpoofing(match.packageName)
                "toggled spoofing for ${match.appName}"
            }
            "SETMAC" -> setField(mac = arg)
            "SETIMEI" -> setField(imei = arg)
            "SETDEVICEID" -> setField(deviceId = arg)
            "USEMAC" -> setToggle(useMac = parseBool(arg))
            "USEIMEI" -> setToggle(useImei = parseBool(arg))
            "USEDEVICEID" -> setToggle(useDeviceId = parseBool(arg))
            "RANDOM" -> {
                repository.generateRandomSpoofedIds(); "generated random identifiers\n" + statusJson()
            }
            "RESET" -> {
                repository.resetToReal(); "reset to real identifiers"
            }
            "PROFILES" -> profilesList()
            "PROFILE" -> handleProfile(arg)
            "ADDON" -> handleAddon(arg)
            "EXPORT" -> exportJson()
            else -> "[ERR] unknown command: $verb (type HELP)"
        }
    }

    private fun parseBool(s: String) = s.equals("on", true) || s.equals("true", true) || s == "1"

    private fun setField(mac: String? = null, imei: String? = null, deviceId: String? = null): String {
        val cur = repository.hardwareIdentifiers.value
        repository.updateHardwareIds(
            useSpoofedMac = cur.useSpoofedMac,
            spoofedMac = mac ?: cur.spoofedMac,
            useSpoofedImei = cur.useSpoofedImei,
            spoofedImei = imei ?: cur.spoofedImei,
            useSpoofedDeviceId = cur.useSpoofedDeviceId,
            spoofedDeviceId = deviceId ?: cur.spoofedDeviceId
        )
        return "ok\n" + statusJson()
    }

    private fun setToggle(useMac: Boolean? = null, useImei: Boolean? = null, useDeviceId: Boolean? = null): String {
        val cur = repository.hardwareIdentifiers.value
        repository.updateHardwareIds(
            useSpoofedMac = useMac ?: cur.useSpoofedMac,
            spoofedMac = cur.spoofedMac,
            useSpoofedImei = useImei ?: cur.useSpoofedImei,
            spoofedImei = cur.spoofedImei,
            useSpoofedDeviceId = useDeviceId ?: cur.useSpoofedDeviceId,
            spoofedDeviceId = cur.spoofedDeviceId
        )
        return "ok\n" + statusJson()
    }

    private fun handleProfile(arg: String): String {
        val sub = arg.split(Regex("\\s+"), limit = 2)
        val action = sub[0].uppercase()
        val rest = sub.getOrNull(1)?.trim() ?: ""
        return when (action) {
            "ADD" -> {
                // format: manufacturer|model|board
                val f = rest.split("|").map { it.trim() }
                if (f.size != 3 || f.any { it.isBlank() }) {
                    return "usage: PROFILE ADD <manufacturer>|<model>|<board>"
                }
                repository.addDeviceProfile(DeviceProfile(f[0], f[1], f[2]))
                "added profile: ${f[1]}"
            }
            "SELECT" -> {
                val p = repository.deviceProfiles.value.firstOrNull { it.model.equals(rest, true) }
                    ?: return "[ERR] unknown profile: $rest"
                repository.selectProfile(p)
                "selected profile: ${p.model}"
            }
            else -> "usage: PROFILE ADD <mfg>|<model>|<board>  |  PROFILE SELECT <model>"
        }
    }

    private fun handleAddon(arg: String): String {
        if (arg.isBlank()) return "usage: ADDON <json>  e.g. {\"profiles\":[{\"manufacturer\":\"Sony\",\"model\":\"Xperia 1 VI\",\"board\":\"pdx234\"}],\"mac\":\"AA:BB:CC:DD:EE:FF\"}"
        val obj = JSONObject(arg)
        var added = 0
        obj.optJSONArray("profiles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                repository.addDeviceProfile(
                    DeviceProfile(
                        p.optString("manufacturer", "Custom"),
                        p.optString("model", "Model-$i"),
                        p.optString("board", "board")
                    )
                )
                added++
            }
        }
        val cur = repository.hardwareIdentifiers.value
        repository.updateHardwareIds(
            useSpoofedMac = obj.optBoolean("useMac", cur.useSpoofedMac),
            spoofedMac = obj.optString("mac", cur.spoofedMac),
            useSpoofedImei = obj.optBoolean("useImei", cur.useSpoofedImei),
            spoofedImei = obj.optString("imei", cur.spoofedImei),
            useSpoofedDeviceId = obj.optBoolean("useDeviceId", cur.useSpoofedDeviceId),
            spoofedDeviceId = obj.optString("deviceId", cur.spoofedDeviceId)
        )
        return "addon applied. profiles added: $added\n" + statusJson()
    }

    private fun statusJson(): String {
        val h = repository.hardwareIdentifiers.value
        return JSONObject().apply {
            put("useMac", h.useSpoofedMac); put("mac", h.spoofedMac)
            put("useImei", h.useSpoofedImei); put("imei", h.spoofedImei)
            put("useDeviceId", h.useSpoofedDeviceId); put("deviceId", h.spoofedDeviceId)
            put("selectedProfile", repository.selectedProfile.value?.model ?: "none")
        }.toString(2)
    }

    private fun appsList(): String =
        repository.targetApps.value.joinToString("\n") {
            "${if (it.isEnabledForSpoofing) "[x]" else "[ ]"} ${it.appName} (${it.packageName})"
        }

    private fun profilesList(): String =
        repository.deviceProfiles.value.joinToString("\n") { "- ${it.manufacturer} ${it.model} / ${it.board}" }

    private fun exportJson(): String {
        val h = repository.hardwareIdentifiers.value
        return JSONObject().apply {
            put("mac", h.spoofedMac); put("imei", h.spoofedImei); put("deviceId", h.spoofedDeviceId)
        }.toString(2)
    }

    private fun helpText(): String = """
        Commands:
          HELP                         show this help
          STATUS                       current spoof configuration (JSON)
          APPS                         list target apps and spoof state
          SPOOF <package|name>         toggle spoofing for an app
          SETMAC <value>               set spoofed MAC
          SETIMEI <value>              set spoofed IMEI
          SETDEVICEID <value>          set spoofed Android device id
          USEMAC <on|off>              enable/disable MAC spoofing
          USEIMEI <on|off>             enable/disable IMEI spoofing
          USEDEVICEID <on|off>         enable/disable device id spoofing
          RANDOM                       generate random identifiers
          RESET                        reset to real identifiers
          PROFILES                     list device profiles
          PROFILE ADD <mfg>|<model>|<board>   add an add-on device profile
          PROFILE SELECT <model>       select a device profile
          ADDON <json>                 bulk-load profiles + identifiers from JSON
          EXPORT                       export current identifiers (JSON)
          QUIT                         close the session
    """.trimIndent()
}
