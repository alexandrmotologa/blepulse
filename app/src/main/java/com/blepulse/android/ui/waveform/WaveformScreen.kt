package com.blepulse.android.ui.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.core.waveform.ScopeChannel
import com.blepulse.android.core.waveform.ScopeStreamFormat
import com.blepulse.android.core.waveform.WaveformEngine
import com.blepulse.android.ui.common.StatusBadge
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.CriticalRed
import com.blepulse.android.ui.theme.DarkBorder
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.DarkSurfaceElevated
import com.blepulse.android.ui.theme.SignalGreen
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.theme.TextPrimary
import com.blepulse.android.ui.theme.TextSecondary
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

@Composable
fun WaveformScreen(
    repository: BleRepository,
    targetCharUuid: UUID? = null
) {
    val waveformEngine = remember { WaveformEngine() }
    val scopeState by waveformEngine.state.collectAsState()
    val activeConn by repository.activeConnection.collectAsState()

    var streamFormat by remember { mutableStateOf(ScopeStreamFormat.ACCEL_3AXIS) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(streamFormat) {
        waveformEngine.initializeChannels(streamFormat)
    }

    // Connect notify stream from active connection
    LaunchedEffect(activeConn, targetCharUuid, streamFormat) {
        if (activeConn != null && targetCharUuid != null) {
            activeConn!!.observeCharacteristic(targetCharUuid)
                .onEach { bytes ->
                    waveformEngine.feedData(streamFormat, bytes)
                }
                .launchIn(this)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBg)
    ) {
        // Control Bar
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
                            text = "Live Waveform Oscilloscope",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (targetCharUuid != null) "Routed: ${targetCharUuid.toString().take(8)}..." else "Simulated Data Engine",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { waveformEngine.togglePause() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (scopeState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = if (scopeState.isPaused) SignalGreen else BleCyan
                            )
                        }

                        IconButton(
                            onClick = { waveformEngine.clearData() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stream Format Selector
                    Box {
                        Button(
                            onClick = { formatMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = BleCyan),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(streamFormat.label, fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = formatMenuExpanded,
                            onDismissRequest = { formatMenuExpanded = false }
                        ) {
                            ScopeStreamFormat.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.label) },
                                    onClick = {
                                        streamFormat = f
                                        formatMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Window duration chips
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5, 10, 30).forEach { sec ->
                            val isSelected = scopeState.timeWindowSeconds == sec
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) BleCyan else DarkSurfaceElevated)
                                    .border(1.dp, if (isSelected) BleCyan else DarkBorder, RoundedCornerShape(4.dp))
                                    .clickable { waveformEngine.setTimeWindow(sec) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${sec}s",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Oscilloscope Canvas Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF04060A))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
        ) {
            OscilloscopeCanvas(
                channels = scopeState.channels,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Telemetry Channel Cards
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(scopeState.channels, key = { it.id }) { ch ->
                ScopeChannelCard(channel = ch)
            }
        }
    }
}

@Composable
fun OscilloscopeCanvas(
    channels: List<ScopeChannel>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Draw Grid
        val gridLinesX = 8
        val gridLinesY = 6

        for (i in 0..gridLinesX) {
            val x = (w / gridLinesX) * i
            drawLine(
                color = Color(0xFF162032),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx()
            )
        }

        for (i in 0..gridLinesY) {
            val y = (h / gridLinesY) * i
            drawLine(
                color = Color(0xFF162032),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 2. Draw Center Baseline
        drawLine(
            color = Color(0xFF263854),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1.5.dp.toPx()
        )

        // 3. Draw Waveforms
        channels.forEach { ch ->
            val points = ch.points
            if (points.size >= 2) {
                val minVal = ch.minValue
                val maxVal = ch.maxValue
                val range = (maxVal - minVal).coerceAtLeast(1f)

                val path = Path()
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                points.forEachIndexed { index, pt ->
                    val normY = 1.0f - ((pt.value - minVal) / range).coerceIn(0f, 1f)
                    val px = index * stepX
                    val py = (normY * (h - 40f)) + 20f

                    if (index == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(ch.colorArgb),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun ScopeChannelCard(
    channel: ScopeChannel
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, Color(channel.colorArgb).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(channel.colorArgb)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(channel.colorArgb))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "%.2f".format(channel.currentValue),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Min: ${"%.1f".format(channel.minValue)}", fontSize = 9.sp, color = TextSecondary)
            Text("Max: ${"%.1f".format(channel.maxValue)}", fontSize = 9.sp, color = TextSecondary)
        }
    }
}
