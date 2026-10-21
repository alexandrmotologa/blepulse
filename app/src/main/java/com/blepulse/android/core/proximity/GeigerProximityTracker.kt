package com.blepulse.android.core.proximity

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.blepulse.android.core.decoder.BleAdvertisementParser
import com.blepulse.android.core.model.BleDevice
import com.blepulse.android.core.model.ProximityZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class GeigerState(
    val isRunning: Boolean = false,
    val targetAddress: String? = null,
    val targetName: String? = null,
    val currentRssi: Int = -80,
    val smoothedRssi: Double = -80.0,
    val estimatedDistanceMeters: Double = 3.0,
    val proximityZone: ProximityZone = ProximityZone.FAR,
    val tickFrequencyHz: Float = 1.0f,
    val isAudioEnabled: Boolean = true,
    val isHapticEnabled: Boolean = true,
    val referenceTxPower: Int = -59
)

class GeigerProximityTracker(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var geigerJob: Job? = null

    private val _state = MutableStateFlow(GeigerState())
    val state: StateFlow<GeigerState> = _state.asStateFlow()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Pre-synthesized 16-bit PCM click waveform (5ms at 1500 Hz, sample rate 44100 Hz)
    private val clickPcmBytes: ByteArray by lazy {
        val sampleRate = 44100
        val durationSamples = (sampleRate * 0.005).toInt() // 5 ms
        val pcm = ShortArray(durationSamples)
        val freq = 1500.0

        for (i in 0 until durationSamples) {
            val env = 1.0 - (i.toDouble() / durationSamples) // decaying envelope
            val wave = sin(2.0 * Math.PI * freq * i / sampleRate) * env
            pcm[i] = (wave * 30000).toInt().toShort()
        }

        val bytes = ByteArray(durationSamples * 2)
        for (i in pcm.indices) {
            bytes[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
        }
        bytes
    }

    private var audioTrack: AudioTrack? = null

    private fun initAudioTrack() {
        if (audioTrack != null) return
        val sampleRate = 44100
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(clickPcmBytes.size * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    fun startTracking(device: BleDevice) {
        stopTracking()
        initAudioTrack()

        _state.value = GeigerState(
            isRunning = true,
            targetAddress = device.address,
            targetName = device.displayName,
            currentRssi = device.rssi,
            smoothedRssi = device.smoothedRssi,
            referenceTxPower = device.txPower ?: -59
        )

        geigerJob = scope.launch {
            while (isActive) {
                val st = _state.value
                val rssi = st.smoothedRssi.toInt()

                // Map RSSI (-95 to -35) to interval (1200ms down to 40ms)
                val norm = ((rssi - (-95f)) / ((-35f) - (-95f))).coerceIn(0f, 1f)
                val intervalMs = (1200L - (norm * 1150L)).toLong().coerceIn(35L, 1200L)
                val freqHz = 1000f / intervalMs

                _state.update {
                    it.copy(
                        tickFrequencyHz = freqHz,
                        estimatedDistanceMeters = BleAdvertisementParser.calculateDistance(it.referenceTxPower, rssi),
                        proximityZone = when {
                            rssi >= -55 -> ProximityZone.IMMEDIATE
                            rssi >= -75 -> ProximityZone.NEAR
                            else -> ProximityZone.FAR
                        }
                    )
                }

                // Play click
                if (_state.value.isAudioEnabled) {
                    playTickSound()
                }

                // Haptic pulse
                if (_state.value.isHapticEnabled) {
                    triggerHaptic()
                }

                delay(intervalMs)
            }
        }
    }

    fun updateRssi(newRssi: Int) {
        _state.update { current ->
            val smoothed = BleAdvertisementParser.applyEmaFilter(current.smoothedRssi, newRssi, alpha = 0.3)
            current.copy(
                currentRssi = newRssi,
                smoothedRssi = smoothed
            )
        }
    }

    private fun playTickSound() {
        try {
            audioTrack?.write(clickPcmBytes, 0, clickPcmBytes.size)
        } catch (_: Exception) {}
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        } catch (_: Exception) {}
    }

    fun setAudioEnabled(enabled: Boolean) {
        _state.update { it.copy(isAudioEnabled = enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _state.update { it.copy(isHapticEnabled = enabled) }
    }

    fun setReferenceTxPower(txPower: Int) {
        _state.update { it.copy(referenceTxPower = txPower) }
    }

    fun stopTracking() {
        geigerJob?.cancel()
        geigerJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        _state.update { it.copy(isRunning = false) }
    }
}
