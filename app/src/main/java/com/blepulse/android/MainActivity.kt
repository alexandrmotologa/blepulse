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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.ui.explorer.GattExplorerScreen
import com.blepulse.android.ui.fuzzer.FuzzerScreen
import com.blepulse.android.ui.scanner.ScannerScreen
import com.blepulse.android.ui.simulator.SimulatorScreen
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.BlePulseTheme
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.theme.TextPrimary
import com.blepulse.android.ui.waveform.WaveformScreen
import java.util.UUID

enum class AppTab(val label: String, val icon: ImageVector) {
    SCANNER("Scanner", Icons.Default.Radar),
    EXPLORER("Explorer", Icons.Default.SettingsInputComponent),
    WAVEFORM("Scope", Icons.Default.ShowChart),
    FUZZER("Fuzzer", Icons.Default.BugReport),
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
            when (AppTab.entries[selectedTab]) {
                AppTab.SCANNER -> {
                    ScannerScreen(
                        repository = repository,
                        onNavigateToExplorer = { address ->
                            repository.connect(address)
                            selectedTab = AppTab.EXPLORER.ordinal
                        }
                    )
                }
                AppTab.EXPLORER -> {
                    GattExplorerScreen(
                        repository = repository,
                        onRouteToScope = { charUuid ->
                            routedScopeCharUuid = charUuid
                            selectedTab = AppTab.WAVEFORM.ordinal
                        },
                        onRouteToFuzzer = { serviceUuid, charUuid ->
                            routedFuzzerServiceUuid = serviceUuid
                            routedFuzzerCharUuid = charUuid
                            selectedTab = AppTab.FUZZER.ordinal
                        }
                    )
                }
                AppTab.WAVEFORM -> {
                    WaveformScreen(
                        repository = repository,
                        targetCharUuid = routedScopeCharUuid
                    )
                }
                AppTab.FUZZER -> {
                    FuzzerScreen(
                        repository = repository,
                        preselectedServiceUuid = routedFuzzerServiceUuid,
                        preselectedCharUuid = routedFuzzerCharUuid
                    )
                }
                AppTab.SIMULATOR -> {
                    SimulatorScreen(
                        repository = repository
                    )
                }
            }
        }
    }
}
