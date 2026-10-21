package com.blepulse.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.ui.explorer.GattExplorerScreen
import com.blepulse.android.ui.scanner.ScannerScreen
import com.blepulse.android.ui.simulator.SimulatorScreen
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.BlePulseTheme
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.tools.ActiveTool
import com.blepulse.android.ui.tools.ToolsScreen
import com.blepulse.android.ui.waveform.WaveformScreen
import java.util.UUID

enum class AppTab(val label: String, val icon: ImageVector) {
    SCANNER("Scanner", Icons.Default.Radar),
    EXPLORER("Explorer", Icons.Default.SettingsInputComponent),
    WAVEFORM("Scope", Icons.Default.ShowChart),
    TOOLS("Tools", Icons.Default.Build),
    SIMULATOR("Simulator", Icons.Default.Sensors)
}

class MainActivity : ComponentActivity() {

    private lateinit var bleRepository: BleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleRepository = BleRepository(applicationContext)

        setContent {
            BlePulseTheme {
                MainContent(bleRepository)
            }
        }
    }
}

@Composable
fun MainContent(repository: BleRepository) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var routedScopeCharUuid by remember { mutableStateOf<UUID?>(null) }
    var routedFuzzerServiceUuid by remember { mutableStateOf<UUID?>(null) }
    var routedFuzzerCharUuid by remember { mutableStateOf<UUID?>(null) }
    var routedTool by remember { mutableStateOf(ActiveTool.TERMINAL) }
    var targetDeviceAddress by remember { mutableStateOf<String?>(null) }

    // Runtime Permission Request for Android 12+ (API 31+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        if (isExpandedScreen) {
            // Adaptive Tablet / Foldable / Landscape Navigation Rail Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkSlateBg)
            ) {
                NavigationRail(
                    containerColor = DarkSurface,
                    header = {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BleCyan.copy(alpha = 0.15f))
                                .border(1.dp, BleCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "BLEPulse",
                                tint = BleCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                ) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = BleCyan,
                                selectedTextColor = BleCyan,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = DarkSlateBg
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(DarkSlateBg)
                ) {
                    AppTabContent(
                        tab = AppTab.entries[selectedTab],
                        repository = repository,
                        routedScopeCharUuid = routedScopeCharUuid,
                        routedFuzzerServiceUuid = routedFuzzerServiceUuid,
                        routedFuzzerCharUuid = routedFuzzerCharUuid,
                        routedTool = routedTool,
                        targetDeviceAddress = targetDeviceAddress,
                        onNavigateToExplorer = { address ->
                            repository.connect(address)
                            targetDeviceAddress = address
                            selectedTab = AppTab.EXPLORER.ordinal
                        },
                        onRouteToScope = { charUuid ->
                            routedScopeCharUuid = charUuid
                            selectedTab = AppTab.WAVEFORM.ordinal
                        },
                        onRouteToFuzzer = { serviceUuid, charUuid ->
                            routedFuzzerServiceUuid = serviceUuid
                            routedFuzzerCharUuid = charUuid
                            routedTool = ActiveTool.FUZZER
                            selectedTab = AppTab.TOOLS.ordinal
                        }
                    )
                }
            }
        } else {
            // Adaptive Compact Phone Layout with Bottom NavigationBar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 8.dp
                    ) {
                        AppTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BleCyan,
                                    selectedTextColor = BleCyan,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = DarkSlateBg
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSlateBg)
                        .padding(innerPadding)
                ) {
                    AppTabContent(
                        tab = AppTab.entries[selectedTab],
                        repository = repository,
                        routedScopeCharUuid = routedScopeCharUuid,
                        routedFuzzerServiceUuid = routedFuzzerServiceUuid,
                        routedFuzzerCharUuid = routedFuzzerCharUuid,
                        routedTool = routedTool,
                        targetDeviceAddress = targetDeviceAddress,
                        onNavigateToExplorer = { address ->
                            repository.connect(address)
                            targetDeviceAddress = address
                            selectedTab = AppTab.EXPLORER.ordinal
                        },
                        onRouteToScope = { charUuid ->
                            routedScopeCharUuid = charUuid
                            selectedTab = AppTab.WAVEFORM.ordinal
                        },
                        onRouteToFuzzer = { serviceUuid, charUuid ->
                            routedFuzzerServiceUuid = serviceUuid
                            routedFuzzerCharUuid = charUuid
                            routedTool = ActiveTool.FUZZER
                            selectedTab = AppTab.TOOLS.ordinal
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppTabContent(
    tab: AppTab,
    repository: BleRepository,
    routedScopeCharUuid: UUID?,
    routedFuzzerServiceUuid: UUID?,
    routedFuzzerCharUuid: UUID?,
    routedTool: ActiveTool,
    targetDeviceAddress: String?,
    onNavigateToExplorer: (String) -> Unit,
    onRouteToScope: (UUID) -> Unit,
    onRouteToFuzzer: (UUID, UUID) -> Unit
) {
    when (tab) {
        AppTab.SCANNER -> {
            ScannerScreen(
                repository = repository,
                onNavigateToExplorer = onNavigateToExplorer
            )
        }
        AppTab.EXPLORER -> {
            GattExplorerScreen(
                repository = repository,
                onRouteToScope = onRouteToScope,
                onRouteToFuzzer = onRouteToFuzzer
            )
        }
        AppTab.WAVEFORM -> {
            WaveformScreen(
                repository = repository,
                targetCharUuid = routedScopeCharUuid
            )
        }
        AppTab.TOOLS -> {
            ToolsScreen(
                repository = repository,
                initialTool = routedTool,
                fuzzerServiceUuid = routedFuzzerServiceUuid,
                fuzzerCharUuid = routedFuzzerCharUuid,
                targetDeviceAddress = targetDeviceAddress
            )
        }
        AppTab.SIMULATOR -> {
            SimulatorScreen(
                repository = repository
            )
        }
    }
}
