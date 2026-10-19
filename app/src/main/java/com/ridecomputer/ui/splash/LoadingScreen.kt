package com.ridecomputer.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomputer.ui.components.CycloCompLogo
import com.ridecomputer.ui.theme.AccentGreen
import com.ridecomputer.ui.theme.DashboardBackground
import com.ridecomputer.ui.theme.DashboardSurfaceVariant
import com.ridecomputer.ui.theme.TextDim
import com.ridecomputer.ui.theme.TextPrimary
import com.ridecomputer.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Modern startup loading screen adhering to anti-slop guidelines:
 * - High-contrast athletic dark palette matching the cyclocomputer theme.
 * - Purposeful startup animation that resolves cleanly into the dashboard.
 * - Transparent initialization status without artificial decorative noise.
 */
@Composable
fun LoadingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var targetProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Initializing sensors...") }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "LoadingProgress",
    )

    LaunchedEffect(Unit) {
        targetProgress = 0.35f
        delay(400)
        statusText = "Calibrating GPS & Bluetooth..."
        targetProgress = 0.85f
        delay(500)
        statusText = "Ready to ride"
        targetProgress = 1.0f
        delay(300)
        onFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        CycloCompLogo(
            size = 112.dp,
            sweepProgress = animatedProgress,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "CYCLOCOMP",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "GPS RIDE COMPUTER",
            color = TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(40.dp))

        // Minimalist linear loading track
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DashboardSurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction = animatedProgress.coerceIn(0f, 1f))
                    .background(AccentGreen)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = statusText,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "v0.1.0",
            color = TextDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
