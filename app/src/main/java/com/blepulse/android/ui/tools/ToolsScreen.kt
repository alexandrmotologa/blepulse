package com.blepulse.android.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.ui.audit.SecurityAuditorScreen
import com.blepulse.android.ui.fota.FotaScreen
import com.blepulse.android.ui.fuzzer.FuzzerScreen
import com.blepulse.android.ui.macro.MacroScreen
import com.blepulse.android.ui.proximity.GeigerScreen
import com.blepulse.android.ui.terminal.TerminalScreen
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.DarkSurfaceElevated
import com.blepulse.android.ui.theme.TextMuted
import java.util.UUID

enum class ActiveTool(val label: String, val icon: ImageVector) {
    TERMINAL("Terminal", Icons.Default.Terminal),
    GEIGER("Geiger RSSI", Icons.Default.Sensors),
    AUDIT("Security Audit", Icons.Default.Security),
    MACROS("GATT Macros", Icons.Default.PlayCircleOutline),
    FOTA("FOTA Flasher", Icons.Default.CloudUpload),
    FUZZER("Fuzzer", Icons.Default.BugReport)
}

@Composable
fun ToolsScreen(
    repository: BleRepository,
    initialTool: ActiveTool = ActiveTool.TERMINAL,
    fuzzerServiceUuid: UUID? = null,
    fuzzerCharUuid: UUID? = null,
    targetDeviceAddress: String? = null
) {
    var selectedTool by remember { mutableStateOf(initialTool) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBg)
    ) {
        // Tool Switcher Bar
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ActiveTool.entries) { tool ->
                    val isSelected = selectedTool == tool
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTool = tool },
                        leadingIcon = {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) BleCyan else TextMuted
                            )
                        },
                        label = {
                            Text(
                                text = tool.label,
                                fontSize = 11.sp,
                                color = if (isSelected) BleCyan else TextMuted
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BleCyan.copy(alpha = 0.2f),
                            selectedLabelColor = BleCyan,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // Active Tool Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTool) {
                ActiveTool.TERMINAL -> {
                    TerminalScreen(repository = repository)
                }
                ActiveTool.GEIGER -> {
                    GeigerScreen(
                        repository = repository,
                        targetDeviceAddress = targetDeviceAddress
                    )
                }
                ActiveTool.AUDIT -> {
                    SecurityAuditorScreen(repository = repository)
                }
                ActiveTool.MACROS -> {
                    MacroScreen(repository = repository)
                }
                ActiveTool.FOTA -> {
                    FotaScreen(repository = repository)
                }
                ActiveTool.FUZZER -> {
                    FuzzerScreen(
                        repository = repository,
                        preselectedServiceUuid = fuzzerServiceUuid,
                        preselectedCharUuid = fuzzerCharUuid
                    )
                }
            }
        }
    }
}
