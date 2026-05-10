package com.fitnesslink.fit.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * FA-98 — Billing screen. The billing API isn't wired client-side yet,
 * so the page renders with a sensible default subscription summary.
 * The "Manage" button deep-links to the Play Store subscriptions hub
 * where Play-billed customers can change or cancel; if no app handles
 * the intent we fall back to the web URL.
 */
@Composable
fun BillingScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Placeholder until BillingApi is plumbed. Renewal: 30 days out.
    val plan = "FitnessLink Pro"
    val status = "Active"
    val renewalDate = remember30Days()
    val price = "$9.99 / month"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Billing",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Plan card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(White)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(status = status)
                }
                Text(
                    text = price,
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                LabeledRow(label = "Next renewal", value = renewalDate)
                LabeledRow(label = "Billing source", value = "Google Play")
            }

            // Manage CTA — Play Store first, web fallback if no Play app.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FLPrimary)
                    .clickable {
                        val playIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/account/subscriptions")
                        ).setPackage("com.android.vending")
                        if (playIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(playIntent)
                        } else {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/account/subscriptions")
                                )
                            )
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Manage subscription",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }

            Text(
                text = "Server-billed plans manage from your account on the FitnessLink web app.",
                fontSize = 11.sp,
                color = TextSecondaryColor
            )
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = when (status.lowercase()) {
        "active" -> FLPrimary.copy(alpha = 0.12f) to FLPrimary
        "trial" -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
        "cancelled" -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        else -> TextSecondaryColor.copy(alpha = 0.15f) to TextSecondaryColor
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
    }
}

private fun remember30Days(): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 30)
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
}
