package com.blepulse.android.terminal

import com.blepulse.android.core.terminal.DisplayMode
import com.blepulse.android.core.terminal.LineEnding
import com.blepulse.android.core.terminal.MessageDirection
import com.blepulse.android.core.terminal.TerminalMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleTerminalEngineTest {

    @Test
    fun testLineEndingBytes() {
        assertArrayEquals(byteArrayOf(0x0D, 0x0A), LineEnding.CRLF.bytes)
        assertArrayEquals(byteArrayOf(0x0A), LineEnding.LF.bytes)
        assertArrayEquals(byteArrayOf(0x0D), LineEnding.CR.bytes)
        assertArrayEquals(byteArrayOf(), LineEnding.NONE.bytes)
    }

    @Test
    fun testTerminalMessageCreation() {
        val msg = TerminalMessage(
            direction = MessageDirection.OUTGOING,
            text = "AT+GMR",
            rawBytes = "AT+GMR\r\n".toByteArray(Charsets.US_ASCII)
        )

        assertEquals(MessageDirection.OUTGOING, msg.direction)
        assertEquals("AT+GMR", msg.text)
        assertEquals(8, msg.rawBytes.size)
    }

    @Test
    fun testDisplayModeValues() {
        assertEquals("ASCII Text", DisplayMode.TEXT.label)
        assertEquals("Hexadecimal", DisplayMode.HEX.label)
        assertEquals("Hex + Text", DisplayMode.HEX_AND_TEXT.label)
    }
}
