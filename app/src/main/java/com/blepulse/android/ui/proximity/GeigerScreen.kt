package com.blepulse.android.ui.proximity

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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

    // Automatically pick targeted device or first discovered device
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

    // Stream live RSSI updates from scanner to geiger tracker
    LaunchedEffect(discoveredDevices, geigerState.targetAddress) {
        if (geigerState.targetAddress != null) {
            val dev = discoveredDevices.find { it.address == geigerState.targetAddress }
            if (dev != null) {
                tracker.updateRssi(dev.rssi)
            }
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
                        onClick = {
                            if (geigerState.isRunning) {
                                tracker.stopTracking()
                            } else {
                                selectedDevice?.let { tracker.startTracking(it) }
                            }
                        },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Target Device Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Peripheral:", fontSize = 12.sp, color = TextSecondary)
                    Box {
                        Button(
                            onClick = { deviceDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = TextPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(selectedDevice?.displayName ?: "Select Device", fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = deviceDropdownExpanded,
                            onDismissRequest = { deviceDropdownExpanded = false }
                        ) {
                            discoveredDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text("${dev.displayName} (${dev.rssi} dBm)") },
                                    onClick = {
                                        selectedDevice = dev
                                        deviceDropdownExpanded = false
                                        if (geigerState.isRunning) {
                                            tracker.startTracking(dev)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Geiger Dial Gauge Visualizer
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Analog Needle Gauge
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

        // Audio, Haptic & Calibration Settings Bar
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
                        onCheckedChange = { tracker.setAudioEnabled(it) }
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
                        onCheckedChange = { tracker.setHapticEnabled(it) }
                    )
                }
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

        // Draw background Arc from 180 to 360 degrees
        drawArc(
            color = Color(0xFF1E293B),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Colored Zones:
        // Far: 180 -> 225 deg (Red)
        drawArc(
            color = Color(0xFFFF1744),
            startAngle = 180f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )
        // Mid-Far: 225 -> 270 deg (Amber)
        drawArc(
            color = Color(0xFFFFB300),
            startAngle = 225f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )
        // Near: 270 -> 315 deg (Cyan)
        drawArc(
            color = Color(0xFF00E5FF),
            startAngle = 270f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )
        // Immediate: 315 -> 360 deg (Green)
        drawArc(
            color = Color(0xFF00E676),
            startAngle = 315f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx())
        )

        // Calculate Needle Angle: -100 dBm -> 180 deg; -35 dBm -> 360 deg
        val norm = ((smoothedRssi - (-100f)) / ((-35f) - (-100f))).coerceIn(0f, 1f)
        val needleAngleRad = Math.toRadians((180f + norm * 180f).toDouble())

        val needleLen = radius * 0.85f
        val needleEnd = Offset(
            center.x + (needleLen * cos(needleAngleRad)).toFloat(),
            center.y + (needleLen * sin(needleAngleRad)).toFloat()
        )

        // Draw Needle line
        drawLine(
            color = Color.White,
            start = center,
            end = needleEnd,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw Needle center pivot hub
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 10.dp.toPx(),
            center = center
        )
    }
}
