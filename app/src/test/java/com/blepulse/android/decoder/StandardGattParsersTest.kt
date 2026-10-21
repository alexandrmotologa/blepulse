package com.blepulse.android.decoder

import com.blepulse.android.core.decoder.StandardGattParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StandardGattParsersTest {

    @Test
    fun testParseHeartRate8Bit() {
        // Flag: 0x00 (8-bit BPM, no extra fields), BPM: 78
        val payload = byteArrayOf(0x00, 78)
        val hr = StandardGattParsers.parseHeartRate(payload)
        assertNotNull(hr)
        assertEquals(78, hr!!.bpm)
    }

    @Test
    fun testParseHeartRate16BitWithRrIntervals() {
        // Flag: 0x11 (bit 0: 16-bit BPM, bit 4: RR-Intervals present)
        // BPM: 0x0055 = 85
        // RR: 0x0400 = 1024 -> 1000 ms
        val payload = byteArrayOf(
            0x11,
            0x55, 0x00,
            0x00, 0x04
        )
        val hr = StandardGattParsers.parseHeartRate(payload)
        assertNotNull(hr)
        assertEquals(85, hr!!.bpm)
        assertEquals(1, hr.rrIntervalsMs.size)
        assertEquals(1000, hr.rrIntervalsMs[0])
    }

    @Test
    fun testParseBatteryLevel() {
        val payload = byteArrayOf(94)
        val bat = StandardGattParsers.parseBatteryLevel(payload)
        assertEquals(94, bat)
    }

    @Test
    fun testParseEnvironmentalTempAndHumidity() {
        // Temp: 2350 = 23.50 C (0x092E LE: 0x2E, 0x09)
        val tempPayload = byteArrayOf(0x2E, 0x09)
        val temp = StandardGattParsers.parseEnvironmentalTemperature(tempPayload)
        assertNotNull(temp)
        assertEquals(23.50, temp!!, 0.001)

        // Humidity: 4560 = 45.60 % (0x11D0 LE: 0xD0, 0x11)
        val humPayload = byteArrayOf(0xD0.toByte(), 0x11)
        val hum = StandardGattParsers.parseEnvironmentalHumidity(humPayload)
        assertNotNull(hum)
        assertEquals(45.60, hum!!, 0.001)
    }
}
