package com.blepulse.android.decoder

import com.blepulse.android.core.decoder.DataFormat
import com.blepulse.android.core.decoder.DataTypeParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteOrder

class DataTypeParsersTest {

    @Test
    fun testHexConversion() {
        val bytes = byteArrayOf(0x01, 0x02, 0xA3.toByte(), 0xFF.toByte())
        val hex = DataTypeParsers.toHexString(bytes)
        assertEquals("01 02 A3 FF", hex)

        val parsed = DataTypeParsers.parseHexString("0102A3FF")
        assertEquals(4, parsed.size)
        assertEquals(0x01.toByte(), parsed[0])
        assertEquals(0xFF.toByte(), parsed[3])
    }

    @Test
    fun testIntegersLEandBE() {
        val bytes = byteArrayOf(0x34, 0x12) // 0x1234 = 4660
        val u16Le = DataTypeParsers.toUInt16(bytes, ByteOrder.LITTLE_ENDIAN)
        val u16Be = DataTypeParsers.toUInt16(bytes, ByteOrder.BIG_ENDIAN)
        assertEquals(4660, u16Le)
        assertEquals(0x3412, u16Be)

        val encodedLe = DataTypeParsers.encodeValue(DataFormat.UINT16_LE, "4660")
        assertEquals(0x34.toByte(), encodedLe[0])
        assertEquals(0x12.toByte(), encodedLe[1])
    }

    @Test
    fun testFloat32() {
        val encoded = DataTypeParsers.encodeValue(DataFormat.FLOAT32_LE, "23.5")
        val decoded = DataTypeParsers.toFloat32(encoded, ByteOrder.LITTLE_ENDIAN)
        assertNotNull(decoded)
        assertEquals(23.5f, decoded!!, 0.001f)
    }

    @Test
    fun testSFloat11073() {
        // IEEE 11073 SFloat: value = 37.0
        // Mantissa = 370 (0x0172), Exponent = -1 (0xF) -> Raw = 0xF172 (Little-Endian: 0x72, 0xF1)
        val bytes = byteArrayOf(0x72, 0xF1.toByte())
        val decoded = DataTypeParsers.toSFloat11073(bytes, ByteOrder.LITTLE_ENDIAN)
        assertNotNull(decoded)
        assertEquals(37.0, decoded!!, 0.01)
    }

    @Test
    fun testBinaryBits() {
        val bytes = byteArrayOf(0b10100101.toByte())
        val bin = DataTypeParsers.toBinaryString(bytes)
        assertEquals("10100101", bin)
    }
}
