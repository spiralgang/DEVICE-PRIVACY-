package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.control.ControlServer
import com.example.data.PrivacyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.api.MistralMessage
import com.example.api.MistralRequest
import com.example.api.MistralRetrofitClient
import com.example.api.NvidiaMessage
import com.example.api.NvidiaRequest
import com.example.api.NvidiaRetrofitClient

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PrivacyRepository(application)
    private val controlServer = ControlServer(repository).also { it.start() }

    val controlPort: Int = ControlServer.DEFAULT_PORT

    val targetApps = repository.targetApps
    val deviceProfiles = repository.deviceProfiles
    val selectedProfile = repository.selectedProfile
    val hardwareIdentifiers = repository.hardwareIdentifiers
    val history = repository.history

    private val _aiAnalysisOutput = MutableStateFlow("Tap the Analyze button below to run the AI FSM bot check.")
    val aiAnalysisOutput: StateFlow<String> = _aiAnalysisOutput.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    private val _mistralOutput = MutableStateFlow("> Edge terminal ready.\n> Connect to Mistral node via termux emulation...")
    val mistralOutput: StateFlow<String> = _mistralOutput.asStateFlow()

    private val _isMistralLoading = MutableStateFlow(false)
    val isMistralLoading: StateFlow<Boolean> = _isMistralLoading.asStateFlow()

    fun runMistralCommand(command: String) {
        viewModelScope.launch {
            _isMistralLoading.value = true
            val currentText = _mistralOutput.value
            _mistralOutput.value = "$currentText\n\n> $command"
            
            try {
                val apiKey = BuildConfig.MISTRAL_API_KEY
                if (apiKey.isEmpty()) {
                    _mistralOutput.value = _mistralOutput.value + "\n[ERR] MISTRAL_API_KEY not found in secrets."
                    return@launch
                }
                
                val prompt = "You are a terminal-based AI FSM assistant connected via Termux overlay to solve coding and privacy/spoofing issues. User command:\n$command"
                
                val request = MistralRequest(
                    messages = listOf(MistralMessage(role = "user", content = prompt))
                )
                
                val response = MistralRetrofitClient.service.generateContent(
                    authHeader = "Bearer $apiKey",
                    request = request
                )
                val reply = response.choices.firstOrNull()?.message?.content ?: "No response returned."
                _mistralOutput.value = _mistralOutput.value + "\n\n$reply"
                
            } catch (e: Exception) {
                _mistralOutput.value = _mistralOutput.value + "\n[ERR] Execution failed: ${e.message}"
            } finally {
                _isMistralLoading.value = false
            }
        }
    }

    val privacyRiskScore: Int
        get() {
            val highRiskCount = targetApps.value.count { it.accessesHardwareIds && !it.isEnabledForSpoofing }
            return 100 - (highRiskCount * 20).coerceIn(0, 100)
        }

    fun toggleAppSpoofing(packageName: String) {
        repository.toggleAppSpoofing(packageName)
    }

    fun selectProfile(profile: com.example.data.DeviceProfile) {
        repository.selectProfile(profile)
    }

    fun updateHardwareIds(
        useSpoofedMac: Boolean, spoofedMac: String,
        useSpoofedImei: Boolean, spoofedImei: String,
        useSpoofedDeviceId: Boolean, spoofedDeviceId: String
    ) {
        repository.updateHardwareIds(useSpoofedMac, spoofedMac, useSpoofedImei, spoofedImei, useSpoofedDeviceId, spoofedDeviceId)
    }

    fun generateRandomSpoofedIds() {
        repository.generateRandomSpoofedIds()
    }

    override fun onCleared() {
        super.onCleared()
        controlServer.stop()
    }
    
    fun resetToReal() {
        repository.resetToReal()
    }

    fun getExportJson(): String {
        val exportData = org.json.JSONObject()
        val current = hardwareIdentifiers.value
        
        val curObj = org.json.JSONObject().apply {
            put("useMac", current.useSpoofedMac)
            put("mac", current.spoofedMac)
            put("useImei", current.useSpoofedImei)
            put("imei", current.spoofedImei)
            put("useDeviceId", current.useSpoofedDeviceId)
            put("deviceId", current.spoofedDeviceId)
        }
        exportData.put("currentComponent", curObj)
        
        val histArr = org.json.JSONArray()
        history.value.forEach { 
            val hItem = org.json.JSONObject().apply {
                put("mac", it.spoofedMac)
                put("imei", it.spoofedImei)
                put("deviceId", it.spoofedDeviceId)
            }
            histArr.put(hItem)
        }
        exportData.put("history", histArr)
        
        return exportData.toString(4)
    }

    fun runAiAnalysis() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _aiAnalysisOutput.value = "Analyzing current configuration with NVIDIA Llama 3.3 70B..."
            
            val apps = targetApps.value
            val profile = selectedProfile.value
            val spoofedAppsCount = apps.count { it.isEnabledForSpoofing }
            
            val prompt = """
                You are a privacy expert AI assistant (AI FSM Bot). Assess the user's current device spoofing config:
                - Privacy Score: ${privacyRiskScore}/100
                - Spoofed Target Apps: $spoofedAppsCount out of ${apps.size}
                - Selected Mock Device Profile: ${profile?.model ?: "Unknown"} by ${profile?.manufacturer ?: "Unknown"}
                
                Simulate a Play Integrity / SafetyNet check based on this profile and tell the user if their spoofed configuration is likely to trigger detection mechanisms in high-security apps like Snapchat. Provide a short, 3-4 sentence risk report and analyze the implications of spoofing high-risk identifiers.
            """.trimIndent()

            try {
                val apiKey = BuildConfig.NVIDIA_API_KEY
                if (apiKey.isEmpty()) {
                    _aiAnalysisOutput.value = "NVIDIA_API_KEY not found in secrets."
                    return@launch
                }
                val request = NvidiaRequest(
                    messages = listOf(
                        NvidiaMessage(
                            role = "system",
                            content = "You are a privacy expert AI assistant (AI FSM Bot) specializing in Android device spoofing and hardware identifier privacy."
                        ),
                        NvidiaMessage(role = "user", content = prompt)
                    )
                )
                val response = NvidiaRetrofitClient.service.generateContent(
                    authHeader = "Bearer $apiKey",
                    request = request
                )
                val resultText = response.choices.firstOrNull()?.message?.content
                _aiAnalysisOutput.value = resultText ?: "Analysis complete, but no result generated."
            } catch (e: Exception) {
                _aiAnalysisOutput.value = "Error during analysis: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}
