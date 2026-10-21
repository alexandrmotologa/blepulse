package com.blepulse.android.ui.fota

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.core.fota.FotaManager
import com.blepulse.android.core.fota.FotaProtocol
import com.blepulse.android.core.fota.FotaState
import com.blepulse.android.ui.common.BleCard
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.BleGreen
import com.blepulse.android.ui.theme.BleOrange
import com.blepulse.android.ui.theme.BleRed
import com.blepulse.android.ui.theme.BleViolet
import com.blepulse.android.ui.theme.DarkCard
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.theme.TextPrimary
import com.blepulse.android.ui.theme.TextSecondary

@Composable
fun FotaScreen(
    repository: BleRepository,
    modifier: Modifier = Modifier
) {
    val fotaManager = remember { FotaManager() }
    val progress by fotaManager.progress.collectAsState()
    val logs by fotaManager.logs.collectAsState()
    val logListState = rememberLazyListState()

    var selectedProtocol by remember { mutableStateOf(FotaProtocol.ESP32_BLE_OTA) }
    var selectedPresetSizeKb by remember { mutableIntStateOf(32) }
    var chunkSize by remember { mutableFloatStateOf(244f) }
    var interChunkDelayMs by remember { mutableFloatStateOf(5f) }

    // Synthesize sample firmware binary for testing
    val currentFirmwareBytes = remember(selectedProtocol, selectedPresetSizeKb) {
        val totalBytes = selectedPresetSizeKb * 1024
        val bytes = ByteArray(totalBytes)
        when (selectedProtocol) {
            FotaProtocol.ESP32_BLE_OTA -> {
                // ESP32 magic byte
                bytes[0] = 0xE9.toByte()
                bytes[1] = 0x03.toByte()
                bytes[2] = 0x02.toByte()
                bytes[3] = 0x20.toByte()
            }
            FotaProtocol.NORDIC_SECURE_DFU -> {
                // PK zip header
                bytes[0] = 0x50.toByte()
                bytes[1] = 0x4B.toByte()
                bytes[2] = 0x03.toByte()
                bytes[3] = 0x04.toByte()
            }
            FotaProtocol.GENERIC_STREAM_OTA -> {
                bytes[0] = 0xAA.toByte()
                bytes[1] = 0x55.toByte()
            }
        }
        for (i in 4 until totalBytes) {
            bytes[i] = ((i * 37) and 0xFF).toByte()
        }
        bytes
    }

    val firmwareMeta = remember(currentFirmwareBytes) {
        fotaManager.inspectFirmware(currentFirmwareBytes, "${selectedProtocol.name.lowercase()}_v1.4.bin")
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSlateBg)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(BleViolet.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = BleViolet
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "FOTA Firmware Flasher",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Nordic DFU & ESP-IDF Over-The-Air Flashing",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Protocol Selector
        Text(
            text = "TARGET PROTOCOL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BleCyan
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FotaProtocol.entries.forEach { proto ->
                FilterChip(
                    selected = selectedProtocol == proto,
                    onClick = { selectedProtocol = proto },
                    label = { Text(proto.displayName, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BleCyan.copy(alpha = 0.2f),
                        selectedLabelColor = BleCyan,
                        containerColor = DarkSurface,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Firmware Metadata Card
        BleCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = firmwareMeta.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${firmwareMeta.fileSizeBytes / 1024} KB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BleGreen
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = firmwareMeta.detectedType,
                    fontSize = 11.sp,
                    color = BleViolet
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("CRC32 CHECKSUM", fontSize = 9.sp, color = TextMuted)
                        Text(
                            "0x${"%08X".format(firmwareMeta.crc32Checksum)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    Column {
                        Text("HEADER MAGIC", fontSize = 9.sp, color = TextMuted)
                        Text(
                            firmwareMeta.magicHeaderHex,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    Column {
                        Text("EST. TIME", fontSize = 9.sp, color = TextMuted)
                        Text("~${firmwareMeta.estimatedDurationSec}s", fontSize = 11.sp, color = BleOrange)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Sample image size selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sample Size:", fontSize = 10.sp, color = TextMuted)
                    listOf(16, 32, 64, 128).forEach { kb ->
                        FilterChip(
                            selected = selectedPresetSizeKb == kb,
                            onClick = { selectedPresetSizeKb = kb },
                            label = { Text("${kb}KB", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BleViolet.copy(alpha = 0.25f),
                                selectedLabelColor = BleViolet,
                                containerColor = DarkCard,
                                labelColor = TextMuted
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chunk & Delay Tuning
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chunk Size: ${chunkSize.toInt()} B",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Slider(
                    value = chunkSize,
                    onValueChange = { chunkSize = it },
                    valueRange = 20f..498f,
                    colors = SliderDefaults.colors(
                        thumbColor = BleCyan,
                        activeTrackColor = BleCyan,
                        inactiveTrackColor = DarkSurface
                    )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interval Delay: ${interChunkDelayMs.toInt()} ms",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Slider(
                    value = interChunkDelayMs,
                    onValueChange = { interChunkDelayMs = it },
                    valueRange = 0f..25f,
                    colors = SliderDefaults.colors(
                        thumbColor = BleOrange,
                        activeTrackColor = BleOrange,
                        inactiveTrackColor = DarkSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Section
        BleCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateBadge(progress.state)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = progress.statusMessage,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${(progress.percent * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (progress.state) {
                            FotaState.COMPLETED -> BleGreen
                            FotaState.FAILED, FotaState.ABORTED -> BleRed
                            else -> BleCyan
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = when (progress.state) {
                        FotaState.COMPLETED -> BleGreen
                        FotaState.FAILED -> BleRed
                        else -> BleCyan
                    },
                    trackColor = DarkSurface
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricPill("Speed", "${"%.1f".format(progress.transferRateKbps)} kbps")
                    MetricPill("Bytes", "${progress.bytesSent} / ${progress.totalBytes}")
                    MetricPill("Chunks", "${progress.currentChunk} / ${progress.totalChunks}")
                    MetricPill("ETA", "${progress.estimatedRemainingSeconds}s")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons
        val isFlashing = progress.state in listOf(
            FotaState.VALIDATING,
            FotaState.NEGOTIATING_MTU,
            FotaState.PREPARING,
            FotaState.FLASHING,
            FotaState.VERIFYING
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val activeConn = repository.activeConnection.value
                    if (activeConn != null) {
                        fotaManager.startFota(
                            connection = activeConn,
                            firmwareBytes = currentFirmwareBytes,
                            protocol = selectedProtocol,
                            chunkSize = chunkSize.toInt(),
                            interChunkDelayMs = interChunkDelayMs.toLong()
                        )
                    }
                },
                enabled = !isFlashing && repository.activeConnection.value != null,
                colors = ButtonDefaults.buttonColors(containerColor = BleCyan),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Flash OTA", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            if (isFlashing) {
                Button(
                    onClick = { fotaManager.cancelFota() },
                    colors = ButtonDefaults.buttonColors(containerColor = BleRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Abort", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        if (repository.activeConnection.value == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notice: Connect to a target BLE peripheral in Scanner/Explorer before flashing.",
                fontSize = 10.sp,
                color = BleOrange
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Monospace Console Logs
        Text(
            text = "FOTA TRANSFER LOGS (${logs.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(DarkSurface, RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Logs will appear here once flashing starts...",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(logs) { entry ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "[${entry.level}]",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = when (entry.level) {
                                    "TX" -> BleCyan
                                    "RX" -> BleGreen
                                    "WARN" -> BleOrange
                                    "ERROR" -> BleRed
                                    else -> TextMuted
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = entry.message,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: FotaState) {
    val color: Color
    val text: String
    when (state) {
        FotaState.IDLE -> { color = TextMuted; text = "IDLE" }
        FotaState.VALIDATING -> { color = BleViolet; text = "VALIDATING" }
        FotaState.CONNECTING -> { color = BleCyan; text = "CONNECTING" }
        FotaState.NEGOTIATING_MTU -> { color = BleCyan; text = "MTU" }
        FotaState.PREPARING -> { color = BleOrange; text = "PREPARING" }
        FotaState.FLASHING -> { color = BleCyan; text = "FLASHING" }
        FotaState.VERIFYING -> { color = BleViolet; text = "VERIFYING" }
        FotaState.COMPLETED -> { color = BleGreen; text = "DONE" }
        FotaState.ABORTED -> { color = BleOrange; text = "ABORTED" }
        FotaState.FAILED -> { color = BleRed; text = "FAILED" }
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column {
        Text(label, fontSize = 9.sp, color = TextMuted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
