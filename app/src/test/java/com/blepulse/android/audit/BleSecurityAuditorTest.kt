package com.blepulse.android.audit

import com.blepulse.android.core.audit.BleSecurityAuditor
import com.blepulse.android.core.audit.Severity
import com.blepulse.android.core.model.AdvertisementRecord
import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.model.GattCharacteristicInfo
import com.blepulse.android.core.model.GattServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleSecurityAuditorTest {

    @Test
    fun testEsp32Fingerprinting() {
        val espDevice = BleDevice(
            address = "24:0A:C4:11:22:33",
            name = "ESP32 Sensor",
            rssi = -50,
            smoothedRssi = -50.0,
            advertisementRecord = AdvertisementRecord(
                manufacturerData = mapOf(0x02E5 to byteArrayOf(0x01, 0x02))
            )
        )

        val fp = BleSecurityAuditor.fingerprintChipset(espDevice)
        assertEquals("Espressif Systems", fp.vendor)
        assertTrue(fp.chipset.contains("ESP32"))
        assertTrue(fp.confidence >= 0.9f)
    }

    @Test
    fun testNordicFingerprinting() {
        val nordicDevice = BleDevice(
            address = "E0:4E:01:23:45:67",
            name = "nRF52 Blinky",
            rssi = -60,
            smoothedRssi = -60.0,
            advertisementRecord = AdvertisementRecord(
                manufacturerData = mapOf(0x0059 to byteArrayOf(0x04, 0x05))
            )
        )

        val fp = BleSecurityAuditor.fingerprintChipset(nordicDevice)
        assertEquals("Nordic Semiconductor", fp.vendor)
        assertTrue(fp.chipset.contains("nRF52"))
    }

    @Test
    fun testSecurityAuditWithUnprotectedCharacteristics() {
        val device = BleDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Vulnerable Lock",
            rssi = -70,
            smoothedRssi = -70.0
        )

        val unencryptedService = GattServiceInfo(
            uuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            name = "Nordic UART Service",
            isStandard = false,
            type = 0,
            characteristics = listOf(
                GattCharacteristicInfo(
                    uuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
                    name = "RX Characteristic",
                    properties = 0x08 or 0x04, // WRITE or WRITE_NO_RESPONSE
                    permissions = 0x10 // plain write without encryption
                )
            )
        )

        val report = BleSecurityAuditor.auditDeviceSecurity(device, listOf(unencryptedService))

        assertTrue(report.securityScore < 100)
        assertTrue(report.findings.isNotEmpty())

        val highOrCrit = report.findings.filter { it.severity == Severity.HIGH || it.severity == Severity.CRITICAL }
        assertTrue(highOrCrit.isNotEmpty())
    }
}
