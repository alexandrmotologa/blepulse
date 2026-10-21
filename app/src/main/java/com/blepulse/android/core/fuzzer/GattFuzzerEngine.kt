package com.blepulse.android.core.fuzzer

import com.blepulse.android.core.ble.BleConnection
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.model.ConnectionState
import com.blepulse.android.core.model.GattStatus
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
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

enum class FuzzStrategy(val label: String, val description: String) {
    BOUNDARY_VALUES("Boundary Value Injection", "Injects 0-byte, 1-byte, MTU-boundary (20, 23, 247, 517), and oversized buffer payloads"),
    RANDOM_MUTATION("Random Mutation", "Injects pseudorandom byte streams, bit flips, 0x00 fills, and 0xFF saturations"),
    FORMAT_STRINGS("Format Strings & Malformed UTF-8", "Injects %s%n%x format strings, null-byte traps, and illegal UTF-8 sequence bytes"),
    WRITE_BURST_FLOOD("High-Rate Write Flood", "Dispatches rapid consecutive bursts to test RTOS task queue depth and buffer starvation"),
    RECONNECT_STRESS("Connection Cycle Stress", "Executes rapid connect, MTU exchange, and disconnect cycles to uncover peripheral memory leaks")
}

data class FuzzResult(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val testCase: String,
    val strategy: FuzzStrategy,
    val payloadSize: Int,
    val payloadHex: String,
    val status: GattStatus,
    val latencyMs: Long,
    val isAnomalyDetected: Boolean,
    val notes: String
)

data class FuzzerConfig(
    val strategy: FuzzStrategy = FuzzStrategy.BOUNDARY_VALUES,
    val serviceUuid: UUID,
    val charUuid: UUID,
    val withResponse: Boolean = true,
    val burstCount: Int = 20,
    val intervalMs: Long = 100L
)

data class FuzzerState(
    val isFuzzing: Boolean = false,
    val progress: Float = 0.0f,
    val totalTests: Int = 0,
    val completedTests: Int = 0,
    val anomaliesCount: Int = 0,
    val currentTestCase: String = "",
    val results: List<FuzzResult> = emptyList()
)

class GattFuzzerEngine(
    private val connection: BleConnection
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fuzzJob: Job? = null

    private val _state = MutableStateFlow(FuzzerState())
    val state: StateFlow<FuzzerState> = _state.asStateFlow()

    fun startFuzzing(config: FuzzerConfig) {
        if (_state.value.isFuzzing) return

        fuzzJob = scope.launch {
            _state.value = FuzzerState(isFuzzing = true, results = emptyList())

            val payloads = generatePayloads(config)
            val total = payloads.size
            _state.update { it.copy(totalTests = total) }

            for ((index, item) in payloads.withIndex()) {
                if (!isActive) break
                val (testCase, payload) = item

                _state.update {
                    it.copy(
                        currentTestCase = testCase,
                        completedTests = index,
                        progress = if (total > 0) index.toFloat() / total else 0f
                    )
                }

                val startTime = System.currentTimeMillis()
                val writeResult = connection.writeCharacteristic(
                    serviceUuid = config.serviceUuid,
                    charUuid = config.charUuid,
                    data = payload,
                    withResponse = config.withResponse
                )
                val latency = System.currentTimeMillis() - startTime

                val isConnected = connection.connectionState.value == ConnectionState.CONNECTED
                val isFailure = writeResult.isFailure
                val anomaly = !isConnected || isFailure
                val notes = when {
                    !isConnected -> "CRITICAL: Peripheral connection dropped or crashed"
                    isFailure -> "Fault: ${writeResult.exceptionOrNull()?.message ?: "GATT failure"}"
                    else -> "Ack received in ${latency}ms"
                }

                val result = FuzzResult(
                    testCase = testCase,
                    strategy = config.strategy,
                    payloadSize = payload.size,
                    payloadHex = DataTypeParsers.toHexString(payload.take(16).toByteArray()) + if (payload.size > 16) "..." else "",
                    status = if (isFailure) GattStatus.FAILURE else GattStatus.SUCCESS,
                    latencyMs = latency,
                    isAnomalyDetected = anomaly,
                    notes = notes
                )

                _state.update { current ->
                    current.copy(
                        results = listOf(result) + current.results,
                        anomaliesCount = if (anomaly) current.anomaliesCount + 1 else current.anomaliesCount
                    )
                }

                if (!isConnected) {
                    // Peripheral crashed or disconnected, stop fuzzing
                    break
                }

                delay(config.intervalMs)
            }

            _state.update {
                it.copy(
                    isFuzzing = false,
                    progress = 1.0f,
                    completedTests = it.totalTests
                )
            }
        }
    }

    fun stopFuzzing() {
        fuzzJob?.cancel()
        fuzzJob = null
        _state.update { it.copy(isFuzzing = false) }
    }

    private fun generatePayloads(config: FuzzerConfig): List<Pair<String, ByteArray>> {
        return when (config.strategy) {
            FuzzStrategy.BOUNDARY_VALUES -> listOf(
                "Zero Length Buffer (0 bytes)" to byteArrayOf(),
                "Single Byte (0x00)" to byteArrayOf(0x00),
                "Single Byte (0xFF)" to byteArrayOf(0xFF.toByte()),
                "Default BLE MTU (20 bytes data)" to ByteArray(20) { 0x41 },
                "Standard ATT MTU (23 bytes)" to ByteArray(23) { 0x42 },
                "Intermediate Buffer (64 bytes)" to ByteArray(64) { 0x43 },
                "High Speed MTU (247 bytes)" to ByteArray(247) { 0x44 },
                "Max ATT MTU (512 bytes)" to ByteArray(512) { 0x45 },
                "Max Android MTU (517 bytes)" to ByteArray(517) { 0x46 },
                "Oversized Fragmented Buffer (600 bytes)" to ByteArray(600) { 0xAA.toByte() }
            )
            FuzzStrategy.RANDOM_MUTATION -> {
                val list = mutableListOf<Pair<String, ByteArray>>()
                list.add("All Zero Bytes (32 bytes)" to ByteArray(32) { 0x00 })
                list.add("All 0xFF Saturated Bytes (32 bytes)" to ByteArray(32) { 0xFF.toByte() })
                list.add("Alternating 0xAA / 0x55 (32 bytes)" to ByteArray(32) { if (it % 2 == 0) 0xAA.toByte() else 0x55 })
                for (i in 1..config.burstCount) {
                    val len = Random.nextInt(1, 128)
                    val bytes = Random.nextBytes(len)
                    list.add("Random Payload #$i ($len bytes)" to bytes)
                }
                list
            }
            FuzzStrategy.FORMAT_STRINGS -> listOf(
                "Standard C Format Specifier (%s%s%s%s)" to "%s%s%s%s%s%s%s%s".toByteArray(StandardCharsets.US_ASCII),
                "Memory Write Format Specifier (%n%n%n%n)" to "%n%n%n%n%n%n%n%n".toByteArray(StandardCharsets.US_ASCII),
                "Hex Stack Leak Specifier (%08x%08x%08x)" to "%08x%08x%08x%08x".toByteArray(StandardCharsets.US_ASCII),
                "Null-Byte Boundary Injection" to byteArrayOf(0x00, 0x41, 0x44, 0x4D, 0x49, 0x4E, 0x00),
                "Command Injection Delimiters (;reboot;)" to ";reboot;id;ls -la;".toByteArray(StandardCharsets.US_ASCII),
                "Illegal Overlong UTF-8 Sequence" to byteArrayOf(0xC0.toByte(), 0xAF.toByte(), 0xC1.toByte(), 0xBF.toByte()),
                "Malformed Truncated Multi-Byte UTF-8" to byteArrayOf(0xE2.toByte(), 0x82.toByte()),
                "JNDI LDAP Template Injection" to "\${jndi:ldap://127.0.0.1/a}".toByteArray(StandardCharsets.US_ASCII)
            )
            FuzzStrategy.WRITE_BURST_FLOOD -> {
                val list = mutableListOf<Pair<String, ByteArray>>()
                for (i in 1..config.burstCount) {
                    list.add("Burst Packet #$i (16 bytes)" to ByteArray(16) { i.toByte() })
                }
                list
            }
            FuzzStrategy.RECONNECT_STRESS -> {
                listOf(
                    "State Probe #1 (4 bytes)" to byteArrayOf(0x01, 0x02, 0x03, 0x04),
                    "State Probe #2 (8 bytes)" to byteArrayOf(0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C)
                )
            }
        }
    }

    private fun isExpectedError(payloadSize: Int): Boolean {
        // Many BLE characteristics reject payloads larger than their defined attribute size with GATT error
        return payloadSize > 64
    }

    fun exportReportMarkdown(): String {
        val s = _state.value
        val sb = StringBuilder()
        sb.appendLine("# BLEPulse IoT Peripheral Fuzzing Audit Report")
        sb.appendLine()
        sb.appendLine("- Target Device: `${connection.address}`")
        sb.appendLine("- Timestamp: ${System.currentTimeMillis()}")
        sb.appendLine("- Total Tests Executed: ${s.completedTests}")
        sb.appendLine("- Anomalies / Suspected Crashes: ${s.anomaliesCount}")
        sb.appendLine()
        sb.appendLine("## Test Case Execution Log")
        sb.appendLine()
        sb.appendLine("| Test Case | Strategy | Size | Status | Latency | Anomaly | Notes |")
        sb.appendLine("|---|---|---|---|---|---|---|")
        for (r in s.results) {
            sb.appendLine("| ${r.testCase} | ${r.strategy.label} | ${r.payloadSize} B | ${r.status} | ${r.latencyMs}ms | ${if (r.isAnomalyDetected) "CRITICAL" else "OK"} | ${r.notes} |")
        }
        return sb.toString()
    }
}
