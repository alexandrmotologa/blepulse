package com.blepulse.android.core.simulator

import com.blepulse.android.core.ble.BleHardwareProvider
import com.blepulse.android.core.ble.CustomAdvertiseConfig
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.model.GattCharacteristicInfo
import com.blepulse.android.core.model.GattServiceInfo
import com.blepulse.android.core.sig.BluetoothSigRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sin

enum class DynamicBehavior(val label: String) {
    STATIC("Static Value"),
    HEART_RATE_PULSE("Dynamic Heart Rate (60-120 BPM)"),
    BATTERY_DRAIN("Discharging Battery (100% -> 0%)"),
    TEMP_FLUCTUATION("Environmental Temp Oscillation"),
    UART_ECHO("Nordic UART Loopback Echo")
}

data class SimulatorCharConfig(
    val uuid: UUID,
    val name: String,
    val properties: Int,
    val initialValue: ByteArray,
    val behavior: DynamicBehavior = DynamicBehavior.STATIC
)

data class SimulatorProfile(
    val id: String,
    val name: String,
    val serviceUuid: UUID,
    val characteristics: List<SimulatorCharConfig>
)

data class ConnectedClient(
    val address: String,
    val mtu: Int = 23,
    val connectedTimestamp: Long = System.currentTimeMillis()
)

data class SimulatorState(
    val isRunning: Boolean = false,
    val profile: SimulatorProfile,
    val connectedClients: List<ConnectedClient> = emptyList(),
    val requestCount: Long = 0L,
    val currentValues: Map<UUID, ByteArray> = emptyMap(),
    val serverLogs: List<String> = emptyList()
)

class PeripheralSimulatorEngine(
    private val hardwareProvider: BleHardwareProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    val defaultProfiles: List<SimulatorProfile> = listOf(
        SimulatorProfile(
            id = "hr_sensor",
            name = "Heart Rate Monitor",
            serviceUuid = BluetoothSigRegistry.uuidFromShort16("180D"),
            characteristics = listOf(
                SimulatorCharConfig(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A37"),
                    name = "Heart Rate Measurement",
                    properties = 0x10, // Notify
                    initialValue = byteArrayOf(0x00, 72),
                    behavior = DynamicBehavior.HEART_RATE_PULSE
                ),
                SimulatorCharConfig(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A38"),
                    name = "Body Sensor Location",
                    properties = 0x02, // Read
                    initialValue = byteArrayOf(0x01) // Chest
                )
            )
        ),
        SimulatorProfile(
            id = "battery_service",
            name = "Battery Monitor",
            serviceUuid = BluetoothSigRegistry.uuidFromShort16("180F"),
            characteristics = listOf(
                SimulatorCharConfig(
                    uuid = BluetoothSigRegistry.uuidFromShort16("2A19"),
                    name = "Battery Level",
                    properties = 0x12, // Read | Notify
                    initialValue = byteArrayOf(98),
                    behavior = DynamicBehavior.BATTERY_DRAIN
                )
            )
        ),
        SimulatorProfile(
            id = "nus_uart",
            name = "Nordic UART Loopback",
            serviceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            characteristics = listOf(
                SimulatorCharConfig(
                    uuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
                    name = "UART RX",
                    properties = 0x0C, // Write | Write Without Response
                    initialValue = byteArrayOf(),
                    behavior = DynamicBehavior.UART_ECHO
                ),
                SimulatorCharConfig(
                    uuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
                    name = "UART TX",
                    properties = 0x10, // Notify
                    initialValue = byteArrayOf()
                )
            )
        )
    )

    private val _state = MutableStateFlow(
        SimulatorState(
            profile = defaultProfiles.first(),
            currentValues = defaultProfiles.first().characteristics.associate { it.uuid to it.initialValue }
        )
    )
    val state: StateFlow<SimulatorState> = _state.asStateFlow()

    fun selectProfile(profile: SimulatorProfile) {
        if (_state.value.isRunning) stop()
        _state.value = SimulatorState(
            profile = profile,
            currentValues = profile.characteristics.associate { it.uuid to it.initialValue }
        )
    }

    fun start() {
        if (_state.value.isRunning) return
        val currentProfile = _state.value.profile

        // Start BLE advertising
        val config = CustomAdvertiseConfig(
            deviceName = "BLEPulse ${currentProfile.name}",
            serviceUuid = currentProfile.serviceUuid,
            includeTxPower = true,
            connectable = true
        )
        hardwareProvider.startAdvertising(config)

        _state.update {
            it.copy(
                isRunning = true,
                serverLogs = listOf("GATT Server started. Advertising '${config.deviceName}'") + it.serverLogs
            )
        }

        loopJob = scope.launch {
            var step = 0
            while (isActive) {
                delay(1000)
                step++

                val newValues = _state.value.currentValues.toMutableMap()
                for (char in currentProfile.characteristics) {
                    when (char.behavior) {
                        DynamicBehavior.HEART_RATE_PULSE -> {
                            val bpm = 70 + (sin(step * 0.2) * 15).toInt()
                            newValues[char.uuid] = byteArrayOf(0x00, bpm.toByte())
                        }
                        DynamicBehavior.BATTERY_DRAIN -> {
                            val level = (100 - (step % 100)).toByte()
                            newValues[char.uuid] = byteArrayOf(level)
                        }
                        DynamicBehavior.TEMP_FLUCTUATION -> {
                            val temp = 2200 + (sin(step * 0.1) * 300).toInt()
                            val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(temp.toShort())
                            newValues[char.uuid] = bb.array()
                        }
                        else -> {}
                    }
                }

                _state.update {
                    it.copy(
                        currentValues = newValues,
                        requestCount = it.requestCount + 1
                    )
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        hardwareProvider.stopAdvertising()
        _state.update {
            it.copy(
                isRunning = false,
                connectedClients = emptyList(),
                serverLogs = listOf("GATT Server stopped") + it.serverLogs
            )
        }
    }

    fun handleIncomingWrite(charUuid: UUID, data: ByteArray) {
        val currentProfile = _state.value.profile
        val char = currentProfile.characteristics.find { it.uuid == charUuid }
        val logEntry = "Write received on ${char?.name ?: charUuid}: ${DataTypeParsers.toHexString(data)}"

        val newValues = _state.value.currentValues.toMutableMap()
        newValues[charUuid] = data

        // If UART echo behavior, update TX characteristic
        if (char?.behavior == DynamicBehavior.UART_ECHO) {
            val txUuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
            newValues[txUuid] = data
        }

        _state.update {
            it.copy(
                currentValues = newValues,
                requestCount = it.requestCount + 1,
                serverLogs = listOf(logEntry) + it.serverLogs.take(50)
            )
        }
    }
}
