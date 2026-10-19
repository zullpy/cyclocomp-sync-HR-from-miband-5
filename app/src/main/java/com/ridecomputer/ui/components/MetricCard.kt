package com.ridecomputer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomputer.ui.theme.DashboardSurface
import com.ridecomputer.ui.theme.TextDim
import com.ridecomputer.ui.theme.TextPrimary
import com.ridecomputer.ui.theme.TextSecondary

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 32.sp,
    available: Boolean = true,
    statusText: String? = null,
) {
    Column(
        modifier = modifier
            .background(DashboardSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
        Text(
            text = if (available) value else "--",
            color = if (available) TextPrimary else TextDim,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = if (statusText != null && !available) statusText else unit,
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}
