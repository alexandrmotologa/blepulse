package com.blepulse.android.core.decoder

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedHeartRate(
    val bpm: Int,
    val sensorContactDetected: Boolean?,
    val energyExpendedJoules: Int?,
    val rrIntervalsMs: List<Int>
)

data class ParsedTemperature(
    val temperatureCelsius: Double,
    val isFahrenheit: Boolean,
    val sensorLocation: String?
)

object StandardGattParsers {

    fun parseHeartRate(bytes: ByteArray): ParsedHeartRate? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        val is16BitBpm = (flags and 0x01) != 0
        val sensorContactStatus = (flags shr 1) and 0x03
        val sensorContactDetected = when (sensorContactStatus) {
            2 -> false
            3 -> true
            else -> null
        }
        val energyExpendedPresent = (flags and 0x08) != 0
        val rrIntervalsPresent = (flags and 0x10) != 0

        var offset = 1
        val bpm = if (is16BitBpm) {
            if (bytes.size < offset + 2) return null
            val value = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            value
        } else {
            val value = bytes[offset].toInt() and 0xFF
            offset += 1
            value
        }

        var energyExpended: Int? = null
        if (energyExpendedPresent && bytes.size >= offset + 2) {
            energyExpended = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
        }

        val rrIntervals = mutableListOf<Int>()
        if (rrIntervalsPresent) {
            while (bytes.size >= offset + 2) {
                val rawRr = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                val rrMs = (rawRr * 1000) / 1024
                rrIntervals.add(rrMs)
                offset += 2
            }
        }

        return ParsedHeartRate(
            bpm = bpm,
            sensorContactDetected = sensorContactDetected,
            energyExpendedJoules = energyExpended,
            rrIntervalsMs = rrIntervals
        )
    }

    fun parseBatteryLevel(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        return (bytes[0].toInt() and 0xFF).coerceIn(0, 100)
    }

    fun parseEnvironmentalTemperature(bytes: ByteArray): Double? {
        if (bytes.size < 2) return null
        val raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short
        return raw / 100.0
    }

    fun parseEnvironmentalHumidity(bytes: ByteArray): Double? {
        if (bytes.size < 2) return null
        val raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return raw / 100.0
    }
}
