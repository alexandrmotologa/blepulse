package com.blepulse.android.pcap

import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.pcap.PcapExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapExporterTest {

    @Test
    fun testPcapGlobalHeader() {
        val emptyPcap = PcapExporter.buildPcapFile(emptyList())

        // Libpcap header must be 24 bytes
        assertEquals(24, emptyPcap.size)

        val bb = ByteBuffer.wrap(emptyPcap).order(ByteOrder.BIG_ENDIAN)
        val magic = bb.int
        assertEquals(0xA1B2C3D4.toInt(), magic)

        val vMajor = bb.short.toInt()
        val vMinor = bb.short.toInt()
        assertEquals(2, vMajor)
        assertEquals(4, vMinor)

        bb.int // thiszone
        bb.int // sigfigs
        val snaplen = bb.int
        assertEquals(65535, snaplen)

        val linktype = bb.int
        // DLT_BLUETOOTH_LE_LL is 251
        assertEquals(251, linktype)
    }

    @Test
    fun testExportAdvertisementsWithDevice() {
        val testDevice = BleDevice(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Test Peripheral",
            rssi = -65,
            smoothedRssi = -65.0,
            isConnectable = true
        )

        val pcapBytes = PcapExporter.exportAdvertisements(listOf(testDevice))

        // Header 24 bytes + packet record header 16 bytes + payload
        assertTrue(pcapBytes.size > 40)

        // Check Access Address in packet payload (offset 24 + 16 = 40)
        // Little-endian representation of 0x8E89BED6 is D6 BE 89 8E
        assertEquals(0xD6.toByte(), pcapBytes[40])
        assertEquals(0xBE.toByte(), pcapBytes[41])
        assertEquals(0x89.toByte(), pcapBytes[42])
        assertEquals(0x8E.toByte(), pcapBytes[43])
    }

    @Test
    fun testMacAddressReversalInPdu() {
        val macBytes = PcapExporter.CapturedBlePacket.parseMacToBytes("11:22:33:44:55:66")
        // Little-endian reversed over the air
        assertEquals(0x66.toByte(), macBytes[0])
        assertEquals(0x55.toByte(), macBytes[1])
        assertEquals(0x44.toByte(), macBytes[2])
        assertEquals(0x33.toByte(), macBytes[3])
        assertEquals(0x22.toByte(), macBytes[4])
        assertEquals(0x11.toByte(), macBytes[5])
    }
}
