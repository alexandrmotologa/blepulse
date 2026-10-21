package com.blepulse.android.core.terminal

import com.blepulse.android.core.ble.BleConnection
import com.blepulse.android.core.decoder.DataTypeParsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class MessageDirection {
    INCOMING,
    OUTGOING,
    SYSTEM
}

data class TerminalMessage(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: MessageDirection,
    val text: String,
    val rawBytes: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TerminalMessage
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class LineEnding(val label: String, val bytes: ByteArray) {
    CRLF("CR+LF (\\r\\n)", byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())),
    LF("LF (\\n)", byteArrayOf('\n'.code.toByte())),
    CR("CR (\\r)", byteArrayOf('\r'.code.toByte())),
    NONE("None", byteArrayOf())
}

enum class DisplayMode(val label: String) {
    TEXT("ASCII Text"),
    HEX("Hexadecimal"),
    HEX_AND_TEXT("Hex + Text")
}

data class TerminalState(
    val isReady: Boolean = false,
    val serviceUuid: UUID? = null,
    val rxCharUuid: UUID? = null,
    val txCharUuid: UUID? = null,
    val lineEnding: LineEnding = LineEnding.CRLF,
    val displayMode: DisplayMode = DisplayMode.TEXT,
    val showTimestamps: Boolean = true,
    val autoScroll: Boolean = true,
    val messages: List<TerminalMessage> = emptyList(),
    val commandHistory: List<String> = emptyList()
)

class BleTerminalEngine(
    private val connection: BleConnection
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rxJob: Job? = null

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    // Known Serial Services
    companion object {
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        val ESP32_SPP_SERVICE_UUID: UUID = UUID.fromString("0000abf0-0000-1000-8000-00805f9b34fb")
        val ESP32_SPP_RX_UUID: UUID = UUID.fromString("0000abf1-0000-1000-8000-00805f9b34fb")
        val ESP32_SPP_TX_UUID: UUID = UUID.fromString("0000abf2-0000-1000-8000-00805f9b34fb")

        val DEFAULT_MACROS = listOf("help", "status", "AT", "AT+GMR", "reboot", "ping")
    }

    init {
        detectSerialChannels()
    }

    private fun detectSerialChannels() {
        val services = connection.services.value

        var foundService: UUID? = null
        var foundRx: UUID? = null
        var foundTx: UUID? = null

        // Check Nordic UART NUS
        val nus = services.find { it.uuid == NUS_SERVICE_UUID }
        if (nus != null) {
            foundService = NUS_SERVICE_UUID
            foundRx = NUS_RX_CHAR_UUID
            foundTx = NUS_TX_CHAR_UUID
        } else {
            // Check ESP32 SPP
            val esp = services.find { it.uuid == ESP32_SPP_SERVICE_UUID }
            if (esp != null) {
                foundService = ESP32_SPP_SERVICE_UUID
                foundRx = ESP32_SPP_RX_UUID
                foundTx = ESP32_SPP_TX_UUID
            } else {
                // Heuristic: find any service with a writable and a notifiable characteristic
                for (s in services) {
                    val rx = s.characteristics.find { it.isWritable || it.isWritableWithoutResponse }
                    val tx = s.characteristics.find { it.isNotifiable || it.isIndicatable }
                    if (rx != null && tx != null) {
                        foundService = s.uuid
                        foundRx = rx.uuid
                        foundTx = tx.uuid
                        break
                    }
                }
            }
        }

        if (foundService != null && foundRx != null && foundTx != null) {
            _state.update {
                it.copy(
                    isReady = true,
                    serviceUuid = foundService,
                    rxCharUuid = foundRx,
                    txCharUuid = foundTx,
                    messages = listOf(
                        TerminalMessage(
                            direction = MessageDirection.SYSTEM,
                            text = "Serial UART channels linked: RX=${foundRx.toString().take(8)}... TX=${foundTx.toString().take(8)}..."
                        )
                    )
                )
            }

            // Enable notifications on TX
            scope.launch {
                connection.setNotification(foundService, foundTx, enable = true)
            }

            // Observe incoming data stream
            rxJob?.cancel()
            rxJob = connection.observeCharacteristic(foundTx)
                .onEach { incomingBytes ->
                    val text = String(incomingBytes, StandardCharsets.UTF_8)
                    val msg = TerminalMessage(
                        direction = MessageDirection.INCOMING,
                        text = text,
                        rawBytes = incomingBytes
                    )
                    _state.update { current ->
                        current.copy(messages = (current.messages + msg).takeLast(500))
                    }
                }
                .launchIn(scope)
        } else {
            _state.update {
                it.copy(
                    isReady = false,
                    messages = listOf(
                        TerminalMessage(
                            direction = MessageDirection.SYSTEM,
                            text = "No compatible UART serial service detected on peripheral."
                        )
                    )
                )
            }
        }
    }

    fun sendText(text: String) {
        val st = _state.value
        if (!st.isReady || st.serviceUuid == null || st.rxCharUuid == null) return

        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = textBytes + st.lineEnding.bytes

        val msg = TerminalMessage(
            direction = MessageDirection.OUTGOING,
            text = text,
            rawBytes = payload
        )

        _state.update { current ->
            val updatedHistory = if (text.isNotBlank() && !current.commandHistory.contains(text)) {
                listOf(text) + current.commandHistory.take(49)
            } else current.commandHistory

            current.copy(
                messages = (current.messages + msg).takeLast(500),
                commandHistory = updatedHistory
            )
        }

        scope.launch {
            connection.writeCharacteristic(
                serviceUuid = st.serviceUuid,
                charUuid = st.rxCharUuid,
                data = payload,
                withResponse = false
            )
        }
    }

    fun setLineEnding(ending: LineEnding) {
        _state.update { it.copy(lineEnding = ending) }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _state.update { it.copy(displayMode = mode) }
    }

    fun toggleTimestamps() {
        _state.update { it.copy(showTimestamps = !it.showTimestamps) }
    }

    fun toggleAutoScroll() {
        _state.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun clearLog() {
        _state.update { it.copy(messages = emptyList()) }
    }

    fun exportLog(): String {
        return _state.value.messages.joinToString("\n") { m ->
            val dir = when (m.direction) {
                MessageDirection.INCOMING -> "<<"
                MessageDirection.OUTGOING -> ">>"
                MessageDirection.SYSTEM -> "--"
            }
            "[$dir] ${m.text}"
        }
    }
}
