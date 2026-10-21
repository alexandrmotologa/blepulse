package com.blepulse.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.decoder.DataTypeParsers
import com.blepulse.android.core.model.RssiSample
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.CriticalRed
import com.blepulse.android.ui.theme.DarkBorder
import com.blepulse.android.ui.theme.DarkSurfaceElevated
import com.blepulse.android.ui.theme.SignalGreen
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.theme.TextPrimary
import com.blepulse.android.ui.theme.TextSecondary
import com.blepulse.android.ui.theme.WarningAmber

@Composable
fun SignalIndicator(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        rssi >= -60 -> SignalGreen
        rssi >= -75 -> BleCyan
        rssi >= -85 -> WarningAmber
        else -> CriticalRed
    }

    val activeBars = when {
        rssi >= -60 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        else -> 1
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..4) {
            val h = (i * 4).dp
            val isFilled = i <= activeBars
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .background(
                        color = if (isFilled) barColor else DarkBorder,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$rssi dBm",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = barColor
        )
    }
}

@Composable
fun SparklineView(
    samples: List<RssiSample>,
    modifier: Modifier = Modifier,
    lineColor: Color = BleCyan
) {
    if (samples.size < 2) {
        Box(modifier = modifier)
        return
    }

    Canvas(modifier = modifier) {
        val minRssi = -100f
        val maxRssi = -35f
        val range = (maxRssi - minRssi).coerceAtLeast(1f)

        val w = size.width
        val h = size.height

        val stepX = w / (samples.size - 1).coerceAtLeast(1)
        val path = Path()

        samples.forEachIndexed { i, s ->
            val normY = 1.0f - ((s.rssi.toFloat() - minRssi) / range).coerceIn(0f, 1f)
            val px = i * stepX
            val py = normY * h
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun HexDumpViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier
) {
    if (bytes.isEmpty()) {
        Text(
            text = "(empty payload)",
            color = TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceElevated, shape = RoundedCornerShape(6.dp))
            .border(1.dp, DarkBorder, shape = RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        val chunkSize = 16
        for (i in bytes.indices step chunkSize) {
            val chunk = bytes.copyOfRange(i, minOf(i + chunkSize, bytes.size))
            val hexStr = chunk.joinToString(" ") { "%02X".format(it) }.padEnd(chunkSize * 3 - 1)
            val asciiStr = chunk.map { if (it.toInt() in 32..126) it.toInt().toChar() else '.' }.joinToString("")
            val offsetStr = "%04X".format(i)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = offsetStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = hexStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = BleCyan
                )
                Text(
                    text = asciiStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
