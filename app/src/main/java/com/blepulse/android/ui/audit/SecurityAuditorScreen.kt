package com.blepulse.android.ui.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blepulse.android.core.audit.AuditFinding
import com.blepulse.android.core.audit.BleSecurityAuditor
import com.blepulse.android.core.audit.Severity
import com.blepulse.android.core.ble.BleRepository
import com.blepulse.android.ui.common.StatusBadge
import com.blepulse.android.ui.theme.BleCyan
import com.blepulse.android.ui.theme.CriticalRed
import com.blepulse.android.ui.theme.DarkBorder
import com.blepulse.android.ui.theme.DarkSlateBg
import com.blepulse.android.ui.theme.DarkSurface
import com.blepulse.android.ui.theme.DarkSurfaceElevated
import com.blepulse.android.ui.theme.ElectricViolet
import com.blepulse.android.ui.theme.SignalGreen
import com.blepulse.android.ui.theme.TextMuted
import com.blepulse.android.ui.theme.TextPrimary
import com.blepulse.android.ui.theme.TextSecondary
import com.blepulse.android.ui.theme.WarningAmber

@Composable
fun SecurityAuditorScreen(
    repository: BleRepository
) {
    val activeConn by repository.activeConnection.collectAsState()
    val discoveredDevices by repository.discoveredDevices.collectAsState()

    if (activeConn == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSlateBg)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Peripheral Connected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to a target device to run the Security & Chipset Audit",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val conn = activeConn!!
    val services by conn.services.collectAsState()

    val targetDevice = remember(discoveredDevices, conn.address) {
        discoveredDevices.find { it.address == conn.address } ?: com.blepulse.android.core.model.BleDevice(
            address = conn.address,
            name = "Connected Peripheral",
            rssi = -60,
            smoothedRssi = -60.0
        )
    }

    val auditReport = remember(targetDevice, services) {
        BleSecurityAuditor.auditDeviceSecurity(targetDevice, services)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateBg)
    ) {
        // Top Toolbar
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Security & Hardware Auditor",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Target: ${conn.address}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = BleCyan
                        )
                    }

                    // Security Score Pill
                    val scoreColor = when {
                        auditReport.securityScore >= 80 -> SignalGreen
                        auditReport.securityScore >= 60 -> BleCyan
                        auditReport.securityScore >= 40 -> WarningAmber
                        else -> CriticalRed
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scoreColor.copy(alpha = 0.15f))
                            .border(1.dp, scoreColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "SCORE: ${auditReport.securityScore}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = scoreColor
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Chipset Fingerprint Card
            item {
                val fp = auditReport.chipsetFingerprint
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .border(1.dp, BleCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = BleCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chipset Fingerprint",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        StatusBadge(text = "${(fp.confidence * 100).toInt()}% Confidence", color = SignalGreen)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = fp.chipset,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BleCyan
                    )
                    Text(
                        text = "Vendor: ${fp.vendor} | Arch: ${fp.architecture}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = fp.description,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Findings Header
            item {
                Text(
                    text = "Security Findings (${auditReport.findings.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (auditReport.findings.isEmpty()) {
                item {
                    Text(
                        text = "No security vulnerabilities identified for this profile.",
                        fontSize = 12.sp,
                        color = SignalGreen
                    )
                }
            } else {
                items(auditReport.findings, key = { it.id }) { finding ->
                    FindingCard(finding = finding)
                }
            }
        }
    }
}

@Composable
fun FindingCard(finding: AuditFinding) {
    val col = when (finding.severity) {
        Severity.CRITICAL -> CriticalRed
        Severity.HIGH -> CriticalRed
        Severity.MEDIUM -> WarningAmber
        Severity.LOW -> BleCyan
        Severity.INFO -> ElectricViolet
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = finding.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(text = finding.severity.label, color = col)
        }

        if (finding.targetUuid != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Target: ${finding.targetUuid}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = finding.description,
            fontSize = 11.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Recommendation: ${finding.recommendation}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = SignalGreen
        )
    }
}
