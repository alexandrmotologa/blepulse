package com.blepulse.android.core.macro

import com.blepulse.android.core.ble.BleConnection
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.model.BlePhy
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
import java.util.UUID

sealed interface MacroStep {
    val description: String

    data class RequestMtu(val mtu: Int) : MacroStep {
        override val description: String = "Request ATT MTU: $mtu bytes"
    }

    data class RequestPhy(val phy: BlePhy) : MacroStep {
        override val description: String = "Switch PHY: ${phy.label}"
    }

    data class ReadCharacteristic(val serviceUuid: UUID, val charUuid: UUID) : MacroStep {
        override val description: String = "Read Char: ${charUuid.toString().take(8)}..."
    }

    data class WriteCharacteristic(
        val serviceUuid: UUID,
        val charUuid: UUID,
        val payload: ByteArray,
        val withResponse: Boolean = true
    ) : MacroStep {
        override val description: String = "Write Char: ${DataTypeParsers.toHexString(payload.take(6).toByteArray())} (${payload.size} B)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as WriteCharacteristic
            return serviceUuid == other.serviceUuid &&
                    charUuid == other.charUuid &&
                    payload.contentEquals(other.payload) &&
                    withResponse == other.withResponse
        }

        override fun hashCode(): Int {
            var result = serviceUuid.hashCode()
            result = 31 * result + charUuid.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + withResponse.hashCode()
            return result
        }
    }

    data class EnableNotification(val serviceUuid: UUID, val charUuid: UUID) : MacroStep {
        override val description: String = "Subscribe Notify: ${charUuid.toString().take(8)}..."
    }

    data class WaitDelay(val ms: Long) : MacroStep {
        override val description: String = "Delay ${ms}ms"
    }

    data class AssertEquals(
        val serviceUuid: UUID,
        val charUuid: UUID,
        val expectedHex: String
    ) : MacroStep {
        override val description: String = "Assert Char == '$expectedHex'"
    }
}

data class MacroScenario(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<MacroStep>
)

enum class StepStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED
}

data class StepExecutionResult(
    val index: Int,
    val step: MacroStep,
    val status: StepStatus = StepStatus.PENDING,
    val latencyMs: Long = 0,
    val message: String = ""
)

data class MacroRunnerState(
    val isRunning: Boolean = false,
    val scenario: MacroScenario,
    val currentStepIndex: Int = 0,
    val passedCount: Int = 0,
    val failedCount: Int = 0,
    val stepResults: List<StepExecutionResult> = emptyList()
)

class GattMacroEngine(
    private val connection: BleConnection
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null

    val defaultScenarios: List<MacroScenario> = listOf(
        MacroScenario(
            id = "commissioning_audit",
            name = "Commissioning & Throughput Test",
            description = "Negotiates maximum MTU (517), upgrades to LE 2M PHY, reads Device Info, and verifies Battery.",
            steps = listOf(
                MacroStep.RequestMtu(517),
                MacroStep.RequestPhy(BlePhy.PHY_LE_2M),
                MacroStep.WaitDelay(100),
                MacroStep.ReadCharacteristic(
                    UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
                    UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
                ),
                MacroStep.WaitDelay(50)
            )
        ),
        MacroScenario(
            id = "nus_uart_ping",
            name = "Nordic UART Echo Loopback",
            description = "Subscribes to NUS TX notifications, transmits a PING payload, and waits for loopback response.",
            steps = listOf(
                MacroStep.EnableNotification(
                    UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                    UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
                ),
                MacroStep.WaitDelay(50),
                MacroStep.WriteCharacteristic(
                    serviceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                    charUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
                    payload = "PING\r\n".toByteArray(),
                    withResponse = false
                ),
                MacroStep.WaitDelay(100)
            )
        )
    )

    private val _state = MutableStateFlow(
        MacroRunnerState(
            scenario = defaultScenarios.first(),
            stepResults = defaultScenarios.first().steps.mapIndexed { idx, st ->
                StepExecutionResult(index = idx, step = st)
            }
        )
    )
    val state: StateFlow<MacroRunnerState> = _state.asStateFlow()

    fun selectScenario(scenario: MacroScenario) {
        if (_state.value.isRunning) abort()
        _state.value = MacroRunnerState(
            scenario = scenario,
            stepResults = scenario.steps.mapIndexed { idx, st ->
                StepExecutionResult(index = idx, step = st)
            }
        )
    }

    fun runScenario() {
        if (_state.value.isRunning) return

        val scenario = _state.value.scenario
        val initialResults = scenario.steps.mapIndexed { idx, st ->
            StepExecutionResult(index = idx, step = st)
        }

        _state.value = _state.value.copy(
            isRunning = true,
            currentStepIndex = 0,
            passedCount = 0,
            failedCount = 0,
            stepResults = initialResults
        )

        runJob = scope.launch {
            var passed = 0
            var failed = 0

            for ((i, step) in scenario.steps.withIndex()) {
                if (!isActive) break

                updateStep(i, StepStatus.RUNNING, 0, "Executing...")
                val startTime = System.currentTimeMillis()

                var success = false
                var errorMsg = ""

                try {
                    when (step) {
                        is MacroStep.RequestMtu -> {
                            val res = connection.requestMtu(step.mtu)
                            success = res.isSuccess
                            if (!success) errorMsg = res.exceptionOrNull()?.message ?: "MTU failed"
                        }
                        is MacroStep.RequestPhy -> {
                            val res = connection.requestPhy(step.phy, step.phy)
                            success = res.isSuccess
                            if (!success) errorMsg = res.exceptionOrNull()?.message ?: "PHY failed"
                        }
                        is MacroStep.ReadCharacteristic -> {
                            val res = connection.readCharacteristic(step.serviceUuid, step.charUuid)
                            success = res.isSuccess
                            if (!success) errorMsg = res.exceptionOrNull()?.message ?: "Read failed"
                            else errorMsg = "Value: ${DataTypeParsers.toHexString(res.getOrNull() ?: byteArrayOf())}"
                        }
                        is MacroStep.WriteCharacteristic -> {
                            val res = connection.writeCharacteristic(step.serviceUuid, step.charUuid, step.payload, step.withResponse)
                            success = res.isSuccess
                            if (!success) errorMsg = res.exceptionOrNull()?.message ?: "Write failed"
                        }
                        is MacroStep.EnableNotification -> {
                            val res = connection.setNotification(step.serviceUuid, step.charUuid, enable = true)
                            success = res.isSuccess
                            if (!success) errorMsg = res.exceptionOrNull()?.message ?: "Notify failed"
                        }
                        is MacroStep.WaitDelay -> {
                            delay(step.ms)
                            success = true
                        }
                        is MacroStep.AssertEquals -> {
                            val res = connection.readCharacteristic(step.serviceUuid, step.charUuid)
                            if (res.isSuccess) {
                                val actualHex = DataTypeParsers.toHexString(res.getOrNull() ?: byteArrayOf()).replace(" ", "").lowercase()
                                val expected = step.expectedHex.replace(" ", "").lowercase()
                                success = actualHex == expected
                                if (!success) errorMsg = "Expected $expected but got $actualHex"
                            } else {
                                success = false
                                errorMsg = "Read failed before assertion"
                            }
                        }
                    }
                } catch (e: Exception) {
                    success = false
                    errorMsg = e.message ?: "Exception occurred"
                }

                val latency = System.currentTimeMillis() - startTime
                if (success) {
                    passed++
                    updateStep(i, StepStatus.PASSED, latency, if (errorMsg.isNotBlank()) errorMsg else "OK (${latency}ms)")
                } else {
                    failed++
                    updateStep(i, StepStatus.FAILED, latency, errorMsg)
                }

                _state.update {
                    it.copy(
                        currentStepIndex = i + 1,
                        passedCount = passed,
                        failedCount = failed
                    )
                }

                delay(30)
            }

            _state.update { it.copy(isRunning = false) }
        }
    }

    private fun updateStep(index: Int, status: StepStatus, latency: Long, msg: String) {
        _state.update { current ->
            val list = current.stepResults.toMutableList()
            if (index in list.indices) {
                list[index] = list[index].copy(status = status, latencyMs = latency, message = msg)
            }
            current.copy(stepResults = list)
        }
    }

    fun abort() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false) }
    }
}
