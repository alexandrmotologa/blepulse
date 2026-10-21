package com.blepulse.android.ui.explorer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.blepulse.android.core.ble.BleConnection
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.core.model.BlePhy
import com.blepulse.android.core.decoder.DataFormat
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.model.ConnectionState
import com.blepulse.android.core.model.GattCharacteristicInfo
import com.blepulse.android.core.model.GattServiceInfo
import com.blepulse.android.ui.common.HexDumpViewer
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
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GattExplorerScreen(
    repository: BleRepository,
    onRouteToScope: (UUID) -> Unit,
    onRouteToFuzzer: (UUID, UUID) -> Unit
) {
    val activeConn by repository.activeConnection.collectAsState()
    val scope = rememberCoroutineScope()

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
                    text = "Select a device from the Scanner tab and tap 'CONNECT'",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val conn = activeConn!!
    val connState by conn.connectionState.collectAsState()
    val params by conn.connectionParameters.collectAsState()
    val services by conn.services.collectAsState()
    val logs by conn.operationLogs.collectAsState()

    var showWriteDialogForChar by remember { mutableStateOf<Pair<UUID, GattCharacteristicInfo>?>(null) }
    var showLogsPanel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBg)
    ) {
        // Connection Info Header
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
                            text = "GATT Service Explorer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = conn.address,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = { repository.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalRed, contentColor = Color.White),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("DISCONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Connection Parameters Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = connState.name,
                        color = when (connState) {
                            ConnectionState.CONNECTED -> SignalGreen
                            ConnectionState.CONNECTING -> WarningAmber
                            else -> CriticalRed
                        }
                    )

                    // MTU Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, BleCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable { scope.launch { conn.requestMtu(517) } }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MTU: ${params.mtu} (REQ 517)",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = BleCyan
                        )
                    }

                    // PHY Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable {
                                scope.launch {
                                    val nextPhy = if (params.txPhy == BlePhy.PHY_LE_1M) BlePhy.PHY_LE_2M else BlePhy.PHY_LE_1M
                                    conn.requestPhy(nextPhy, nextPhy)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PHY: ${params.txPhy.label}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ElectricViolet
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Toggle Logs
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorder, RoundedCornerShape(4.dp))
                            .clickable { showLogsPanel = !showLogsPanel }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (showLogsPanel) "HIDE LOGS" else "LOGS (${logs.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        if (showLogsPanel) {
            Surface(
                color = DarkSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(1.dp, DarkBorder)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs.take(20)) { logEntry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${logEntry.opType.name} -> ${logEntry.status}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (logEntry.status == com.blepulse.android.core.model.GattStatus.SUCCESS) SignalGreen else CriticalRed
                            )
                            Text(
                                text = "${logEntry.latencyMs}ms",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                        if (logEntry.message.isNotBlank()) {
                            Text(
                                text = logEntry.message,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Services Tree
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(services, key = { it.uuid }) { service ->
                ServiceAccordion(
                    service = service,
                    onReadChar = { charUuid ->
                        scope.launch { conn.readCharacteristic(service.uuid, charUuid) }
                    },
                    onOpenWrite = { char ->
                        showWriteDialogForChar = Pair(service.uuid, char)
                    },
                    onToggleNotify = { charUuid, enable ->
                        scope.launch { conn.setNotification(service.uuid, charUuid, enable) }
                    },
                    onRouteToScope = onRouteToScope,
                    onRouteToFuzzer = { charUuid -> onRouteToFuzzer(service.uuid, charUuid) }
                )
            }
        }
    }

    showWriteDialogForChar?.let { (serviceUuid, char) ->
        WriteCharacteristicDialog(
            char = char,
            onDismiss = { showWriteDialogForChar = null },
            onWrite = { data, withResponse ->
                scope.launch {
                    conn.writeCharacteristic(serviceUuid, char.uuid, data, withResponse)
                }
                showWriteDialogForChar = null
            }
        )
    }
}

@Composable
fun ServiceAccordion(
    service: GattServiceInfo,
    onReadChar: (UUID) -> Unit,
    onOpenWrite: (GattCharacteristicInfo) -> Unit,
    onToggleNotify: (UUID, Boolean) -> Unit,
    onRouteToScope: (UUID) -> Unit,
    onRouteToFuzzer: (UUID) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
    ) {
        // Service Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = service.uuid.toString(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    text = "${service.characteristics.size} Chars",
                    color = BleCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }

        // Characteristics List
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                service.characteristics.forEach { char ->
                    CharacteristicItem(
                        char = char,
                        onRead = { onReadChar(char.uuid) },
                        onWrite = { onOpenWrite(char) },
                        onToggleNotify = { enable -> onToggleNotify(char.uuid, enable) },
                        onRouteToScope = { onRouteToScope(char.uuid) },
                        onRouteToFuzzer = { onRouteToFuzzer(char.uuid) }
                    )
                }
            }
        }
    }
}

@Composable
fun CharacteristicItem(
    char: GattCharacteristicInfo,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onToggleNotify: (Boolean) -> Unit,
    onRouteToScope: () -> Unit,
    onRouteToFuzzer: () -> Unit
) {
    var notificationsActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        // Name & UUID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = char.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = char.uuid.toString(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            // Properties Chips
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (char.isReadable) StatusBadge("READ", SignalGreen)
                if (char.isWritable) StatusBadge("WRITE", BleCyan)
                if (char.isWritableWithoutResponse) StatusBadge("CMD", BleCyan)
                if (char.isNotifiable) StatusBadge("NOTIFY", WarningAmber)
                if (char.isIndicatable) StatusBadge("INDICATE", ElectricViolet)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Current Value Presentations
        if (char.value != null && char.value.isNotEmpty()) {
            val p = DataTypeParsers.parseAllPresentations(char.value)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlateBg, shape = RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "HEX: ${p.hex}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = BleCyan
                )
                if (p.utf8.isNotBlank()) {
                    Text(
                        text = "UTF-8: \"${p.utf8}\"",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                }
                if (p.int16 != null) {
                    Text(
                        text = "Int16: ${p.int16} | Float32: ${p.float32 ?: "-"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            Text(
                text = "Value: (No data read yet)",
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (char.isReadable) {
                IconButton(onClick = onRead, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Read", tint = SignalGreen, modifier = Modifier.size(18.dp))
                }
            }

            if (char.isWritable || char.isWritableWithoutResponse) {
                IconButton(onClick = onWrite, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Write", tint = BleCyan, modifier = Modifier.size(18.dp))
                }
            }

            if (char.isNotifiable || char.isIndicatable) {
                IconButton(
                    onClick = {
                        notificationsActive = !notificationsActive
                        onToggleNotify(notificationsActive)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (notificationsActive) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        contentDescription = "Notify Toggle",
                        tint = if (notificationsActive) CriticalRed else WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Route to Scope
            IconButton(onClick = onRouteToScope, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ShowChart, contentDescription = "Waveform Scope", tint = BleCyan, modifier = Modifier.size(18.dp))
            }

            // Route to Fuzzer
            if (char.isWritable || char.isWritableWithoutResponse) {
                IconButton(onClick = onRouteToFuzzer, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.BugReport, contentDescription = "Fuzzer", tint = ElectricViolet, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun WriteCharacteristicDialog(
    char: GattCharacteristicInfo,
    onDismiss: () -> Unit,
    onWrite: (ByteArray, Boolean) -> Unit
) {
    var format by remember { mutableStateOf(DataFormat.HEX) }
    var input by remember { mutableStateOf("") }
    var withResponse by remember { mutableStateOf(char.isWritable) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Write Characteristic",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = char.name,
                    fontSize = 12.sp,
                    color = BleCyan
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Format selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Data Format:", fontSize = 13.sp, color = TextSecondary)
                    Box {
                        Button(
                            onClick = { formatMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = BleCyan),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(format.label, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = formatMenuExpanded,
                            onDismissRequest = { formatMenuExpanded = false }
                        ) {
                            DataFormat.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.label) },
                                    onClick = {
                                        format = f
                                        formatMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Value to Write (${format.label})") },
                    placeholder = { Text(if (format == DataFormat.HEX) "e.g. 01 02 A3 FF" else "Value") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (char.isWritable && char.isWritableWithoutResponse) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Write With Response (Ack)", fontSize = 13.sp, color = TextPrimary)
                        Switch(
                            checked = withResponse,
                            onCheckedChange = { withResponse = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            val bytes = runCatching { DataTypeParsers.encodeValue(format, input) }.getOrDefault(byteArrayOf())
                            onWrite(bytes, withResponse)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BleCyan, contentColor = Color.Black)
                    ) {
                        Text("SEND WRITE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
