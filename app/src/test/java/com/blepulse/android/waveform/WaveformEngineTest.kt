package com.blepulse.android.waveform

import com.blepulse.android.core.waveform.ScopeStreamFormat
import com.blepulse.android.core.waveform.WaveformEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveformEngineTest {

    @Test
    fun testWaveformEngineHeartRateStream() {
        val engine = WaveformEngine()
        engine.initializeChannels(ScopeStreamFormat.HEART_RATE_BPM)

        val chs = engine.state.value.channels
        assertEquals(1, chs.size)
        assertEquals("Heart Rate (BPM)", chs[0].name)

        // Feed heart rate 75 BPM (payload 0x00, 75)
        engine.feedData(ScopeStreamFormat.HEART_RATE_BPM, byteArrayOf(0x00, 75))
        engine.feedData(ScopeStreamFormat.HEART_RATE_BPM, byteArrayOf(0x00, 85))

        val updated = engine.state.value.channels[0]
        assertEquals(2, updated.points.size)
        assertEquals(85f, updated.currentValue, 0.001f)
        assertEquals(75f, updated.minValue, 0.001f)
        assertEquals(85f, updated.maxValue, 0.001f)
        assertEquals(80f, updated.avgValue, 0.001f)

        val csv = engine.exportToCsv()
        assertTrue(csv.contains("TimestampMs,TimeElapsedSec,Heart Rate (BPM)"))
    }

    @Test
    fun testWaveformEngine3AxisAccel() {
        val engine = WaveformEngine()
        engine.initializeChannels(ScopeStreamFormat.ACCEL_3AXIS)

        assertEquals(3, engine.state.value.channels.size)

        // 3 x Float32 LE: X=1.5, Y=-2.0, Z=9.8
        val payload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(1.5f)
            .putFloat(-2.0f)
            .putFloat(9.8f)
            .array()

        engine.feedData(ScopeStreamFormat.ACCEL_3AXIS, payload)

        val chs = engine.state.value.channels
        assertEquals(1.5f, chs[0].currentValue, 0.01f)
        assertEquals(-2.0f, chs[1].currentValue, 0.01f)
        assertEquals(9.8f, chs[2].currentValue, 0.01f)
    }
}
