package com.blepulse.android.ui.fuzzer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.core.fuzzer.FuzzResult
import com.blepulse.android.core.fuzzer.FuzzStrategy
import com.blepulse.android.core.fuzzer.FuzzerConfig
import com.blepulse.android.core.fuzzer.GattFuzzerEngine
import com.blepulse.android.core.model.GattStatus
import com.blepulse.android.ui.common.StatusBadge
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.CriticalRed
import com.blepulse.android.ui.theme.DarkBorder
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.DarkSurfaceElevated
import com.blepulse.android.ui.theme.ElectricViolet
import com.blepulse.android.ui.theme.SignalGreen
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.theme.TextPrimary
import com.blepulse.android.ui.theme.TextSecondary
import com.blepulse.android.ui.theme.WarningAmber
import java.util.UUID

@Composable
fun FuzzerScreen(
    repository: BleRepository,
    preselectedServiceUuid: UUID? = null,
    preselectedCharUuid: UUID? = null
) {
    val activeConn by repository.activeConnection.collectAsState()

    if (activeConn == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSlateBg)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Peripheral Fuzzer Offline",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to an IoT peripheral or the Virtual Fuzz Target first",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val conn = activeConn!!
    val services by conn.services.collectAsState()
    val fuzzerEngine = remember(conn) { GattFuzzerEngine(conn) }
    val fuzzerState by fuzzerEngine.state.collectAsState()

    var strategy by remember { mutableStateOf(FuzzStrategy.BOUNDARY_VALUES) }
    var strategyMenuExpanded by remember { mutableStateOf(false) }

    // Resolve target writable characteristics
    val writableCharacteristics = remember(services) {
        services.flatMap { s ->
            s.characteristics.filter { it.isWritable || it.isWritableWithoutResponse }
                .map { c -> Pair(s.uuid, c.uuid) }
        }
    }

    val targetPair = remember(writableCharacteristics, preselectedServiceUuid, preselectedCharUuid) {
        if (preselectedServiceUuid != null && preselectedCharUuid != null) {
            Pair(preselectedServiceUuid, preselectedCharUuid)
        } else {
            writableCharacteristics.firstOrNull() ?: Pair(
                UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBg)
    ) {
        // Top Toolbar
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "IoT Peripheral Fuzzer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Target: ${conn.address}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = BleCyan
                        )
                    }

                    Button(
                        onClick = {
                            if (fuzzerState.isFuzzing) {
                                fuzzerEngine.stopFuzzing()
                            } else {
                                val cfg = FuzzerConfig(
                                    strategy = strategy,
                                    serviceUuid = targetPair.first,
                                    charUuid = targetPair.second,
                                    withResponse = true,
                                    burstCount = 20,
                                    intervalMs = 120L
                                )
                                fuzzerEngine.startFuzzing(cfg)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (fuzzerState.isFuzzing) CriticalRed else ElectricViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (fuzzerState.isFuzzing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (fuzzerState.isFuzzing) "ABORT" else "START FUZZ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Strategy Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attack Strategy:", fontSize = 12.sp, color = TextSecondary)
                    Box {
                        Button(
                            onClick = { strategyMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = TextPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(strategy.label, fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = strategyMenuExpanded,
                            onDismissRequest = { strategyMenuExpanded = false }
                        ) {
                            FuzzStrategy.entries.forEach { strat ->
                                DropdownMenuItem(
                                    text = { Text(strat.label) },
                                    onClick = {
                                        strategy = strat
                                        strategyMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strategy.description,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        // Live Execution Stats
        Surface(
            color = DarkSurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (fuzzerState.isFuzzing) "Running: ${fuzzerState.currentTestCase}" else "Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(
                            text = "${fuzzerState.completedTests}/${fuzzerState.totalTests} Tests",
                            color = BleCyan
                        )
                        StatusBadge(
                            text = "${fuzzerState.anomaliesCount} Anomalies",
                            color = if (fuzzerState.anomaliesCount > 0) CriticalRed else SignalGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { fuzzerState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (fuzzerState.anomaliesCount > 0) CriticalRed else BleCyan,
                    trackColor = DarkSlateBg
                )
            }
        }

        // Execution Log List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(fuzzerState.results, key = { it.id }) { res ->
                FuzzResultCard(result = res)
            }
        }
    }
}

@Composable
fun FuzzResultCard(result: FuzzResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(
                1.dp,
                if (result.isAnomalyDetected) CriticalRed.copy(alpha = 0.6f) else DarkBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.testCase,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (result.isAnomalyDetected) CriticalRed else TextPrimary
            )
            StatusBadge(
                text = if (result.isAnomalyDetected) "ANOMALY" else "NORMAL",
                color = if (result.isAnomalyDetected) CriticalRed else SignalGreen
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Size: ${result.payloadSize} B | Latency: ${result.latencyMs}ms",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
            Text(
                text = result.status.name,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (result.status == GattStatus.SUCCESS) SignalGreen else CriticalRed
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Payload: ${result.payloadHex}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = BleCyan
        )

        if (result.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = result.notes,
                fontSize = 10.sp,
                color = if (result.isAnomalyDetected) CriticalRed else TextMuted
            )
        }
    }
}
