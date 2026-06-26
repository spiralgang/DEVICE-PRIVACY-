package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.repackage.StepStatus
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val apps by viewModel.forgeApps.collectAsStateWithLifecycle()
    val selected by viewModel.selectedForgeApp.collectAsStateWithLifecycle()
    val steps by viewModel.pipelineSteps.collectAsStateWithLifecycle()
    val busy by viewModel.forgeBusy.collectAsStateWithLifecycle()
    val toolchain by viewModel.toolchainStatus.collectAsStateWithLifecycle()
    val terminal by viewModel.terminalOutput.collectAsStateWithLifecycle()
    val remoteEndpoint by viewModel.remoteEndpoint.collectAsStateWithLifecycle()

    var endpointField by remember(remoteEndpoint) { mutableStateOf(remoteEndpoint) }
    var useAndroidId by remember { mutableStateOf(true) }
    var useImei by remember { mutableStateOf(false) }
    var useMac by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) viewModel.loadForgeApps()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "FORGE // REPACKAGER",
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "On-device repackaging spoofer: decompile a target app, inject spoofed " +
                "identifiers, rebuild, sign and install the clone. Re-signed clones are " +
                "rejected by apps that verify their signature / pin certs / use Play Integrity.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Toolchain card
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("PROOT TOOLCHAIN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                Text(toolchain, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.provisionToolchain() }, enabled = !busy) {
                        Text("PROVISION")
                    }
                    OutlinedButton(onClick = { viewModel.printEnvironmentReport() }, enabled = !busy) {
                        Text("ENV REPORT")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "REMOTE BUILD BACKEND (Cloud Shell / GCE / any Linux)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedTextField(
                    value = endpointField,
                    onValueChange = { endpointField = it },
                    placeholder = { Text("https://worker.example/build", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { viewModel.setRemoteEndpoint(endpointField) }, enabled = !busy) {
                    Text(if (remoteEndpoint.isBlank()) "SAVE ENDPOINT" else "ENDPOINT SET ✓")
                }
            }
        }

        // Spoof options
        Spacer(Modifier.height(16.dp))
        Text("IDENTIFIERS TO SPOOF", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        ForgeCheckRow("ANDROID_ID", useAndroidId) { useAndroidId = it }
        ForgeCheckRow("IMEI / Device ID", useImei) { useImei = it }
        ForgeCheckRow("Wi-Fi MAC", useMac) { useMac = it }

        // Target app picker
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TARGET APP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.loadForgeApps() }) { Text("RESCAN (${apps.size})") }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                apps.forEach { app ->
                    val isSel = selected?.packageName == app.packageName
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectForgeApp(app) }
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                        if (isSel) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                }
                if (apps.isEmpty()) {
                    Text("No apps scanned. Tap RESCAN.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        // Run / install
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.runForgePipeline(useAndroidId, useImei, useMac) },
                enabled = !busy && selected != null,
                modifier = Modifier.weight(1f)
            ) { Text(if (busy) "WORKING…" else "RUN PIPELINE") }
            OutlinedButton(
                onClick = { viewModel.installLastClone() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("INSTALL CLONE") }
        }
        if (busy) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
        }

        // Pipeline steps
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("PIPELINE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            steps.forEach { s ->
                val color = when (s.status) {
                    StepStatus.OK -> MaterialTheme.colorScheme.tertiary
                    StepStatus.FAILED -> MaterialTheme.colorScheme.error
                    StepStatus.RUNNING -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    "[${s.status}] ${s.name} ${s.detail}",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Terminal
        Spacer(Modifier.height(16.dp))
        Text("EDGE SHELL (real)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        ForgeTerminal(output = terminal, onSubmit = { viewModel.runTerminalCommand(it) })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ForgeCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgeTerminal(output: String, onSubmit: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(output) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .padding(8.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Text(
                text = output.ifBlank { "device-privacy edge shell — try: id, uname -a, getprop ro.product.cpu.abi" },
                color = Color(0xFF39FF14),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("command…", color = Color.Gray, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(color = Color(0xFF39FF14), fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            trailingIcon = {
                IconButton(onClick = { if (input.isNotBlank()) { onSubmit(input); input = "" } }) {
                    Icon(Icons.Filled.Send, contentDescription = "Run", tint = Color(0xFF39FF14))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) { onSubmit(input); input = "" } }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.DarkGray,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = Color(0xFF39FF14)
            ),
            singleLine = true
        )
    }
}
