package com.ridecomputer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomputer.gpx.GpxSummary
import com.ridecomputer.ui.theme.AccentAmber
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.DashboardBackground
import com.ridecomputer.ui.theme.DashboardSurface
import com.ridecomputer.ui.theme.DashboardSurfaceVariant
import com.ridecomputer.ui.theme.TextDim
import com.ridecomputer.ui.theme.TextPrimary
import com.ridecomputer.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GpxImportPreviewDialog(
    summary: GpxSummary,
    isDuplicate: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateString = summary.startTime?.let { dateFmt.format(Date(it)) } ?: "No timestamp"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Import GPX",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    text = "GPX FILE",
                    color = AccentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(DashboardSurfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isDuplicate) {
                    Text(
                        text = "⚠ Rute ini tampaknya sudah ada di riwayat",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Route name or fallback
                Text(
                    text = summary.name ?: "Track Activity",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = dateString,
                    color = TextDim,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(14.dp))

                // Distance & Duration Primary Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DashboardSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "DISTANCE",
                            color = TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "%.2f km".format(summary.distanceKm),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DURATION",
                            color = TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = formatDuration(summary.durationSeconds),
                            color = AccentGreen,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Stats Matrix
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricColumn(
                        label = "AVG SPEED",
                        value = if (summary.avgSpeedKmh > 0f) "%.1f km/h".format(summary.avgSpeedKmh) else "--",
                    )
                    MetricColumn(
                        label = "MAX SPEED",
                        value = if (summary.maxSpeedKmh > 0f) "%.1f km/h".format(summary.maxSpeedKmh) else "--",
                    )
                    MetricColumn(
                        label = "ELEVATION",
                        value = summary.elevationGainM?.let { "+%.0f m".format(it) } ?: "--",
                    )
                }

                if (summary.avgHeartRate != null || summary.maxHeartRate != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MetricColumn(
                            label = "AVG HR",
                            value = summary.avgHeartRate?.let { "$it bpm" } ?: "--",
                        )
                        MetricColumn(
                            label = "MAX HR",
                            value = summary.maxHeartRate?.let { "$it bpm" } ?: "--",
                        )
                        MetricColumn(
                            label = "POINTS",
                            value = "%,d".format(summary.totalPoints),
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "%,d track points recorded".format(summary.totalPoints),
                        color = TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "IMPORT",
                    color = DashboardBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "BATAL",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        },
        containerColor = DashboardSurface,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = TextDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
