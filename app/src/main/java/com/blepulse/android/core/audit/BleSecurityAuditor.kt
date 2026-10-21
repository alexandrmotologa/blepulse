package com.blepulse.android.core.audit

import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.model.GattCharacteristicInfo
import com.blepulse.android.core.model.GattServiceInfo
import java.util.UUID

enum class Severity(val label: String, val scoreImpact: Int) {
    CRITICAL("CRITICAL", 30),
    HIGH("HIGH", 20),
    MEDIUM("MEDIUM", 10),
    LOW("LOW", 5),
    INFO("INFO", 0)
}

data class AuditFinding(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val severity: Severity,
    val targetUuid: String? = null,
    val description: String,
    val recommendation: String
)

data class ChipsetFingerprint(
    val chipset: String,
    val vendor: String,
    val architecture: String,
    val confidence: Float,
    val description: String
)

data class SecurityAuditReport(
    val deviceAddress: String,
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val securityScore: Int, // 0 - 100
    val chipsetFingerprint: ChipsetFingerprint,
    val findings: List<AuditFinding>
)

object BleSecurityAuditor {

    fun fingerprintChipset(device: BleDevice): ChipsetFingerprint {
        val mac = device.address.uppercase()
        val mfrData = device.advertisementRecord.manufacturerData
        val serviceUuids = device.advertisementRecord.serviceUuids.map { it.toString().lowercase() }

        return when {
            // Espressif ESP32 detection (OUI or Manufacturer ID 0x02E5)
            mfrData.containsKey(0x02E5) ||
                    mac.startsWith("78:21:84") || mac.startsWith("24:0A:C4") ||
                    mac.startsWith("30:AE:A4") || mac.startsWith("AC:67:B2") -> {
                ChipsetFingerprint(
                    chipset = "ESP32-S3 / ESP32-C3",
                    vendor = "Espressif Systems",
                    architecture = "Xtensa LX7 / RISC-V 32-bit",
                    confidence = 0.95f,
                    description = "Detected via Espressif Bluetooth SIG Company ID 0x02E5 and registered OUI MAC prefix."
                )
            }
            // Nordic Semiconductor nRF52/nRF53 detection
            mfrData.containsKey(0x0059) ||
                    serviceUuids.any { it.contains("6e400001") } ||
                    mac.startsWith("C8:FD:19") || mac.startsWith("F0:C7:7F") || mac.startsWith("EA:D3:52") -> {
                ChipsetFingerprint(
                    chipset = "nRF52840 / nRF52832",
                    vendor = "Nordic Semiconductor",
                    architecture = "ARM Cortex-M4F 64MHz",
                    confidence = 0.92f,
                    description = "Identified via Nordic UART Service (NUS) profile and Nordic Semiconductor SIG ID."
                )
            }
            // Texas Instruments CC26xx
            mfrData.containsKey(0x000D) || serviceUuids.any { it.contains("f000aa00") } || mac.startsWith("B0:D5:CC") -> {
                ChipsetFingerprint(
                    chipset = "SimpleLink CC2640 / CC2652R",
                    vendor = "Texas Instruments",
                    architecture = "ARM Cortex-M4F + Sensor Controller",
                    confidence = 0.88f,
                    description = "Identified via TI SensorTag GATT UUID profile and Company ID 0x000D."
                )
            }
            // Apple iBeacon / AirTag hardware
            mfrData.containsKey(0x004C) -> {
                ChipsetFingerprint(
                    chipset = "Apple Custom Silicon (U1 / W-Series)",
                    vendor = "Apple, Inc.",
                    architecture = "Proprietary Ultra-Wideband & BLE SoC",
                    confidence = 0.95f,
                    description = "Recognized via Apple Company Identifier 0x004C and iBeacon broadcast encapsulation."
                )
            }
            // Ruuvi Innovations
            mfrData.containsKey(0x0499) -> {
                ChipsetFingerprint(
                    chipset = "nRF52832 Ruuvi Environmental Tag",
                    vendor = "Ruuvi Innovations Ltd.",
                    architecture = "ARM Cortex-M4F with BME280 sensor suite",
                    confidence = 0.94f,
                    description = "Recognized via Ruuvi Innovations Manufacturer Data 0x0499."
                )
            }
            // Generic fallback
            else -> {
                ChipsetFingerprint(
                    chipset = "Generic BLE 5.x Microcontroller",
                    vendor = device.advertisementRecord.companyName ?: "Generic Vendor",
                    architecture = "Embedded MCU",
                    confidence = 0.50f,
                    description = "Standard Bluetooth LE broadcast signature without proprietary vendor hooks."
                )
            }
        }
    }

    fun auditDeviceSecurity(
        device: BleDevice,
        services: List<GattServiceInfo>
    ): SecurityAuditReport {
        val findings = mutableListOf<AuditFinding>()
        val fingerprint = fingerprintChipset(device)

        var totalDeduction = 0

        // Check 1: Insecure Unauthenticated Nordic UART or Serial Service
        val nusService = services.find { it.uuid.toString().lowercase().contains("6e400001") }
        if (nusService != null) {
            val rx = nusService.characteristics.find { it.uuid.toString().lowercase().contains("6e400002") }
            if (rx != null && rx.permissions and 0x20 == 0) { // No write encryption
                val f = AuditFinding(
                    title = "Unencrypted Serial REPL / UART Console Exposed",
                    severity = Severity.HIGH,
                    targetUuid = rx.uuid.toString(),
                    description = "Nordic UART RX characteristic accepts raw writes without requiring bonding or authenticated encryption.",
                    recommendation = "Enforce Bluetooth LE Secure Connections (SC) bonding and require PERMISSION_WRITE_ENCRYPTED."
                )
                findings.add(f)
                totalDeduction += f.severity.scoreImpact
            }
        }

        // Check 2: Sensitive Characteristics writable without encryption
        for (s in services) {
            for (c in s.characteristics) {
                if (c.isWritable || c.isWritableWithoutResponse) {
                    val isEncrypted = (c.permissions and 0x20) != 0 || (c.permissions and 0x40) != 0
                    if (!isEncrypted && !isStandardPublicCharacteristic(c.uuid)) {
                        val f = AuditFinding(
                            title = "Unprotected Custom Writable Register",
                            severity = Severity.MEDIUM,
                            targetUuid = c.uuid.toString(),
                            description = "Characteristic '${c.name}' allows arbitrary central writes with no encryption validation.",
                            recommendation = "Restrict access to bonded devices or authenticate commands with a cryptographic token."
                        )
                        findings.add(f)
                        totalDeduction += f.severity.scoreImpact
                    }
                }
            }
        }

        // Check 3: Missing Bonding on Device Info Firmware Strings
        val devInfoService = services.find { it.uuid.toString().lowercase().contains("180a") }
        if (devInfoService != null) {
            val f = AuditFinding(
                title = "Firmware Revision Disclosed Without Authentication",
                severity = Severity.LOW,
                targetUuid = devInfoService.uuid.toString(),
                description = "Firmware and hardware revision strings are openly readable, allowing attackers to inventory vulnerable firmware versions.",
                recommendation = "Disable Device Information Service (0x180A) in production firmware or randomize build strings."
            )
            findings.add(f)
            totalDeduction += f.severity.scoreImpact
        }

        // Check 4: SweynTooth / BrakTooth Vulnerability Risk profiling
        if (fingerprint.chipset.contains("ESP32") || fingerprint.chipset.contains("nRF52")) {
            val f = AuditFinding(
                title = "SweynTooth / Link-Layer Malformed PDU Advisory",
                severity = Severity.INFO,
                targetUuid = null,
                description = "Legacy SDK revisions for ${fingerprint.chipset} contain known buffer handling anomalies when receiving truncated L2CAP frames.",
                recommendation = "Ensure the microcontroller runs latest ESP-IDF v5.2+ or Nordic nRF Connect SDK v2.6+."
            )
            findings.add(f)
        }

        val finalScore = (100 - totalDeduction).coerceIn(15, 100)

        return SecurityAuditReport(
            deviceAddress = device.address,
            deviceName = device.displayName,
            securityScore = finalScore,
            chipsetFingerprint = fingerprint,
            findings = findings
        )
    }

    private fun isStandardPublicCharacteristic(uuid: UUID): Boolean {
        val s = uuid.toString().lowercase()
        return s.contains("2a00") || s.contains("2a01") || s.contains("2a04")
    }
}
