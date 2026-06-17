package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistralTerminalPanel(viewModel: MainViewModel) {
    val mistralOutput by viewModel.mistralOutput.collectAsStateWithLifecycle()
    val isLoading by viewModel.isMistralLoading.collectAsStateWithLifecycle()
    var inputCommand by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // auto scroll to bottom when output changes
    LaunchedEffect(mistralOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 500.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .padding(8.dp)
    ) {
        // Mock Termux Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color.Red))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color.Yellow))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color.Green))
            Spacer(modifier = Modifier.width(8.dp))
            Text("root@nexus-edge:~", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }

        // Output Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Text(
                text = mistralOutput,
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
        }

        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color.Green,
                trackColor = Color.DarkGray
            )
        }

        // Input Field
        OutlinedTextField(
            value = inputCommand,
            onValueChange = { inputCommand = it },
            placeholder = { Text("Enter command...", color = Color.Gray, fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textStyle = LocalTextStyle.current.copy(
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            trailingIcon = {
                IconButton(onClick = {
                    if (inputCommand.isNotBlank()) {
                        viewModel.runMistralCommand(inputCommand)
                        inputCommand = ""
                    }
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.Green)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (inputCommand.isNotBlank()) {
                    viewModel.runMistralCommand(inputCommand)
                    inputCommand = ""
                }
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.DarkGray,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = Color.Green
            ),
            singleLine = true
        )
    }
}
