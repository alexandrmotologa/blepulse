package com.blepulse.android.ui.proximity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.proximity.GeigerProximityTracker
import com.blepulse.android.core.proximity.GeigerState
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeigerScreen(
    repository: BleRepository,
    targetDeviceAddress: String? = null
) {
    val context = LocalContext.current
    val tracker = remember { GeigerProximityTracker(context) }
    val geigerState by tracker.state.collectAsState()
    val discoveredDevices by repository.discoveredDevices.collectAsState()

    var selectedDevice by remember { mutableStateOf<BleDevice?>(null) }
    var deviceDropdownExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            tracker.stopTracking()
        }
    }

    LaunchedEffect(discoveredDevices, targetDeviceAddress) {
        if (targetDeviceAddress != null) {
            val targeted = discoveredDevices.find { it.address.equals(targetDeviceAddress, ignoreCase = true) }
            if (targeted != null) {
                selectedDevice = targeted
            }
        }
        if (selectedDevice == null && discoveredDevices.isNotEmpty()) {
            selectedDevice = discoveredDevices.first()
        }
    }

    LaunchedEffect(discoveredDevices, geigerState.targetAddress) {
        if (geigerState.targetAddress != null) {
            val dev = discoveredDevices.find { it.address == geigerState.targetAddress }
            if (dev != null) {
                tracker.updateRssi(dev.rssi)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBg)
    ) {
        val isWideScreen = maxWidth >= 680.dp
        val isCompactHeight = maxHeight < 580.dp

        if (isWideScreen) {
            // Adaptive 2-Pane Tablet / Landscape View
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Pane: Needle Dial Gauge & Distance Readout
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040711))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GeigerDialVisualizer(geigerState = geigerState)
                }

                // Right Pane: Device Controls, Audio/Haptic Switches
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GeigerHeader(
                        geigerState = geigerState,
                        onToggleTracking = {
                            if (geigerState.isRunning) {
                                tracker.stopTracking()
                            } else {
                                selectedDevice?.let { tracker.startTracking(it) }
                            }
                        }
                    )

                    GeigerTargetSelector(
                        selectedDevice = selectedDevice,
                        discoveredDevices = discoveredDevices,
                        expanded = deviceDropdownExpanded,
                        onExpandChange = { deviceDropdownExpanded = it },
                        onSelectDevice = { dev ->
                            selectedDevice = dev
                            deviceDropdownExpanded = false
                            if (geigerState.isRunning) {
                                tracker.startTracking(dev)
                            }
                        }
                    )

                    GeigerFeedbackControls(
                        geigerState = geigerState,
                        onToggleAudio = { tracker.setAudioEnabled(it) },
                        onToggleHaptic = { tracker.setHapticEnabled(it) }
                    )
                }
            }
        } else if (isCompactHeight) {
            // Adaptive Compact Scrollable Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                GeigerHeader(
                    geigerState = geigerState,
                    onToggleTracking = {
                        if (geigerState.isRunning) {
                            tracker.stopTracking()
                        } else {
                            selectedDevice?.let { tracker.startTracking(it) }
                        }
                    }
                )

                GeigerTargetSelector(
                    selectedDevice = selectedDevice,
                    discoveredDevices = discoveredDevices,
                    expanded = deviceDropdownExpanded,
                    onExpandChange = { deviceDropdownExpanded = it },
                    onSelectDevice = { dev ->
                        selectedDevice = dev
                        deviceDropdownExpanded = false
                        if (geigerState.isRunning) {
                            tracker.startTracking(dev)
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040711))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GeigerDialVisualizer(geigerState = geigerState)
                }

                GeigerFeedbackControls(
                    geigerState = geigerState,
                    onToggleAudio = { tracker.setAudioEnabled(it) },
                    onToggleHaptic = { tracker.setHapticEnabled(it) }
                )
            }
        } else {
            // Standard Phone Portrait View
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GeigerHeader(
                            geigerState = geigerState,
                            onToggleTracking = {
                                if (geigerState.isRunning) {
                                    tracker.stopTracking()
                                } else {
                                    selectedDevice?.let { tracker.startTracking(it) }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        GeigerTargetSelector(
                            selectedDevice = selectedDevice,
                            discoveredDevices = discoveredDevices,
                            expanded = deviceDropdownExpanded,
                            onExpandChange = { deviceDropdownExpanded = it },
                            onSelectDevice = { dev ->
                                selectedDevice = dev
                                deviceDropdownExpanded = false
                                if (geigerState.isRunning) {
                                    tracker.startTracking(dev)
                                }
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040711))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    GeigerDialVisualizer(geigerState = geigerState)
                }

                GeigerFeedbackControls(
                    geigerState = geigerState,
                    onToggleAudio = { tracker.setAudioEnabled(it) },
                    onToggleHaptic = { tracker.setHapticEnabled(it) }
                )
            }
        }
    }
}

@Composable
private fun GeigerHeader(
    geigerState: GeigerState,
    onToggleTracking: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Geiger Proximity Tracker",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Acoustic & Haptic Sensor Finder",
                fontSize = 11.sp,
                color = BleCyan
            )
        }

        Button(
            onClick = onToggleTracking,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (geigerState.isRunning) CriticalRed else SignalGreen,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                if (geigerState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (geigerState.isRunning) "STOP" else "TRACK",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun GeigerTargetSelector(
    selectedDevice: BleDevice?,
    discoveredDevices: List<BleDevice>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSelectDevice: (BleDevice) -> Unit
) {
    Surface(
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Target Peripheral:", fontSize = 12.sp, color = TextSecondary)
            Box {
                Button(
                    onClick = { onExpandChange(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = TextPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(selectedDevice?.displayName ?: "Select Device", fontSize = 11.sp)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandChange(false) }
                ) {
                    discoveredDevices.forEach { dev ->
                        DropdownMenuItem(
                            text = { Text("${dev.displayName} (${dev.rssi} dBm)") },
                            onClick = { onSelectDevice(dev) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeigerDialVisualizer(geigerState: GeigerState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GeigerGaugeCanvas(
            smoothedRssi = geigerState.smoothedRssi.toFloat(),
            modifier = Modifier.size(280.dp, 160.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${geigerState.currentRssi} dBm",
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = when {
                geigerState.currentRssi >= -55 -> SignalGreen
                geigerState.currentRssi >= -75 -> BleCyan
                geigerState.currentRssi >= -85 -> WarningAmber
                else -> CriticalRed
            }
        )

        Text(
            text = "ESTIMATED DISTANCE: ${"%.1f".format(geigerState.estimatedDistanceMeters)} METERS",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(text = geigerState.proximityZone.label, color = SignalGreen)
            StatusBadge(text = "${"%.1f".format(geigerState.tickFrequencyHz)} Clicks/sec", color = BleCyan)
        }
    }
}

@Composable
private fun GeigerFeedbackControls(
    geigerState: GeigerState,
    onToggleAudio: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (geigerState.isAudioEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                        contentDescription = "Audio Clicks",
                        tint = if (geigerState.isAudioEnabled) SignalGreen else TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Click Feedback", fontSize = 12.sp, color = TextPrimary)
                }
                Switch(
                    checked = geigerState.isAudioEnabled,
                    onCheckedChange = onToggleAudio
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = "Haptic Pulses",
                        tint = if (geigerState.isHapticEnabled) ElectricViolet else TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Haptic Vibration Pulses", fontSize = 12.sp, color = TextPrimary)
                }
                Switch(
                    checked = geigerState.isHapticEnabled,
                    onCheckedChange = onToggleHaptic
                )
            }
        }
    }
}

@Composable
fun GeigerGaugeCanvas(
    smoothedRssi: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h)
        val radius = h * 0.95f

        drawArc(
            color = Color(0xFF1E293B),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
        )

        drawArc(
            color = Color(0xFFFF1744),
            startAngle = 180f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )
        drawArc(
            color = Color(0xFFFFB300),
            startAngle = 225f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )
        drawArc(
            color = Color(0xFF00E5FF),
            startAngle = 270f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )
        drawArc(
            color = Color(0xFF00E676),
            startAngle = 315f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )

        val norm = ((smoothedRssi - (-100f)) / ((-35f) - (-100f))).coerceIn(0f, 1f)
        val needleAngleRad = Math.toRadians((180f + norm * 180f).toDouble())

        val needleLen = radius * 0.85f
        val needleEnd = Offset(
            center.x + (needleLen * cos(needleAngleRad)).toFloat(),
            center.y + (needleLen * sin(needleAngleRad)).toFloat()
        )

        drawLine(
            color = Color.White,
            start = center,
            end = needleEnd,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 10.dp.toPx(),
            center = center
        )
    }
}
