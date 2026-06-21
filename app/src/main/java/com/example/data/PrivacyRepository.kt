package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class AppModel(
    val packageName: String,
    val appName: String,
    val isEnabledForSpoofing: Boolean,
    val accessesHardwareIds: Boolean
)

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val board: String
)

data class HardwareIdentifiers(
    val useSpoofedMac: Boolean,
    val spoofedMac: String,
    val useSpoofedImei: Boolean,
    val spoofedImei: String,
    val useSpoofedDeviceId: Boolean,
    val spoofedDeviceId: String
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    var useSpoofedMac: Boolean
        get() = prefs.getBoolean("USE_SPOOFED_MAC", false)
        set(value) = prefs.edit().putBoolean("USE_SPOOFED_MAC", value).apply()

    var spoofedMac: String
        get() = prefs.getString("SPOOFED_MAC", "") ?: ""
        set(value) = prefs.edit().putString("SPOOFED_MAC", value).apply()

    var useSpoofedImei: Boolean
        get() = prefs.getBoolean("USE_SPOOFED_IMEI", false)
        set(value) = prefs.edit().putBoolean("USE_SPOOFED_IMEI", value).apply()

    var spoofedImei: String
        get() = prefs.getString("SPOOFED_IMEI", "") ?: ""
        set(value) = prefs.edit().putString("SPOOFED_IMEI", value).apply()

    var useSpoofedDeviceId: Boolean
        get() = prefs.getBoolean("USE_SPOOFED_DEVICE_ID", false)
        set(value) = prefs.edit().putBoolean("USE_SPOOFED_DEVICE_ID", value).apply()

    var spoofedDeviceId: String
        get() = prefs.getString("SPOOFED_DEVICE_ID", "") ?: ""
        set(value) = prefs.edit().putString("SPOOFED_DEVICE_ID", value).apply()
}

class PrivacyRepository(context: Context) {
    private val prefsManager = PreferencesManager(context)

    private val _targetApps = MutableStateFlow(
        listOf(
            AppModel("com.snapchat.android", "Snapchat", false, true),
            AppModel("com.facebook.katana", "Facebook", false, true),
            AppModel("com.instagram.android", "Instagram", false, true),
            AppModel("com.whatsapp", "WhatsApp", false, false),
            AppModel("com.spotify.music", "Spotify", false, false)
        )
    )
    val targetApps: StateFlow<List<AppModel>> = _targetApps.asStateFlow()

    private val _deviceProfiles = MutableStateFlow(
        listOf(
            DeviceProfile("Google", "Pixel 8 Pro", "husky"),
            DeviceProfile("Samsung", "Galaxy S24 Ultra", "e3q"),
            DeviceProfile("OnePlus", "OnePlus 12", "op12")
        )
    )
    val deviceProfiles: StateFlow<List<DeviceProfile>> = _deviceProfiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<DeviceProfile?>(null)
    val selectedProfile: StateFlow<DeviceProfile?> = _selectedProfile.asStateFlow()

    private val _hardwareIdentifiers = MutableStateFlow(
        HardwareIdentifiers(
            prefsManager.useSpoofedMac,
            prefsManager.spoofedMac,
            prefsManager.useSpoofedImei,
            prefsManager.spoofedImei,
            prefsManager.useSpoofedDeviceId,
            prefsManager.spoofedDeviceId
        )
    )
    val hardwareIdentifiers: StateFlow<HardwareIdentifiers> = _hardwareIdentifiers.asStateFlow()

    private val _history = MutableStateFlow<List<HardwareIdentifiers>>(emptyList())
    val history: StateFlow<List<HardwareIdentifiers>> = _history.asStateFlow()

    @Synchronized
    fun toggleAppSpoofing(packageName: String) {
        _targetApps.value = _targetApps.value.map {
            if (it.packageName == packageName) it.copy(isEnabledForSpoofing = !it.isEnabledForSpoofing) else it
        }
    }

    fun selectProfile(profile: DeviceProfile) {
        _selectedProfile.value = profile
    }

    @Synchronized
    fun addDeviceProfile(profile: DeviceProfile) {
        if (_deviceProfiles.value.none { it.model.equals(profile.model, ignoreCase = true) }) {
            _deviceProfiles.value = _deviceProfiles.value + profile
        }
    }

    @Synchronized
    fun updateHardwareIds(
        useSpoofedMac: Boolean,
        spoofedMac: String,
        useSpoofedImei: Boolean,
        spoofedImei: String,
        useSpoofedDeviceId: Boolean,
        spoofedDeviceId: String
    ) {
        prefsManager.useSpoofedMac = useSpoofedMac
        if (spoofedMac.isNotBlank()) prefsManager.spoofedMac = spoofedMac
        prefsManager.useSpoofedImei = useSpoofedImei
        if (spoofedImei.isNotBlank()) prefsManager.spoofedImei = spoofedImei
        prefsManager.useSpoofedDeviceId = useSpoofedDeviceId
        if (spoofedDeviceId.isNotBlank()) prefsManager.spoofedDeviceId = spoofedDeviceId
        
        val newId = HardwareIdentifiers(
            useSpoofedMac,
            prefsManager.spoofedMac,
            useSpoofedImei,
            prefsManager.spoofedImei,
            useSpoofedDeviceId,
            prefsManager.spoofedDeviceId
        )
        _hardwareIdentifiers.value = newId
        
        val currentHistory = _history.value.toMutableList()
        currentHistory.add(0, newId)
        if (currentHistory.size > 5) currentHistory.removeLast()
        _history.value = currentHistory
    }
    
    @Synchronized
    fun resetToReal() {
        prefsManager.useSpoofedMac = false
        prefsManager.useSpoofedImei = false
        prefsManager.useSpoofedDeviceId = false
        
        _hardwareIdentifiers.value = _hardwareIdentifiers.value.copy(
            useSpoofedMac = false,
            useSpoofedImei = false,
            useSpoofedDeviceId = false
        )
    }

    fun generateRandomSpoofedIds() {
        val randMac = (1..6).joinToString(":") { String.format("%02X", Random.nextInt(256)) }
        val randImei = (1..15).joinToString("") { Random.nextInt(10).toString() }
        val randDeviceId = (1..16).joinToString("") { "%x".format(Random.nextInt(16)) }
        
        updateHardwareIds(
            _hardwareIdentifiers.value.useSpoofedMac,
            randMac,
            _hardwareIdentifiers.value.useSpoofedImei,
            randImei,
            _hardwareIdentifiers.value.useSpoofedDeviceId,
            randDeviceId
        )
    }
}
