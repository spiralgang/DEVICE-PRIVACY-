package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.ui.DashboardScreen
import com.example.ui.theme.InkBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PrivacyTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivacyTheme {
                PrivacyApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyApp(viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize().graffitiBackground()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "DEVICE//PRIVACY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            fontSize = 22.sp,
                            color = NeonMagenta,
                            textAlign = TextAlign.Start
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NeonMagenta
                    )
                )
            }
        ) { innerPadding ->
            DashboardScreen(
                viewModel = viewModel,
                controlPort = viewModel.controlPort,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

/** Dark moody graffiti mural gradient with neon splatter/drips. */
private fun Modifier.graffitiBackground(): Modifier = this
    .background(
        Brush.linearGradient(
            colors = listOf(InkBlack, Color(0xFF120A1F), Color(0xFF09060F)),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    )
    .drawBehind {
        // Neon glow blooms (controlled "splatter").
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonMagenta.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.10f),
                radius = size.minDimension * 0.55f
            ),
            center = Offset(size.width * 0.12f, size.height * 0.10f),
            radius = size.minDimension * 0.55f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.32f),
                radius = size.minDimension * 0.5f
            ),
            center = Offset(size.width * 0.92f, size.height * 0.32f),
            radius = size.minDimension * 0.5f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonPurple.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.95f),
                radius = size.minDimension * 0.6f
            ),
            center = Offset(size.width * 0.5f, size.height * 0.95f),
            radius = size.minDimension * 0.6f
        )
        // Acid-green controlled drips down the left edge.
        val dripColor = NeonGreen.copy(alpha = 0.5f)
        listOf(0.18f, 0.46f, 0.74f).forEachIndexed { i, fx ->
            val x = size.width * fx
            val top = size.height * (0.0f + i * 0.05f)
            val len = size.height * (0.12f + i * 0.04f)
            drawLine(
                color = dripColor,
                start = Offset(x, top),
                end = Offset(x, top + len),
                strokeWidth = 3f
            )
            drawCircle(color = dripColor, radius = 5f, center = Offset(x, top + len))
        }
    }
