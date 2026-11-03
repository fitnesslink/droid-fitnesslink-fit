package com.fitnesslink.fit.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.ui.theme.FLPrimary

@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit,
    onLogin: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(R.drawable.welcome),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black), // theme-exempt: over media
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "FitnessLink",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(134.dp)
                    .padding(top = 40.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Start your fitness journey",
                fontSize = 20.sp,
                color = Color.White // theme-exempt: over media
            )
            Text(
                text = "Fitness for everyone everywhere",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White // theme-exempt: over media
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(FLPrimary, RoundedCornerShape(25.dp))
                    .clickable(onClick = onSignUp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign Up",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White // theme-exempt: over media
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // iOS 21ce001: say up front that the app needs a subscription.
            Text(
                text = "A FitnessLink subscription is required to use the app. New members can start with a free trial.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f), // theme-exempt: over media
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Already have an account? ",
                    fontSize = 14.sp,
                    color = Color.White // theme-exempt: over media
                )
                Text(
                    text = "Login",
                    fontSize = 14.sp,
                    color = FLPrimary,
                    modifier = Modifier.clickable(onClick = onLogin)
                )
            }
        }
    }
}
