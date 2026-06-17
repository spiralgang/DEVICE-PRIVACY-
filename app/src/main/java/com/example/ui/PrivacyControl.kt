package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.MainViewModel

object IdentifierValidator {
    fun isValidMac(mac: String): Boolean {
        // e.g. 00:1A:2B:3C:4D:5E OR 00-1A-2B-3C-4D-5E
        val regex = Regex("^([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}\$")
        return regex.matches(mac)
    }

    fun isValidImei(imei: String): Boolean {
        // Exactly 15 digits
        return imei.matches(Regex("^[0-9]{15}\$"))
    }

    fun isValidDeviceId(deviceId: String): Boolean {
        // Exactly 16 hex characters
        return deviceId.matches(Regex("^[0-9a-fA-F]{16}\$"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyControl(viewModel: MainViewModel) {
    val context = LocalContext.current
    val hardwareState by viewModel.hardwareIdentifiers.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var macText by remember { mutableStateOf(hardwareState.spoofedMac) }
    var useMac by remember { mutableStateOf(hardwareState.useSpoofedMac) }

    var imeiText by remember { mutableStateOf(hardwareState.spoofedImei) }
    var useImei by remember { mutableStateOf(hardwareState.useSpoofedImei) }

    var deviceIdText by remember { mutableStateOf(hardwareState.spoofedDeviceId) }
    var useDeviceId by remember { mutableStateOf(hardwareState.useSpoofedDeviceId) }
    
    // Auto-update internal state if viewmodel changes (e.g. from random generation)
    LaunchedEffect(hardwareState) {
        macText = hardwareState.spoofedMac
        useMac = hardwareState.useSpoofedMac
        imeiText = hardwareState.spoofedImei
        useImei = hardwareState.useSpoofedImei
        deviceIdText = hardwareState.spoofedDeviceId
        useDeviceId = hardwareState.useSpoofedDeviceId
    }

    fun saveConfiguration() {
        val finalMac = if (IdentifierValidator.isValidMac(macText)) macText else hardwareState.spoofedMac
        val finalImei = if (IdentifierValidator.isValidImei(imeiText)) imeiText else hardwareState.spoofedImei
        val finalDeviceId = if (IdentifierValidator.isValidDeviceId(deviceIdText)) deviceIdText else hardwareState.spoofedDeviceId
        
        viewModel.updateHardwareIds(
            useMac, finalMac, 
            useImei, finalImei, 
            useDeviceId, finalDeviceId
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hardware Identifiers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { 
                viewModel.generateRandomSpoofedIds() 
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Generated Random")
            }
        }

        // MAC Component
        IdentifierToggleRow(
            label = "MAC Address",
            value = macText,
            onValueChange = { macText = it },
            isChecked = useMac,
            onCheckedChange = { useMac = it; saveConfiguration() },
            isError = macText.isNotEmpty() && !IdentifierValidator.isValidMac(macText)
        )

        // IMEI Component
        IdentifierToggleRow(
            label = "IMEI",
            value = imeiText,
            onValueChange = { imeiText = it },
            isChecked = useImei,
            onCheckedChange = { useImei = it; saveConfiguration() },
            isError = imeiText.isNotEmpty() && !IdentifierValidator.isValidImei(imeiText)
        )

        // DeviceID Component
        IdentifierToggleRow(
            label = "Android Device ID",
            value = deviceIdText,
            onValueChange = { deviceIdText = it },
            isChecked = useDeviceId,
            onCheckedChange = { useDeviceId = it; saveConfiguration() },
            isError = deviceIdText.isNotEmpty() && !IdentifierValidator.isValidDeviceId(deviceIdText)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.resetToReal() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset to Real")
            }
            
            Button(
                onClick = { 
                    val json = viewModel.getExportJson()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export JSON"))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Export JSON")
            }
        }

        Button(
            onClick = { saveConfiguration() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Apply Configuration")
        }
        
        if (history.isNotEmpty()) {
            HorizontalDivider()
            Text("History (Last 5 Generated)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            history.forEachIndexed { index, hist ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            viewModel.updateHardwareIds(
                                useSpoofedMac = true,
                                spoofedMac = hist.spoofedMac,
                                useSpoofedImei = true,
                                spoofedImei = hist.spoofedImei,
                                useSpoofedDeviceId = true,
                                spoofedDeviceId = hist.spoofedDeviceId
                            )
                        }
                        .padding(8.dp)
                ) {
                    Text("Gen ${index + 1} (Tap to Revert)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("MAC: ${hist.spoofedMac}", fontSize = 12.sp)
                    Text("IMEI: ${hist.spoofedImei}", fontSize = 12.sp)
                    Text("Dev ID: ${hist.spoofedDeviceId}", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifierToggleRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isChecked) Color.Green else Color.Gray)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (isChecked) "Spoofed Value" else "Real Value (Masked)") },
            singleLine = true,
            enabled = isChecked,
            isError = isError,
            supportingText = {
                if (isError) Text("Invalid format for $label")
            }
        )
    }
}
