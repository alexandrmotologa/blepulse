package com.blepulse.android.ui.terminal

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.terminal.BleTerminalEngine
import com.blepulse.android.core.terminal.DisplayMode
import com.blepulse.android.core.terminal.LineEnding
import com.blepulse.android.core.terminal.MessageDirection
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
fun TerminalScreen(
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
                    text = "Connect to an ESP32 or Nordic UART device to open a serial session",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val terminalEngine = remember(activeConn) { BleTerminalEngine(activeConn!!) }
    val termState by terminalEngine.state.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var endingMenuExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(termState.messages.size) {
        if (termState.autoScroll && termState.messages.isNotEmpty()) {
            listState.animateScrollToItem(termState.messages.size - 1)
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
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BLE Serial Terminal (UART)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (termState.isReady) "Linked to ${activeConn!!.address}" else "Detecting UART service...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (termState.isReady) BleCyan else WarningAmber
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { terminalEngine.clearLog() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Options Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Line ending dropdown
                    Box {
                        Button(
                            onClick = { endingMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = BleCyan),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(termState.lineEnding.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        DropdownMenu(expanded = endingMenuExpanded, onDismissRequest = { endingMenuExpanded = false }) {
                            LineEnding.entries.forEach { le ->
                                DropdownMenuItem(
                                    text = { Text(le.label) },
                                    onClick = {
                                        terminalEngine.setLineEnding(le)
                                        endingMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Display mode dropdown
                    Box {
                        Button(
                            onClick = { modeMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = ElectricViolet),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(termState.displayMode.label, fontSize = 10.sp)
                        }
                        DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                            DisplayMode.entries.forEach { dm ->
                                DropdownMenuItem(
                                    text = { Text(dm.label) },
                                    onClick = {
                                        terminalEngine.setDisplayMode(dm)
                                        modeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Auto-scroll toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (termState.autoScroll) SignalGreen.copy(alpha = 0.2f) else DarkSurfaceElevated)
                            .border(1.dp, if (termState.autoScroll) SignalGreen else DarkBorder, RoundedCornerShape(4.dp))
                            .clickable { terminalEngine.toggleAutoScroll() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (termState.autoScroll) "AUTO-SCROLL ON" else "PAUSED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (termState.autoScroll) SignalGreen else TextMuted
                        )
                    }
                }
            }
        }

        // Quick Macro Shortcuts Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(BleTerminalEngine.DEFAULT_MACROS) { cmd ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurface)
                        .border(1.dp, BleCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable { terminalEngine.sendText(cmd) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cmd,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BleCyan
                    )
                }
            }
        }

        // Terminal Log Console Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF04060A))
                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(termState.messages, key = { it.id }) { msg ->
                    val color = when (msg.direction) {
                        MessageDirection.INCOMING -> BleCyan
                        MessageDirection.OUTGOING -> SignalGreen
                        MessageDirection.SYSTEM -> WarningAmber
                    }
                    val prefix = when (msg.direction) {
                        MessageDirection.INCOMING -> "<<"
                        MessageDirection.OUTGOING -> ">>"
                        MessageDirection.SYSTEM -> "--"
                    }

                    val content = when (termState.displayMode) {
                        DisplayMode.TEXT -> msg.text
                        DisplayMode.HEX -> DataTypeParsers.toHexString(msg.rawBytes)
                        DisplayMode.HEX_AND_TEXT -> "${DataTypeParsers.toHexString(msg.rawBytes)} | ${msg.text.trim()}"
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "$prefix ",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Text(
                            text = content,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (msg.direction == MessageDirection.OUTGOING) Color(0xFFE2E8F0) else color
                        )
                    }
                }
            }
        }

        // Send Input Bar
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Enter command or text...", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            terminalEngine.sendText(inputText)
                            inputText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BleCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
