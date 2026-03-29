package com.fitnesslink.fit.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.White
import kotlin.math.roundToInt

@Composable
fun WorkoutCompletedScreen(
    workoutName: String,
    exerciseCount: Int,
    totalSets: Int,
    duration: String,
    onDismiss: () -> Unit
) {
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { animateIn = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image (same as WelcomeScreen)
        Image(
            painter = painterResource(R.drawable.welcome),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay (same as WelcomeScreen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black)
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Logo + Close
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp)
            ) {
                // Logo
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "FitnessLink",
                    modifier = Modifier
                        .width(134.dp)
                        .align(Alignment.Center)
                )

                // X button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Checkmark circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(FLPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Headline
            Text(
                text = "WORKOUT COMPLETED!",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Workout name
            Text(
                text = workoutName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // RPE Slider
            RPESlider()

            Spacer(modifier = Modifier.height(28.dp))

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(value = duration, label = "Duration", icon = "clock")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(White.copy(alpha = 0.15f))
                )
                StatItem(value = "$exerciseCount", label = "Exercises", icon = "run")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(White.copy(alpha = 0.15f))
                )
                StatItem(value = "$totalSets", label = "Total Sets", icon = "repeat")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Share button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(FLPrimary)
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Share",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(
                when (icon) {
                    "clock" -> R.drawable.ic_launcher_foreground // placeholder
                    "run" -> R.drawable.ic_launcher_foreground
                    "repeat" -> R.drawable.ic_launcher_foreground
                    else -> R.drawable.ic_launcher_foreground
                }
            ),
            contentDescription = null,
            tint = FLPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = White
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun RPESlider() {
    var rpeValue by remember { mutableFloatStateOf(5f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val minRPE = 1f
    val maxRPE = 10f
    val normalizedValue = (rpeValue - minRPE) / (maxRPE - minRPE)

    val rpeLabel = when (rpeValue.toInt()) {
        in 1..2 -> "Very Light"
        in 3..4 -> "Light"
        in 5..6 -> "Moderate"
        in 7..8 -> "Hard"
        9 -> "Very Hard"
        10 -> "Max Effort"
        else -> ""
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "How hard was it?",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Slider track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val x = change.position.x.coerceIn(0f, trackWidthPx)
                        val raw = minRPE + (x / trackWidthPx) * (maxRPE - minRPE)
                        rpeValue = (raw * 2).roundToInt() / 2f
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FLPrimary.copy(alpha = 0.25f))
            )

            // Filled track
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = normalizedValue)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FLPrimary)
            )

            // Thumb
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = ((trackWidthPx * normalizedValue) - with(density) { 12.dp.toPx() }).roundToInt(),
                            y = 0
                        )
                    }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(FLPrimary)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scale labels + value pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("1", fontSize = 11.sp, color = White.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.weight(1f))

            // Value pill
            Row(
                modifier = Modifier
                    .background(White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayValue = if (rpeValue % 1f == 0f) "${rpeValue.toInt()}" else "%.1f".format(rpeValue)
                Text(displayValue, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("RPE", fontSize = 12.sp, color = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("·", fontSize = 12.sp, color = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(rpeLabel, fontSize = 12.sp, color = Color.Black)
            }

            Spacer(modifier = Modifier.weight(1f))
            Text("10", fontSize = 11.sp, color = White.copy(alpha = 0.4f))
        }
    }
}
