package com.blepulse.android.ui.macro

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.blepulse.android.core.macro.GattMacroEngine
import com.blepulse.android.core.macro.StepExecutionResult
import com.blepulse.android.core.macro.StepStatus
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

@Composable
fun MacroScreen(
    repository: BleRepository
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
                    text = "No Peripheral Connected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to a peripheral to run automated GATT testing sequences",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val macroEngine = remember(activeConn) { GattMacroEngine(activeConn!!) }
    val runnerState by macroEngine.state.collectAsState()
    var scenarioDropdownExpanded by remember { mutableStateOf(false) }

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
                            text = "GATT Macro Automation",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Automated Hardware Validation",
                            fontSize = 11.sp,
                            color = BleCyan
                        )
                    }

                    Button(
                        onClick = {
                            if (runnerState.isRunning) {
                                macroEngine.abort()
                            } else {
                                macroEngine.runScenario()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runnerState.isRunning) CriticalRed else BleCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (runnerState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (runnerState.isRunning) "ABORT" else "RUN TEST",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scenario Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Macro Scenario:", fontSize = 12.sp, color = TextSecondary)
                    Box {
                        Button(
                            onClick = { if (!runnerState.isRunning) scenarioDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = TextPrimary),
                            shape = RoundedCornerShape(6.dp),
                            enabled = !runnerState.isRunning,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(runnerState.scenario.name, fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = scenarioDropdownExpanded,
                            onDismissRequest = { scenarioDropdownExpanded = false }
                        ) {
                            macroEngine.defaultScenarios.forEach { sc ->
                                DropdownMenuItem(
                                    text = { Text(sc.name) },
                                    onClick = {
                                        macroEngine.selectScenario(sc)
                                        scenarioDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scenario Description & Pass/Fail stats
        Surface(
            color = DarkSurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = runnerState.scenario.description,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(text = "${runnerState.passedCount} Passed", color = SignalGreen)
                    StatusBadge(text = "${runnerState.failedCount} Failed", color = if (runnerState.failedCount > 0) CriticalRed else TextMuted)
                    StatusBadge(text = "${runnerState.stepResults.size} Total Steps", color = BleCyan)
                }
            }
        }

        // Steps Checklist
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(runnerState.stepResults, key = { it.index }) { result ->
                StepResultCard(result = result)
            }
        }
    }
}

@Composable
fun StepResultCard(result: StepExecutionResult) {
    val borderColor = when (result.status) {
        StepStatus.PASSED -> SignalGreen.copy(alpha = 0.5f)
        StepStatus.FAILED -> CriticalRed.copy(alpha = 0.6f)
        StepStatus.RUNNING -> BleCyan
        StepStatus.PENDING -> DarkBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (result.status) {
                    StepStatus.PASSED -> Icons.Default.CheckCircle
                    StepStatus.FAILED -> Icons.Default.Warning
                    StepStatus.RUNNING -> Icons.Default.PlayArrow
                    StepStatus.PENDING -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                tint = when (result.status) {
                    StepStatus.PASSED -> SignalGreen
                    StepStatus.FAILED -> CriticalRed
                    StepStatus.RUNNING -> BleCyan
                    StepStatus.PENDING -> TextMuted
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "${result.index + 1}. ${result.step.description}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (result.message.isNotBlank()) {
                    Text(
                        text = result.message,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (result.status) {
                            StepStatus.FAILED -> CriticalRed
                            StepStatus.PASSED -> SignalGreen
                            else -> TextSecondary
                        }
                    )
                }
            }
        }

        if (result.latencyMs > 0) {
            Text(
                text = "${result.latencyMs}ms",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }
    }
}
