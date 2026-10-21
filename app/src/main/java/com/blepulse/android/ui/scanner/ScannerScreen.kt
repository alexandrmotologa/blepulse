package com.blepulse.android.ui.scanner

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.window.Dialog
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.core.ble.HardwareMode
import com.blepulse.android.core.ble.ScanFilterConfig
import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.model.EddystoneData
import com.blepulse.android.core.pcap.PcapExporter
import com.blepulse.android.ui.common.HexDumpViewer
import com.blepulse.android.ui.common.SignalIndicator
import com.blepulse.android.ui.common.SparklineView
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
fun ScannerScreen(
    repository: BleRepository,
    onNavigateToExplorer: (String) -> Unit
) {
    val isScanning by repository.isScanning.collectAsState()
    val hardwareMode by repository.hardwareMode.collectAsState()
    val devices by repository.discoveredDevices.collectAsState()

    var showFilterDialog by remember { mutableStateOf(false) }
    var filterConfig by remember { mutableStateOf(ScanFilterConfig()) }
    var searchQuery by remember { mutableStateOf("") }
    var pcapNotice by remember { mutableStateOf<String?>(null) }

    val filteredDevices = remember(devices, searchQuery, filterConfig) {
        devices.filter { dev ->
            val matchesQuery = searchQuery.isBlank() ||
                    dev.displayName.contains(searchQuery, ignoreCase = true) ||
                    dev.address.contains(searchQuery, ignoreCase = true)
            val matchesRssi = dev.rssi >= filterConfig.minRssi
            val matchesConn = !filterConfig.onlyConnectable || dev.isConnectable
            matchesQuery && matchesRssi && matchesConn
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "BLE Scanner",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Mode selector chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hardwareMode == HardwareMode.VIRTUAL) ElectricViolet.copy(alpha = 0.2f) else SignalGreen.copy(alpha = 0.2f))
                                    .border(1.dp, if (hardwareMode == HardwareMode.VIRTUAL) ElectricViolet else SignalGreen, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val newMode = if (hardwareMode == HardwareMode.VIRTUAL) HardwareMode.REAL else HardwareMode.VIRTUAL
                                        repository.setHardwareMode(newMode)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (hardwareMode == HardwareMode.VIRTUAL) "VIRTUAL" else "HARDWARE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hardwareMode == HardwareMode.VIRTUAL) ElectricViolet else SignalGreen
                                )
                            }
                        }
                        Text(
                            text = "${filteredDevices.size} peripherals found",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val pcapBytes = PcapExporter.exportAdvertisements(filteredDevices)
                                pcapNotice = "Exported ${filteredDevices.size} packets (${pcapBytes.size} bytes) in Libpcap 2.4 format"
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Export PCAP", tint = BleCyan)
                        }

                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = BleCyan)
                        }

                        Button(
                            onClick = {
                                if (isScanning) repository.stopScan() else repository.startScan(filterConfig)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) CriticalRed else BleCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isScanning) "STOP" else "SCAN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, MAC address, UUID...", fontSize = 13.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (pcapNotice != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BleCyan.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, BleCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pcapNotice!!,
                            fontSize = 11.sp,
                            color = BleCyan
                        )
                        Text(
                            text = "Dismiss",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.clickable { pcapNotice = null }
                        )
                    }
                }
            }
        }

        // Device List
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isScanning) "Scanning radio broadcast channels..." else "Scanner idle",
                        fontSize = 15.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'SCAN' to listen for BLE 5.x advertising frames",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredDevices, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        onConnect = { onNavigateToExplorer(device.address) }
                    )
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            currentConfig = filterConfig,
            onDismiss = { showFilterDialog = false },
            onApply = {
                filterConfig = it
                showFilterDialog = false
            }
        )
    }
}

@Composable
fun DeviceCard(
    device: BleDevice,
    onConnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, if (expanded) BleCyan.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                SignalIndicator(rssi = device.rssi)
                Spacer(modifier = Modifier.height(4.dp))
                SparklineView(
                    samples = device.rssiHistory,
                    modifier = Modifier
                        .width(60.dp)
                        .height(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Badge row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (device.advertisementRecord.companyName != null) {
                StatusBadge(text = device.advertisementRecord.companyName!!, color = BleCyan)
            }

            if (device.advertisementRecord.iBeaconData != null) {
                StatusBadge(text = "iBeacon", color = WarningAmber)
            }

            if (device.advertisementRecord.eddystoneData != null) {
                StatusBadge(text = "Eddystone", color = ElectricViolet)
            }

            if (device.distanceMeters != null) {
                StatusBadge(
                    text = "${"%.1f".format(device.distanceMeters)}m (${device.proximityZone.label.take(4)})",
                    color = SignalGreen
                )
            }

            if (device.primaryPhy != com.blepulse.android.core.model.BlePhy.PHY_UNKNOWN) {
                StatusBadge(text = device.primaryPhy.label, color = TextMuted)
            }
        }

        // Action row: Connect + Expand Toggle
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (expanded) "Hide Details" else "View Details",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            if (device.isConnectable) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = BleCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("CONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            } else {
                StatusBadge(text = "Non-Connectable", color = TextMuted)
            }
        }

        // Expanded details
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                // iBeacon Specifics
                device.advertisementRecord.iBeaconData?.let { ib ->
                    Text("iBeacon Frame:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarningAmber)
                    Text("UUID: ${ib.uuid}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Text("Major: ${ib.major} | Minor: ${ib.minor} | TxPower @ 1m: ${ib.measuredPower} dBm", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Eddystone Specifics
                device.advertisementRecord.eddystoneData?.let { ed ->
                    Text("Eddystone Frame:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricViolet)
                    when (ed) {
                        is EddystoneData.Url -> Text("URL: ${ed.url} (Tx: ${ed.calibratedTxPower} dBm)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = BleCyan)
                        is EddystoneData.Uid -> Text("UID: NS=${ed.namespace} Inst=${ed.instance}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                        is EddystoneData.Tlm -> Text("TLM: Bat=${ed.batteryVoltageMv}mV | Temp=${"%.1f".format(ed.temperatureCelsius)}°C | Uptime=${ed.uptimeSeconds}s", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                        else -> {}
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Service UUIDs
                if (device.advertisementRecord.serviceUuids.isNotEmpty()) {
                    Text("Advertised Service UUIDs:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    device.advertisementRecord.serviceUuids.forEach { uuid ->
                        Text(
                            text = "• $uuid (${com.blepulse.android.core.sig.BluetoothSigRegistry.resolveServiceName(uuid)})",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Raw Advertisement Hex Dump
                Text("Raw AD Packet Data (${device.advertisementRecord.rawBytes.size} bytes):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                HexDumpViewer(bytes = device.advertisementRecord.rawBytes)
            }
        }
    }
}

@Composable
fun FilterDialog(
    currentConfig: ScanFilterConfig,
    onDismiss: () -> Unit,
    onApply: (ScanFilterConfig) -> Unit
) {
    var minRssi by remember { mutableStateOf(currentConfig.minRssi.toFloat()) }
    var onlyConnectable by remember { mutableStateOf(currentConfig.onlyConnectable) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Scanner Filters",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Minimum RSSI Threshold: ${minRssi.toInt()} dBm",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Slider(
                    value = minRssi,
                    onValueChange = { minRssi = it },
                    valueRange = -100f..-40f,
                    steps = 12
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connectable Only", fontSize = 14.sp, color = TextPrimary)
                    Switch(
                        checked = onlyConnectable,
                        onCheckedChange = { onlyConnectable = it }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = TextSecondary)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApply(
                                currentConfig.copy(
                                    minRssi = minRssi.toInt(),
                                    onlyConnectable = onlyConnectable
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BleCyan, contentColor = Color.Black)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
