package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.viewmodel.MainViewModel

@Composable
fun EdgeScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val messages by viewModel.edgeMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isEdgeLoading.collectAsStateWithLifecycle()
    val config by viewModel.edgeConfig.collectAsStateWithLifecycle()
    val autoRun by viewModel.edgeAutoRun.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isLoading) {
        val target = messages.size + if (isLoading) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "DOLPHIN//CODESPACE",
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = NeonGreen
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "endpoint: ${config.preset.lowercase()} · ${config.model}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "auto-run shell",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (autoRun) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.widthIn(min = 8.dp))
            Switch(
                checked = autoRun,
                onCheckedChange = { viewModel.setEdgeAutoRun(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreen.copy(alpha = 0.35f),
                    uncheckedThumbColor = NeonCyan.copy(alpha = 0.7f)
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                EdgeBubble(
                    role = msg.role,
                    content = msg.content,
                    runnableBlocks = if (msg.role == "assistant")
                        viewModel.extractCodeBlocks(msg.content).filter { viewModel.isRunnable(it.lang) }
                    else emptyList(),
                    onRun = { viewModel.runShell(it) }
                )
            }
            if (isLoading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = NeonGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(16.dp).widthIn(min = 16.dp)
                        )
                        Spacer(modifier = Modifier.widthIn(min = 8.dp))
                        Text(
                            "thinking…",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = NeonGreen
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ask the codespace…", fontFamily = FontFamily.Monospace) },
                singleLine = false,
                maxLines = 4,
                keyboardActions = KeyboardActions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = NeonCyan.copy(alpha = 0.5f),
                    cursorColor = NeonGreen,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )
            IconButton(
                onClick = {
                    val text = input
                    input = ""
                    viewModel.sendEdgeMessage(text)
                },
                enabled = !isLoading && input.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (!isLoading && input.isNotBlank()) NeonMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EdgeBubble(
    role: String,
    content: String,
    runnableBlocks: List<MainViewModel.CodeBlock> = emptyList(),
    onRun: (String) -> Unit = {}
) {
    val isUser = role == "user"
    val isShell = role == "shell"
    val bubbleColor = when {
        isUser -> NeonMagenta.copy(alpha = 0.18f)
        isShell -> NeonGreen.copy(alpha = 0.10f)
        else -> NeonCyan.copy(alpha = 0.10f)
    }
    val accent = when {
        isUser -> NeonMagenta
        isShell -> NeonGreen
        else -> NeonCyan
    }
    val label = when {
        isUser -> "you"
        isShell -> "shell · codespace-sandbox"
        else -> "codespace"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                label,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                content,
                fontSize = 14.sp,
                fontFamily = if (isUser) FontFamily.Default else FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )
            runnableBlocks.forEach { block ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "▶ run",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { onRun(block.code) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
