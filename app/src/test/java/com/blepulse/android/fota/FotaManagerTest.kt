package com.blepulse.android.fota

import com.blepulse.android.core.fota.FotaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class FotaManagerTest {

    private val fotaManager = FotaManager()

    @Test
    fun testEsp32ImageDetection() {
        val bytes = ByteArray(1024)
        bytes[0] = 0xE9.toByte() // ESP32 magic byte
        bytes[1] = 0x03.toByte()

        val meta = fotaManager.inspectFirmware(bytes, "esp32_firmware.bin")

        assertTrue(meta.detectedType.contains("ESP32"))
        assertEquals(1024L, meta.fileSizeBytes)

        val crc = CRC32()
        crc.update(bytes)
        assertEquals(crc.value, meta.crc32Checksum)
    }

    @Test
    fun testNordicZipDetection() {
        val bytes = ByteArray(512)
        bytes[0] = 0x50.toByte() // 'P'
        bytes[1] = 0x4B.toByte() // 'K'
        bytes[2] = 0x03.toByte()
        bytes[3] = 0x04.toByte()

        val meta = fotaManager.inspectFirmware(bytes, "app_dfu_package.zip")

        assertTrue(meta.detectedType.contains("Nordic"))
        assertEquals("50 4B 03 04", meta.magicHeaderHex)
    }

    @Test
    fun testSha256ChecksumIntegrity() {
        val data = "BLEPulse Firmware OTA Test Payload 12345".toByteArray(Charsets.UTF_8)
        val meta = fotaManager.inspectFirmware(data, "test.bin")

        assertEquals(64, meta.sha256Hex.length) // 32 bytes hex encoded = 64 chars
        assertTrue(meta.sha256Hex.all { it in "0123456789abcdef" })
    }
}
