package com.blepulse.android.core.decoder

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.pow

enum class DataFormat(val label: String) {
    HEX("Hex"),
    UTF8("UTF-8 Text"),
    ASCII("ASCII"),
    UINT8("UInt8"),
    UINT16_LE("UInt16 (LE)"),
    UINT16_BE("UInt16 (BE)"),
    UINT32_LE("UInt32 (LE)"),
    UINT32_BE("UInt32 (BE)"),
    INT8("Int8"),
    INT16_LE("Int16 (LE)"),
    INT16_BE("Int16 (BE)"),
    INT32_LE("Int32 (LE)"),
    INT32_BE("Int32 (BE)"),
    FLOAT32_LE("Float32 (LE)"),
    FLOAT32_BE("Float32 (BE)"),
    SFLOAT_11073("IEEE-11073 SFloat"),
    BINARY_BITS("Binary Bits")
}

object DataTypeParsers {

    fun toHexString(bytes: ByteArray, separator: String = " "): String {
        return bytes.joinToString(separator) { "%02X".format(it) }
    }

    fun parseHexString(hex: String): ByteArray {
        val sanitized = hex.replace(" ", "").replace("0x", "", ignoreCase = true)
        if (sanitized.isEmpty()) return byteArrayOf()
        require(sanitized.length % 2 == 0) { "Hex string length must be even" }
        return ByteArray(sanitized.length / 2) { i ->
            sanitized.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun toUtf8String(bytes: ByteArray): String {
        return String(bytes, StandardCharsets.UTF_8).filter { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
    }

    fun toAsciiString(bytes: ByteArray): String {
        return String(bytes, StandardCharsets.US_ASCII).filter { it.code in 32..126 }
    }

    fun toBinaryString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { byte ->
            (7 downTo 0).map { bit ->
                if ((byte.toInt() and (1 shl bit)) != 0) '1' else '0'
            }.joinToString("")
        }
    }

    fun toUInt8(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return bytes[0].toInt() and 0xFF
    }

    fun toInt8(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return bytes[0].toInt()
    }

    fun toUInt16(bytes: ByteArray, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Int? {
        if (bytes.size < 2) return null
        val buffer = ByteBuffer.wrap(bytes).order(order)
        return buffer.short.toInt() and 0xFFFF
    }

    fun toInt16(bytes: ByteArray, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Int? {
        if (bytes.size < 2) return null
        val buffer = ByteBuffer.wrap(bytes).order(order)
        return buffer.short.toInt()
    }

    fun toUInt32(bytes: ByteArray, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Long? {
        if (bytes.size < 4) return null
        val buffer = ByteBuffer.wrap(bytes).order(order)
        return buffer.int.toLong() and 0xFFFFFFFFL
    }

    fun toInt32(bytes: ByteArray, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Int? {
        if (bytes.size < 4) return null
        val buffer = ByteBuffer.wrap(bytes).order(order)
        return buffer.int
    }

    fun toFloat32(bytes: ByteArray, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Float? {
        if (bytes.size < 4) return null
        val buffer = ByteBuffer.wrap(bytes).order(order)
        return buffer.float
    }

    /**
     * Parses IEEE 11073-20601 16-bit SFLOAT (Medical device float):
     * - Bits 0..11: Signed 12-bit mantissa (two's complement)
     * - Bits 12..15: Signed 4-bit exponent (two's complement)
     */
    fun toSFloat11073(bytes: ByteArray, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Double? {
        if (bytes.size < 2) return null
        val raw = if (order == ByteOrder.LITTLE_ENDIAN) {
            (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        } else {
            ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        }

        var mantissa = raw and 0x0FFF
        if ((mantissa and 0x0800) != 0) {
            mantissa = -((0x0FFF + 1) - mantissa)
        }

        var exponent = (raw shr 12) and 0x0F
        if ((exponent and 0x08) != 0) {
            exponent = -((0x0F + 1) - exponent)
        }

        // Special reserved values in IEEE 11073
        return when (raw) {
            0x07FE -> Double.POSITIVE_INFINITY
            0x0802 -> Double.NEGATIVE_INFINITY
            0x07FF -> Double.NaN
            0x0800 -> Double.NaN
            0x0801 -> Double.NaN
            else -> mantissa * 10.0.pow(exponent.toDouble())
        }
    }

    fun encodeValue(format: DataFormat, input: String): ByteArray {
        return when (format) {
            DataFormat.HEX -> parseHexString(input)
            DataFormat.UTF8 -> input.toByteArray(StandardCharsets.UTF_8)
            DataFormat.ASCII -> input.toByteArray(StandardCharsets.US_ASCII)
            DataFormat.UINT8 -> {
                val value = input.trim().toIntOrNull() ?: 0
                byteArrayOf((value and 0xFF).toByte())
            }
            DataFormat.INT8 -> {
                val value = input.trim().toIntOrNull() ?: 0
                byteArrayOf(value.toByte())
            }
            DataFormat.UINT16_LE -> {
                val value = input.trim().toIntOrNull() ?: 0
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
            }
            DataFormat.UINT16_BE -> {
                val value = input.trim().toIntOrNull() ?: 0
                ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array()
            }
            DataFormat.INT16_LE -> {
                val value = input.trim().toIntOrNull() ?: 0
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
            }
            DataFormat.INT16_BE -> {
                val value = input.trim().toIntOrNull() ?: 0
                ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array()
            }
            DataFormat.UINT32_LE -> {
                val value = input.trim().toLongOrNull() ?: 0L
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
            }
            DataFormat.UINT32_BE -> {
                val value = input.trim().toLongOrNull() ?: 0L
                ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array()
            }
            DataFormat.INT32_LE -> {
                val value = input.trim().toIntOrNull() ?: 0
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            }
            DataFormat.INT32_BE -> {
                val value = input.trim().toIntOrNull() ?: 0
                ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
            }
            DataFormat.FLOAT32_LE -> {
                val value = input.trim().toFloatOrNull() ?: 0.0f
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
            }
            DataFormat.FLOAT32_BE -> {
                val value = input.trim().toFloatOrNull() ?: 0.0f
                ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()
            }
            DataFormat.SFLOAT_11073 -> {
                // Approximate 11073 sfloat encoding
                val value = input.trim().toDoubleOrNull() ?: 0.0
                val mantissa = (value * 10).toInt().coerceIn(-2048, 2047)
                val exponent = -1
                val raw = (mantissa and 0x0FFF) or ((exponent and 0x0F) shl 12)
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(raw.toShort()).array()
            }
            DataFormat.BINARY_BITS -> {
                val clean = input.replace(" ", "")
                val bytes = ByteArray(clean.length / 8)
                for (i in bytes.indices) {
                    bytes[i] = clean.substring(i * 8, (i + 1) * 8).toInt(2).toByte()
                }
                bytes
            }
        }
    }

    data class ParsedPresentation(
        val hex: String,
        val utf8: String,
        val int16: Int?,
        val float32: Float?,
        val sFloat: Double?,
        val binary: String
    )

    fun parseAllPresentations(bytes: ByteArray): ParsedPresentation {
        return ParsedPresentation(
            hex = toHexString(bytes),
            utf8 = toUtf8String(bytes),
            int16 = toInt16(bytes),
            float32 = toFloat32(bytes),
            sFloat = toSFloat11073(bytes),
            binary = toBinaryString(bytes)
        )
    }
}
