package com.blepulse.android.waveform

import com.blepulse.android.core.waveform.FftAnalyzer
import com.blepulse.android.core.waveform.FftWindowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class FftAnalyzerTest {

    @Test
    fun testPureSineWavePeakDetection() {
        val sampleRate = 128f
        val numSamples = 128
        val targetFreq = 16f // 16 Hz sine wave

        // Generate 16 Hz sine wave: y(t) = sin(2 * pi * 16 * t)
        val signal = (0 until numSamples).map { n ->
            val t = n.toDouble() / sampleRate
            sin(2.0 * PI * targetFreq * t).toFloat()
        }

        val result = FftAnalyzer.computeFft(
            signal = signal,
            samplingRateHz = sampleRate,
            windowType = FftWindowType.HANNING,
            maxN = 128
        )

        assertEquals(sampleRate / 2f, result.nyquistFrequencyHz, 0.001f)
        assertEquals(64, result.bins.size) // N/2 bins

        // Peak frequency should be accurately detected at 16 Hz (+/- 1 Hz bin resolution)
        val freqError = abs(result.peakFrequencyHz - targetFreq)
        assertTrue("Peak freq was ${result.peakFrequencyHz}, expected near $targetFreq", freqError <= 1.5f)
        assertTrue("Peak magnitude was ${result.peakMagnitude}", result.peakMagnitude > 0.3f)
    }

    @Test
    fun testDcOffsetSuppression() {
        val sampleRate = 100f
        val numSamples = 64
        val dcOffset = 50f

        // Signal with huge DC offset + 10 Hz AC wave
        val signal = (0 until numSamples).map { n ->
            val t = n.toDouble() / sampleRate
            dcOffset + (2.0 * sin(2.0 * PI * 10.0 * t)).toFloat()
        }

        val result = FftAnalyzer.computeFft(
            signal = signal,
            samplingRateHz = sampleRate,
            windowType = FftWindowType.HANNING
        )

        // DC offset should be accurately estimated
        assertEquals(dcOffset, result.dcOffset, 1.0f)

        // Peak frequency must still identify the 10 Hz AC component
        val freqError = abs(result.peakFrequencyHz - 10f)
        assertTrue(freqError <= 2f)
    }

    @Test
    fun testEmptyOrSmallSignalGracefulFallback() {
        val result = FftAnalyzer.computeFft(
            signal = listOf(1f, 2f),
            samplingRateHz = 50f
        )
        assertTrue(result.bins.isEmpty())
        assertEquals(0f, result.peakFrequencyHz, 0.001f)
    }

    @Test
    fun testEstimateSampleRate() {
        // 5 samples spaced by 20ms = 100ms total -> (4 intervals / 0.08s) = 50 Hz
        val timestamps = listOf(0L, 20L, 40L, 60L, 80L)
        val rate = FftAnalyzer.estimateSampleRate(timestamps)
        assertEquals(50f, rate, 0.01f)
    }
}
