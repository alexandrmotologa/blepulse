package com.blepulse.android.core.waveform

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

enum class FftWindowType(val label: String) {
    HANNING("Hanning"),
    HAMMING("Hamming"),
    BLACKMAN("Blackman"),
    RECTANGULAR("Rectangular (None)")
}

data class FftBin(
    val binIndex: Int,
    val frequencyHz: Float,
    val magnitude: Float,
    val magnitudeDb: Float,
    val normalizedMagnitude: Float
)

data class FftResult(
    val bins: List<FftBin>,
    val peakFrequencyHz: Float,
    val peakMagnitude: Float,
    val samplingRateHz: Float,
    val dcOffset: Float,
    val nyquistFrequencyHz: Float
)

object FftAnalyzer {

    /**
     * Computes the Fast Fourier Transform of a 1D time-series signal using
     * Cooley-Tukey Radix-2 Decimation-In-Time (DIT).
     *
     * @param signal Raw sample values.
     * @param samplingRateHz The sampling rate of the signal in Hertz.
     * @param windowType Windowing function applied to reduce spectral leakage.
     * @param maxN The maximum power-of-two size (default 256).
     */
    fun computeFft(
        signal: List<Float>,
        samplingRateHz: Float = 50f,
        windowType: FftWindowType = FftWindowType.HANNING,
        maxN: Int = 256
    ): FftResult {
        if (signal.size < 4) {
            return FftResult(
                bins = emptyList(),
                peakFrequencyHz = 0f,
                peakMagnitude = 0f,
                samplingRateHz = samplingRateHz,
                dcOffset = 0f,
                nyquistFrequencyHz = samplingRateHz / 2f
            )
        }

        // Determine nearest power of 2 <= signal.size (capped at maxN)
        var n = 1
        while (n * 2 <= signal.size && n * 2 <= maxN) {
            n *= 2
        }
        if (n < 4) n = 4

        // Take last n samples
        val samples = signal.takeLast(n)

        // Compute DC offset
        val dcOffset = samples.average().toFloat()

        // Apply window function
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        for (i in 0 until n) {
            val window = when (windowType) {
                FftWindowType.HANNING -> 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
                FftWindowType.HAMMING -> 0.54 - 0.46 * cos(2.0 * PI * i / (n - 1))
                FftWindowType.BLACKMAN -> 0.42 - 0.5 * cos(2.0 * PI * i / (n - 1)) + 0.08 * cos(4.0 * PI * i / (n - 1))
                FftWindowType.RECTANGULAR -> 1.0
            }
            real[i] = (samples[i] - dcOffset) * window
            imag[i] = 0.0
        }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey Radix-2 FFT computation
        var step = 1
        while (step < n) {
            val jump = step shl 1
            val angle = -PI / step
            val wStepR = cos(angle)
            val wStepI = sin(angle)

            for (m in 0 until step) {
                var curWR = 1.0
                var curWI = 0.0
                if (m > 0) {
                    val a = angle * m
                    curWR = cos(a)
                    curWI = sin(a)
                }

                var i = m
                while (i < n) {
                    val pair = i + step
                    val tr = curWR * real[pair] - curWI * imag[pair]
                    val ti = curWR * imag[pair] + curWI * real[pair]

                    real[pair] = real[i] - tr
                    imag[pair] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti

                    i += jump
                }
            }
            step = jump
        }

        // Compute magnitude spectrum for bins 0 .. N/2 (Nyquist limit)
        val halfN = n / 2
        val bins = ArrayList<FftBin>(halfN)
        var maxMag = 0.0001f
        var peakFreq = 0f
        var peakMag = 0f

        val rawMagnitudes = FloatArray(halfN)
        for (k in 0 until halfN) {
            val mag = (sqrt(real[k] * real[k] + imag[k] * imag[k]) * 2.0 / n).toFloat()
            rawMagnitudes[k] = mag
            if (mag > maxMag) {
                maxMag = mag
            }
            // Skip DC bin (k=0) when searching for peak AC frequency
            if (k > 0 && mag > peakMag) {
                peakMag = mag
                peakFreq = k * samplingRateHz / n
            }
        }

        for (k in 0 until halfN) {
            val freq = k * samplingRateHz / n
            val mag = rawMagnitudes[k]
            val magDb = (20.0 * log10((mag + 1e-6).toDouble())).toFloat().coerceIn(-100f, 60f)
            val norm = (mag / maxMag).coerceIn(0f, 1f)

            bins.add(
                FftBin(
                    binIndex = k,
                    frequencyHz = freq,
                    magnitude = mag,
                    magnitudeDb = magDb,
                    normalizedMagnitude = norm
                )
            )
        }

        return FftResult(
            bins = bins,
            peakFrequencyHz = peakFreq,
            peakMagnitude = peakMag,
            samplingRateHz = samplingRateHz,
            dcOffset = dcOffset,
            nyquistFrequencyHz = samplingRateHz / 2f
        )
    }

    /**
     * Estimates the sampling frequency in Hz from a list of timestamped points.
     */
    fun estimateSampleRate(timestampsMs: List<Long>): Float {
        if (timestampsMs.size < 2) return 50f
        val timeSpanMs = timestampsMs.last() - timestampsMs.first()
        if (timeSpanMs <= 0) return 50f
        val count = timestampsMs.size - 1
        return (count * 1000f) / timeSpanMs
    }
}
