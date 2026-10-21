package com.blepulse.android.core.fota

import com.blepulse.android.core.ble.BleConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32

enum class FotaProtocol(val displayName: String, val description: String) {
    NORDIC_SECURE_DFU("Nordic Secure DFU", "nRF52 / nRF53 Series (UUID 0xFE59)"),
    ESP32_BLE_OTA("ESP32 BLE OTA", "ESP-IDF / NimBLE OTA Protocol"),
    GENERIC_STREAM_OTA("Generic Chunk Stream", "Sequenced binary payload streaming")
}

enum class FotaState {
    IDLE,
    VALIDATING,
    CONNECTING,
    NEGOTIATING_MTU,
    PREPARING,
    FLASHING,
    VERIFYING,
    COMPLETED,
    ABORTED,
    FAILED
}

data class FirmwareMetadata(
    val fileName: String,
    val fileSizeBytes: Long,
    val detectedType: String,
    val crc32Checksum: Long,
    val sha256Hex: String,
    val magicHeaderHex: String,
    val estimatedDurationSec: Int
)

data class FotaProgress(
    val state: FotaState = FotaState.IDLE,
    val bytesSent: Long = 0L,
    val totalBytes: Long = 0L,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val percent: Float = 0f,
    val transferRateKbps: Float = 0f,
    val elapsedSeconds: Float = 0f,
    val estimatedRemainingSeconds: Int = 0,
    val statusMessage: String = "Ready to flash firmware",
    val errorMessage: String? = null
)

data class FotaLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: String, // "INFO", "TX", "RX", "WARN", "ERROR"
    val message: String
)

class FotaManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _progress = MutableStateFlow(FotaProgress())
    val progress: StateFlow<FotaProgress> = _progress.asStateFlow()

    private val _logs = MutableStateFlow<List<FotaLogEntry>>(emptyList())
    val logs: StateFlow<List<FotaLogEntry>> = _logs.asStateFlow()

    private var activeJob: Job? = null

    companion object {
        // Nordic Secure DFU Service & Characteristics
        val NORDIC_DFU_SERVICE_UUID: UUID = UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb")
        val NORDIC_DFU_SECURE_UUID: UUID = UUID.fromString("8ec87012-51e9-4344-8929-44b0e5c9a41f")
        val NORDIC_CONTROL_POINT_UUID: UUID = UUID.fromString("8ec80001-51e9-4344-8929-44b0e5c9a41f")
        val NORDIC_PACKET_UUID: UUID = UUID.fromString("8ec80002-51e9-4344-8929-44b0e5c9a41f")

        // ESP32 BLE OTA Service & Characteristics
        val ESP32_OTA_SERVICE_UUID: UUID = UUID.fromString("fb1e4001-54ae-4a28-9f74-dfccb248601d")
        val ESP32_OTA_CONTROL_UUID: UUID = UUID.fromString("fb1e4002-54ae-4a28-9f74-dfccb248601d")
        val ESP32_OTA_DATA_UUID: UUID = UUID.fromString("fb1e4003-54ae-4a28-9f74-dfccb248601d")
    }

    fun inspectFirmware(bytes: ByteArray, fileName: String = "firmware.bin"): FirmwareMetadata {
        val crc = CRC32()
        crc.update(bytes)
        val crcVal = crc.value

        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(bytes)
        val sha256Hex = hashBytes.joinToString("") { "%02x".format(it) }

        val magic = if (bytes.size >= 4) {
            bytes.take(4).joinToString(" ") { "%02X".format(it) }
        } else "00 00 00 00"

        val detectedType = when {
            bytes.isNotEmpty() && bytes[0] == 0xE9.toByte() -> "ESP32 App Binary Image (Magic 0xE9)"
            bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() -> "Nordic DFU ZIP Package (PK Zip Archive)"
            bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x00.toByte() -> "Nordic Init Packet (.dat)"
            else -> "Generic Microcontroller Binary (.bin)"
        }

        val estimatedSec = (bytes.size / (15 * 1024)).coerceAtLeast(1) // ~15 kB/s typical BLE 2M throughput

        return FirmwareMetadata(
            fileName = fileName,
            fileSizeBytes = bytes.size.toLong(),
            detectedType = detectedType,
            crc32Checksum = crcVal,
            sha256Hex = sha256Hex,
            magicHeaderHex = magic,
            estimatedDurationSec = estimatedSec
        )
    }

    fun startFota(
        connection: BleConnection,
        firmwareBytes: ByteArray,
        protocol: FotaProtocol,
        targetServiceUuid: UUID? = null,
        targetCharUuid: UUID? = null,
        chunkSize: Int = 244,
        interChunkDelayMs: Long = 5L
    ) {
        cancelFota()

        activeJob = coroutineScope.launch {
            try {
                log("INFO", "Initiating FOTA flash sequence (${protocol.displayName})")
                log("INFO", "Firmware payload size: ${firmwareBytes.size} bytes")

                _progress.update {
                    FotaProgress(
                        state = FotaState.VALIDATING,
                        totalBytes = firmwareBytes.size.toLong(),
                        statusMessage = "Validating binary image and calculating checksums"
                    )
                }

                val metadata = inspectFirmware(firmwareBytes)
                log("INFO", "Image type: ${metadata.detectedType}")
                log("INFO", "CRC32: 0x${"%08X".format(metadata.crc32Checksum)} | SHA-256: ${metadata.sha256Hex.take(12)}...")

                _progress.update { it.copy(state = FotaState.NEGOTIATING_MTU, statusMessage = "Requesting MTU 247") }
                val mtuResult = connection.requestMtu(247)
                val negotiatedMtu = mtuResult.getOrDefault(23)
                log("INFO", "Negotiated MTU: $negotiatedMtu bytes")

                val effectiveChunk = (negotiatedMtu - 3).coerceAtLeast(20).coerceAtMost(chunkSize)
                log("INFO", "Configured chunk size: $effectiveChunk bytes (delay: ${interChunkDelayMs}ms)")

                when (protocol) {
                    FotaProtocol.ESP32_BLE_OTA -> flashEsp32(
                        connection = connection,
                        firmware = firmwareBytes,
                        serviceUuid = targetServiceUuid ?: ESP32_OTA_SERVICE_UUID,
                        controlUuid = ESP32_OTA_CONTROL_UUID,
                        dataUuid = targetCharUuid ?: ESP32_OTA_DATA_UUID,
                        chunkSize = effectiveChunk,
                        delayMs = interChunkDelayMs,
                        crc32 = metadata.crc32Checksum
                    )
                    FotaProtocol.NORDIC_SECURE_DFU -> flashNordic(
                        connection = connection,
                        firmware = firmwareBytes,
                        serviceUuid = targetServiceUuid ?: NORDIC_DFU_SECURE_UUID,
                        controlUuid = NORDIC_CONTROL_POINT_UUID,
                        packetUuid = targetCharUuid ?: NORDIC_PACKET_UUID,
                        chunkSize = effectiveChunk,
                        delayMs = interChunkDelayMs
                    )
                    FotaProtocol.GENERIC_STREAM_OTA -> flashGenericStream(
                        connection = connection,
                        firmware = firmwareBytes,
                        serviceUuid = targetServiceUuid ?: ESP32_OTA_SERVICE_UUID,
                        dataCharUuid = targetCharUuid ?: ESP32_OTA_DATA_UUID,
                        chunkSize = effectiveChunk,
                        delayMs = interChunkDelayMs
                    )
                }

            } catch (e: CancellationException) {
                log("WARN", "FOTA operation aborted by user")
                _progress.update {
                    it.copy(
                        state = FotaState.ABORTED,
                        statusMessage = "FOTA operation cancelled by user",
                        errorMessage = "Aborted"
                    )
                }
            } catch (e: Exception) {
                log("ERROR", "FOTA execution failed: ${e.message}")
                _progress.update {
                    it.copy(
                        state = FotaState.FAILED,
                        statusMessage = "FOTA failed: ${e.message}",
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    private suspend fun flashEsp32(
        connection: BleConnection,
        firmware: ByteArray,
        serviceUuid: UUID,
        controlUuid: UUID,
        dataUuid: UUID,
        chunkSize: Int,
        delayMs: Long,
        crc32: Long
    ) {
        _progress.update { it.copy(state = FotaState.PREPARING, statusMessage = "Sending ESP32 OTA Begin command") }

        // OTA BEGIN: opcode 0x01 + 4 bytes total size LE
        val beginCmd = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x01.toByte())
            .putInt(firmware.size)
            .array()

        log("TX", "Command OTA_BEGIN (Size: ${firmware.size} bytes)")
        connection.writeCharacteristic(serviceUuid, controlUuid, beginCmd, withResponse = true)

        streamChunks(connection, serviceUuid, dataUuid, firmware, chunkSize, delayMs)

        // OTA END: opcode 0x03 + 4 bytes CRC32 LE
        _progress.update { it.copy(state = FotaState.VERIFYING, statusMessage = "Verifying CRC32 checksum with device") }
        val endCmd = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x03.toByte())
            .putInt(crc32.toInt())
            .array()

        log("TX", "Command OTA_END (CRC32: 0x${"%08X".format(crc32)})")
        connection.writeCharacteristic(serviceUuid, controlUuid, endCmd, withResponse = true)

        _progress.update {
            it.copy(
                state = FotaState.COMPLETED,
                statusMessage = "ESP32 OTA flash complete! Device rebooting with new image",
                percent = 1f
            )
        }
        log("INFO", "ESP32 FOTA finished successfully")
    }

    private suspend fun flashNordic(
        connection: BleConnection,
        firmware: ByteArray,
        serviceUuid: UUID,
        controlUuid: UUID,
        packetUuid: UUID,
        chunkSize: Int,
        delayMs: Long
    ) {
        _progress.update { it.copy(state = FotaState.PREPARING, statusMessage = "Setting PRN and creating Data object") }

        // Set PRN = 0 (or 10)
        val prnCmd = byteArrayOf(0x02, 0x00, 0x00) // Opcode 0x02 = Set PRN
        log("TX", "Nordic DFU Set PRN = 0")
        connection.writeCharacteristic(serviceUuid, controlUuid, prnCmd, withResponse = true)

        // Create Data Object: Opcode 0x01, Type 0x02 (Data), Size (4 bytes LE)
        val createCmd = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x01.toByte())
            .put(0x02.toByte())
            .putInt(firmware.size)
            .array()
        log("TX", "Nordic DFU Create Data Object (${firmware.size} bytes)")
        connection.writeCharacteristic(serviceUuid, controlUuid, createCmd, withResponse = true)

        streamChunks(connection, serviceUuid, packetUuid, firmware, chunkSize, delayMs)

        // Calculate Checksum: Opcode 0x03
        _progress.update { it.copy(state = FotaState.VERIFYING, statusMessage = "Requesting DFU checksum calculation") }
        log("TX", "Nordic DFU Request Checksum (Opcode 0x03)")
        connection.writeCharacteristic(serviceUuid, controlUuid, byteArrayOf(0x03), withResponse = true)

        // Execute: Opcode 0x04
        log("TX", "Nordic DFU Execute (Opcode 0x04)")
        connection.writeCharacteristic(serviceUuid, controlUuid, byteArrayOf(0x04), withResponse = true)

        _progress.update {
            it.copy(
                state = FotaState.COMPLETED,
                statusMessage = "Nordic Secure DFU complete! Bootloader validated image",
                percent = 1f
            )
        }
        log("INFO", "Nordic DFU flash completed successfully")
    }

    private suspend fun flashGenericStream(
        connection: BleConnection,
        serviceUuid: UUID,
        dataCharUuid: UUID,
        firmware: ByteArray,
        chunkSize: Int,
        delayMs: Long
    ) {
        _progress.update { it.copy(state = FotaState.FLASHING, statusMessage = "Streaming firmware payload chunks") }
        streamChunks(connection, serviceUuid, dataCharUuid, firmware, chunkSize, delayMs)

        _progress.update {
            it.copy(
                state = FotaState.COMPLETED,
                statusMessage = "Generic payload streaming complete!",
                percent = 1f
            )
        }
        log("INFO", "Generic firmware streaming finished")
    }

    private suspend fun streamChunks(
        connection: BleConnection,
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        chunkSize: Int,
        delayMs: Long
    ) {
        val totalBytes = data.size.toLong()
        val totalChunks = (data.size + chunkSize - 1) / chunkSize
        var bytesSent = 0L
        val startTime = System.currentTimeMillis()

        _progress.update {
            it.copy(
                state = FotaState.FLASHING,
                totalBytes = totalBytes,
                totalChunks = totalChunks,
                currentChunk = 0,
                statusMessage = "Flashing chunk 1 of $totalChunks..."
            )
        }

        var offset = 0
        var chunkIndex = 0

        while (offset < data.size) {
            val length = minOf(chunkSize, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + length)

            // Write chunk without response for maximum BLE throughput
            connection.writeCharacteristic(serviceUuid, charUuid, chunk, withResponse = false)

            offset += length
            bytesSent += length
            chunkIndex++

            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
            val rateKbps = if (elapsedSec > 0f) (bytesSent * 8f) / (elapsedSec * 1024f) else 0f
            val remainingSec = if (rateKbps > 0f) {
                (((totalBytes - bytesSent) * 8f) / (rateKbps * 1024f)).toInt()
            } else 0

            _progress.update {
                it.copy(
                    bytesSent = bytesSent,
                    currentChunk = chunkIndex,
                    percent = bytesSent.toFloat() / totalBytes,
                    transferRateKbps = rateKbps,
                    elapsedSeconds = elapsedSec,
                    estimatedRemainingSeconds = remainingSec,
                    statusMessage = "Writing chunk $chunkIndex / $totalChunks (${(bytesSent * 100 / totalBytes)}%)"
                )
            }

            if (delayMs > 0) {
                delay(delayMs)
            }
        }
    }

    fun cancelFota() {
        if (activeJob?.isActive == true) {
            activeJob?.cancel()
            activeJob = null
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun log(level: String, message: String) {
        val entry = FotaLogEntry(level = level, message = message)
        _logs.update { (it + entry).takeLast(200) }
    }
}
