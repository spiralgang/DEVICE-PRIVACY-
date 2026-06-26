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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.DashboardScreen
import com.example.ui.DeviceMaskingScreen
import com.example.ui.ForgeScreen
import com.example.ui.TargetAppsScreen
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

private data class NavTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val tabs = listOf(
        NavTab("dashboard", "Vault", Icons.Filled.Home),
        NavTab("target_apps", "Ghost", Icons.Filled.List),
        NavTab("masking", "Tools", Icons.Filled.Build),
        NavTab("forge", "Forge", Icons.Filled.Terminal)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"

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
            },
            bottomBar = {
                NavigationBar(
                    containerColor = InkBlack.copy(alpha = 0.85f),
                    contentColor = NeonCyan
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo("dashboard") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonMagenta,
                                selectedTextColor = NeonMagenta,
                                indicatorColor = NeonPurple.copy(alpha = 0.4f),
                                unselectedIconColor = NeonCyan.copy(alpha = 0.6f),
                                unselectedTextColor = NeonCyan.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("dashboard") {
                    DashboardScreen(viewModel = viewModel, controlPort = viewModel.controlPort)
                }
                composable("target_apps") { TargetAppsScreen(viewModel) }
                composable("masking") { DeviceMaskingScreen(viewModel) }
                composable("forge") { ForgeScreen(viewModel) }
            }
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
