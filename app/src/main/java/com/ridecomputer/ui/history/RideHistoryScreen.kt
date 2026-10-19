package com.ridecomputer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomputer.data.RideRecord
import com.ridecomputer.ui.theme.AccentAmber
import com.ridecomputer.ui.theme.AccentBlue
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.AccentRed
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
fun RideHistoryScreen(
    rides: List<RideRecord>,
    onBack: () -> Unit,
    onDeleteRide: (String) -> Unit,
    onClearAll: () -> Unit,
    onImportGpx: () -> Unit = {},
    onSelectRideForMap: (RideRecord) -> Unit = {},
) {
    var rideToDelete by remember { mutableStateOf<RideRecord?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // Alert dialog for single ride deletion
    rideToDelete?.let { ride ->
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateStr = dateFmt.format(Date(ride.startTime))
        val timeSpan = "${timeFmt.format(Date(ride.startTime))} - ${timeFmt.format(Date(ride.endTime))}"

        AlertDialog(
            onDismissRequest = { rideToDelete = null },
            title = {
                Text(
                    text = "Hapus Riwayat?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus data perjalanan ini?",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$dateStr ($timeSpan)",
                        color = AccentAmber,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Jarak: ${"%.2f".format(ride.distanceKm)} km • Durasi: ${formatDuration(ride.durationSeconds)}",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRide(ride.id)
                        rideToDelete = null
                    }
                ) {
                    Text(
                        text = "HAPUS",
                        color = AccentRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { rideToDelete = null }) {
                    Text(
                        text = "BATAL",
                        color = TextSecondary,
                    )
                }
            },
            containerColor = DashboardSurface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    // Alert dialog for clearing all rides
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = {
                Text(
                    text = "Hapus Semua Riwayat?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Text(
                    text = "Semua riwayat perjalanan (${rides.size} catatan) akan dihapus secara permanen.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    }
                ) {
                    Text(
                        text = "HAPUS SEMUA",
                        color = AccentRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(
                        text = "BATAL",
                        color = TextSecondary,
                    )
                }
            },
            containerColor = DashboardSurface,
            shape = RoundedCornerShape(16.dp),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "← BACK",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Text(
                text = "RIDE HISTORY",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImportGpx) {
                    Text(
                        text = "IMPORT",
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
                if (rides.isNotEmpty()) {
                    TextButton(onClick = { showClearAllConfirm = true }) {
                        Text(
                            text = "CLEAR",
                            color = AccentRed,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (rides.isEmpty()) {
            EmptyHistoryState(onBack = onBack, onImportGpx = onImportGpx)
        } else {
            // Summary banner
            val totalDistance = rides.sumOf { it.distanceKm.toDouble() }
            val totalTime = rides.sumOf { it.durationSeconds }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DashboardSurface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                SummaryItem(label = "TOTAL RIDES", value = "${rides.size}")
                SummaryItem(label = "TOTAL DIST", value = "%.1f km".format(totalDistance))
                SummaryItem(label = "TOTAL TIME", value = formatDuration(totalTime))
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rides, key = { it.id }) { ride ->
                    RideHistoryCard(
                        ride = ride,
                        onRequestDelete = { rideToDelete = ride },
                        onOpenMap = { onSelectRideForMap(ride) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RideHistoryCard(
    ride: RideRecord,
    onRequestDelete: () -> Unit,
    onOpenMap: () -> Unit = {},
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(ride.startTime))
    val startTimeStr = timeFormat.format(Date(ride.startTime))
    val finishTimeStr = timeFormat.format(Date(ride.endTime))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DashboardSurface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        // Line 1: Date & Imported Badge & Actions (MAP + DELETE)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedDate,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (ride.isImported) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "IMPORTED",
                        color = AccentAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(DashboardSurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ride.hasRoute) {
                    TextButton(
                        onClick = onOpenMap,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "MAP",
                            color = AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(
                    onClick = onRequestDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "DELETE",
                        color = AccentRed.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Optional Route Name
        if (!ride.routeName.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = ride.routeName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(6.dp))

        // Line 2: Waktu Start sampai Finish
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DashboardSurfaceVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "START ",
                    color = AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = startTimeStr,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = "→",
                color = TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FINISH ",
                    color = AccentAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = finishTimeStr,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Distance & Duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.2f".format(ride.distanceKm),
                    color = TextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = " km",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Text(
                text = formatDuration(ride.durationSeconds),
                color = AccentGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Detail Stats: Speed, Elevation, & HR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DetailStat(
                label = "AVG SPEED",
                value = "%.1f km/h".format(ride.avgSpeedKmh),
            )
            DetailStat(
                label = "MAX SPEED",
                value = "%.1f km/h".format(ride.maxSpeedKmh),
            )
            DetailStat(
                label = "ELEVATION",
                value = ride.elevationGainM?.let { "+%.0f m".format(it) } ?: "--",
            )
            DetailStat(
                label = "AVG HR",
                value = ride.avgHeartRate?.let { "$it bpm" } ?: "--",
            )
            DetailStat(
                label = "MAX HR",
                value = ride.maxHeartRate?.let { "$it bpm" } ?: "--",
            )
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            color = TextDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = value,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EmptyHistoryState(onBack: () -> Unit, onImportGpx: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "NO RIDES YET",
            color = TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Completed rides or imported GPX tracks\nwill appear here.",
            color = TextDim,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onImportGpx,
                colors = ButtonDefaults.buttonColors(containerColor = DashboardSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "IMPORT GPX",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "DASHBOARD",
                    color = DashboardBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
