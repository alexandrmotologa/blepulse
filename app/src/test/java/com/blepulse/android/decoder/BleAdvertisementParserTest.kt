package com.blepulse.android.decoder

import com.blepulse.android.core.decoder.BleAdvertisementParser
import com.blepulse.android.core.model.EddystoneData
import com.blepulse.android.core.model.ProximityZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleAdvertisementParserTest {

    @Test
    fun testParseIBeacon() {
        // Apple iBeacon advertisement payload
        // 0x02, 0x01, 0x06 (Flags)
        // 0x1A, 0xFF (Manufacturer Data length 26)
        // 0x4C, 0x00 (Apple Company ID 0x004C)
        // 0x02, 0x15 (iBeacon marker)
        // 16 bytes Proximity UUID: E2C56DB5-DFFB-48D2-B060-D0F5A71096E0
        // 0x00, 0x65 (Major = 101)
        // 0x00, 0xCA (Minor = 202)
        // 0xC5 (Measured power = -59 dBm)
        val raw = byteArrayOf(
            0x02, 0x01, 0x06,
            0x1A, 0xFF.toByte(),
            0x4C, 0x00,
            0x02, 0x15,
            0xE2.toByte(), 0xC5.toByte(), 0x6D.toByte(), 0xB5.toByte(),
            0xDF.toByte(), 0xFB.toByte(), 0x48.toByte(), 0xD2.toByte(),
            0xB0.toByte(), 0x60.toByte(), 0xD0.toByte(), 0xF5.toByte(),
            0xA7.toByte(), 0x10.toByte(), 0x96.toByte(), 0xE0.toByte(),
            0x00, 0x65,
            0x00, 0xCA.toByte(),
            0xC5.toByte()
        )

        val record = BleAdvertisementParser.parse(raw, currentRssi = -60)
        assertNotNull(record.iBeaconData)
        val ibeacon = record.iBeaconData!!

        assertEquals(UUID.fromString("e2c56db5-dffb-48d2-b060-d0f5a71096e0"), ibeacon.uuid)
        assertEquals(101, ibeacon.major)
        assertEquals(202, ibeacon.minor)
        assertEquals(-59, ibeacon.measuredPower)
        assertTrue(ibeacon.estimatedDistanceMeters > 0.0)
    }

    @Test
    fun testParseEddystoneUrl() {
        // Eddystone-URL: Service Data for 0xFEAA
        // AD structure:
        // Length: 0x0B (11 bytes: 1 type + 2 uuid + 1 frame + 1 tx + 1 scheme + 4 chars + 1 suffix)
        // Type: 0x16 (Service Data 16-bit UUID)
        // Service UUID: 0xAA, 0xFE (0xFEAA)
        // Frame Type: 0x10 (Eddystone-URL)
        // Tx Power: 0xF0 (-16 dBm)
        // URL Scheme: 0x01 (https://www.)
        // URL chars: 'm', 't', 'l', 'g'
        // Suffix: 0x00 (.com/)
        val raw = byteArrayOf(
            0x0B, 0x16,
            0xAA.toByte(), 0xFE.toByte(),
            0x10,
            0xF0.toByte(),
            0x01,
            'm'.code.toByte(), 't'.code.toByte(), 'l'.code.toByte(), 'g'.code.toByte(),
            0x00
        )

        val record = BleAdvertisementParser.parse(raw)
        assertNotNull(record.eddystoneData)
        assertTrue(record.eddystoneData is EddystoneData.Url)
        val urlData = record.eddystoneData as EddystoneData.Url
        assertEquals("https://www.mtlg.com/", urlData.url)
        assertEquals(-16, urlData.calibratedTxPower)
    }

    @Test
    fun testParseEddystoneUid() {
        // Frame Type: 0x00 (UID)
        // Tx: 0xEC (-20 dBm)
        // 10 bytes Namespace: 0102030405060708090A
        // 6 bytes Instance: 112233445566
        val raw = byteArrayOf(
            0x17, 0x16,
            0xAA.toByte(), 0xFE.toByte(),
            0x00,
            0xEC.toByte(),
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
            0x00, 0x00
        )

        val record = BleAdvertisementParser.parse(raw)
        assertNotNull(record.eddystoneData)
        assertTrue(record.eddystoneData is EddystoneData.Uid)
        val uid = record.eddystoneData as EddystoneData.Uid
        assertEquals("0102030405060708090A", uid.namespace)
        assertEquals("112233445566", uid.instance)
        assertEquals(-20, uid.calibratedTxPower)
    }

    @Test
    fun testEmaFilter() {
        val currentEma = -70.0
        val newRssi = -50
        val smoothed = BleAdvertisementParser.applyEmaFilter(currentEma, newRssi, alpha = 0.25)
        assertEquals(-65.0, smoothed, 0.001)
    }
}
