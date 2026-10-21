package com.blepulse.android.fuzzer

import com.blepulse.android.core.ble.VirtualBleConnection
import com.blepulse.android.core.fuzzer.FuzzStrategy
import com.blepulse.android.core.fuzzer.FuzzerConfig
import com.blepulse.android.core.fuzzer.GattFuzzerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GattFuzzerEngineTest {

    @Test
    fun testFuzzerExecutionAgainstVirtualTarget() = runBlocking {
        // Connect to virtual target with vulnerable buffer (DE:AD:BE:EF:00:01)
        val conn = VirtualBleConnection("DE:AD:BE:EF:00:01")
        val fuzzer = GattFuzzerEngine(conn)

        // Wait for virtual connection to establish
        var connectWait = 0
        while (conn.connectionState.value != com.blepulse.android.core.model.ConnectionState.CONNECTED && connectWait < 30) {
            delay(20)
            connectWait++
        }

        val serviceUuid = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")
        val charUuid = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

        val config = FuzzerConfig(
            strategy = FuzzStrategy.BOUNDARY_VALUES,
            serviceUuid = serviceUuid,
            charUuid = charUuid,
            withResponse = true,
            intervalMs = 10L
        )

        fuzzer.startFuzzing(config)

        // Wait for fuzzer to start
        var startWait = 0
        while (!fuzzer.state.value.isFuzzing && startWait < 50) {
            delay(10)
            startWait++
        }

        // Wait for tests to finish
        var finishWait = 0
        while (fuzzer.state.value.isFuzzing && finishWait < 100) {
            delay(30)
            finishWait++
        }

        val state = fuzzer.state.value
        assertTrue(state.completedTests > 0)
        // Verify that oversized payloads (>64 bytes) were flagged as anomaly
        assertTrue(state.anomaliesCount >= 1)

        val report = fuzzer.exportReportMarkdown()
        assertTrue(report.contains("BLEPulse IoT Peripheral Fuzzing Audit Report"))
        assertTrue(report.contains("Target Device: `DE:AD:BE:EF:00:01`"))
    }
}
