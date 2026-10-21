package com.blepulse.android.core.waveform

import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.decoder.StandardGattParsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WaveformPoint(
    val timestampMs: Long,
    val value: Float
)

data class ScopeChannel(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val points: List<WaveformPoint> = emptyList(),
    val currentValue: Float = 0f,
    val minValue: Float = 0f,
    val maxValue: Float = 0f,
    val avgValue: Float = 0f
)

enum class ScopeViewMode(val label: String) {
    TIME_DOMAIN("Time Domain (Oscilloscope)"),
    FFT_SPECTRUM("Frequency Spectrum (FFT)")
}

data class ScopeState(
    val isPaused: Boolean = false,
    val viewMode: ScopeViewMode = ScopeViewMode.TIME_DOMAIN,
    val timeWindowSeconds: Int = 10,
    val autoScaleY: Boolean = true,
    val manualYMin: Float = 0f,
    val manualYMax: Float = 100f,
    val channels: List<ScopeChannel> = emptyList(),
    val sampleCount: Long = 0L,
    val selectedFftChannelIndex: Int = 0,
    val fftWindowType: FftWindowType = FftWindowType.HANNING,
    val fftResult: FftResult? = null
)

enum class ScopeStreamFormat(val label: String) {
    HEART_RATE_BPM("Heart Rate (0x2A37 BPM)"),
    UINT8("Raw Byte (0-255)"),
    INT16_LE("16-Bit Signed Integer (LE)"),
    FLOAT32_LE("32-Bit Float (LE)"),
    ACCEL_3AXIS("3-Axis Accelerometer (X, Y, Z Float32)"),
    TEMPERATURE_16("Environmental Temp (0.01 C)")
}

class WaveformEngine {

    private val _state = MutableStateFlow(ScopeState())
    val state: StateFlow<ScopeState> = _state.asStateFlow()

    private val defaultColors = listOf(
        0xFF00E5FF, // Cyan
        0xFFFF3D00, // Orange
        0xFF00E676, // Neon Green
        0xFFFFD600  // Yellow
    )

    fun initializeChannels(format: ScopeStreamFormat) {
        val channels = when (format) {
            ScopeStreamFormat.ACCEL_3AXIS -> listOf(
                ScopeChannel("ch_x", "Axis X", defaultColors[0]),
                ScopeChannel("ch_y", "Axis Y", defaultColors[1]),
                ScopeChannel("ch_z", "Axis Z", defaultColors[2])
            )
            ScopeStreamFormat.HEART_RATE_BPM -> listOf(
                ScopeChannel("ch_hr", "Heart Rate (BPM)", 0xFFFF1744) // Red
            )
            ScopeStreamFormat.TEMPERATURE_16 -> listOf(
                ScopeChannel("ch_temp", "Temperature (°C)", 0xFFFF9100) // Amber
            )
            else -> listOf(
                ScopeChannel("ch_1", "Channel 1", defaultColors[0])
            )
        }
        _state.update {
            it.copy(
                channels = channels,
                sampleCount = 0L,
                fftResult = null
            )
        }
    }

    fun setViewMode(mode: ScopeViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun setFftChannel(index: Int) {
        _state.update {
            val updated = it.copy(selectedFftChannelIndex = index)
            recalculateFft(updated)
        }
    }

    fun setFftWindowType(window: FftWindowType) {
        _state.update {
            val updated = it.copy(fftWindowType = window)
            recalculateFft(updated)
        }
    }

    fun feedData(format: ScopeStreamFormat, bytes: ByteArray) {
        if (_state.value.isPaused || bytes.isEmpty()) return

        val now = System.currentTimeMillis()
        val values = decodeValues(format, bytes)
        if (values.isEmpty()) return

        _state.update { current ->
            val updatedChannels = current.channels.mapIndexed { index, ch ->
                val v = values.getOrNull(index) ?: ch.currentValue
                val newPoints = (ch.points + WaveformPoint(now, v)).takeLast(500)
                val min = newPoints.minOfOrNull { it.value } ?: v
                val max = newPoints.maxOfOrNull { it.value } ?: v
                val avg = newPoints.map { it.value }.average().toFloat()

                ch.copy(
                    points = newPoints,
                    currentValue = v,
                    minValue = min,
                    maxValue = max,
                    avgValue = avg
                )
            }

            val updatedState = current.copy(
                channels = updatedChannels,
                sampleCount = current.sampleCount + 1
            )

            // Recalculate FFT on the selected channel
            recalculateFft(updatedState)
        }
    }

    private fun recalculateFft(state: ScopeState): ScopeState {
        val targetCh = state.channels.getOrNull(state.selectedFftChannelIndex)
            ?: state.channels.firstOrNull()

        if (targetCh == null || targetCh.points.size < 8) {
            return state.copy(fftResult = null)
        }

        val rawValues = targetCh.points.map { it.value }
        val timestamps = targetCh.points.map { it.timestampMs }
        val sampleRate = FftAnalyzer.estimateSampleRate(timestamps)

        val fft = FftAnalyzer.computeFft(
            signal = rawValues,
            samplingRateHz = sampleRate,
            windowType = state.fftWindowType,
            maxN = 256
        )

        return state.copy(fftResult = fft)
    }

    private fun decodeValues(format: ScopeStreamFormat, bytes: ByteArray): List<Float> {
        return when (format) {
            ScopeStreamFormat.HEART_RATE_BPM -> {
                val hr = StandardGattParsers.parseHeartRate(bytes)
                if (hr != null) listOf(hr.bpm.toFloat()) else emptyList()
            }
            ScopeStreamFormat.UINT8 -> {
                listOf((bytes[0].toInt() and 0xFF).toFloat())
            }
            ScopeStreamFormat.INT16_LE -> {
                val v = DataTypeParsers.toInt16(bytes)
                if (v != null) listOf(v.toFloat()) else emptyList()
            }
            ScopeStreamFormat.FLOAT32_LE -> {
                val v = DataTypeParsers.toFloat32(bytes)
                if (v != null) listOf(v) else emptyList()
            }
            ScopeStreamFormat.TEMPERATURE_16 -> {
                val v = StandardGattParsers.parseEnvironmentalTemperature(bytes)
                if (v != null) listOf(v.toFloat()) else emptyList()
            }
            ScopeStreamFormat.ACCEL_3AXIS -> {
                if (bytes.size >= 12) {
                    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val x = bb.float
                    val y = bb.float
                    val z = bb.float
                    listOf(x, y, z)
                } else if (bytes.size >= 6) {
                    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    listOf(bb.short.toFloat(), bb.short.toFloat(), bb.short.toFloat())
                } else emptyList()
            }
        }
    }

    fun togglePause() {
        _state.update { it.copy(isPaused = !it.isPaused) }
    }

    fun setTimeWindow(seconds: Int) {
        _state.update { it.copy(timeWindowSeconds = seconds) }
    }

    fun clearData() {
        _state.update { current ->
            current.copy(
                channels = current.channels.map { it.copy(points = emptyList(), currentValue = 0f) },
                sampleCount = 0L,
                fftResult = null
            )
        }
    }

    fun exportToCsv(): String {
        val s = _state.value
        val sb = StringBuilder()
        sb.append("TimestampMs,TimeElapsedSec")
        s.channels.forEach { sb.append(",${it.name}") }
        sb.appendLine()

        val primaryPoints = s.channels.firstOrNull()?.points ?: emptyList()
        val firstTs = primaryPoints.firstOrNull()?.timestampMs ?: 0L

        for (i in primaryPoints.indices) {
            val ts = primaryPoints[i].timestampMs
            val elapsed = (ts - firstTs) / 1000.0
            sb.append("$ts,${"%.3f".format(elapsed)}")
            for (ch in s.channels) {
                val valAtI = ch.points.getOrNull(i)?.value ?: 0f
                sb.append(",$valAtI")
            }
            sb.appendLine()
        }
        return sb.toString()
    }
}
