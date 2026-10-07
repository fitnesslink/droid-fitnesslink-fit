package com.fitnesslink.fit.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor

/**
 * "Who This Program Is For": the paragraph plus optional "Best for"
 * bullets (iOS `ProgramAudienceView`). Shared by Program Detail and My
 * Program's Overview.
 */
@Composable
fun ProgramAudienceView(whoIsFor: String, bestFor: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (whoIsFor.isNotEmpty()) {
            Text(whoIsFor, fontSize = 14.sp, color = TextSecondaryColor)
        }
        if (bestFor.isNotEmpty()) {
            Text("Best for", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            bestFor.forEach { item ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = FLPrimary,
                        modifier = Modifier.padding(top = 3.dp).size(13.dp)
                    )
                    Text(item, fontSize = 14.sp, color = TextSecondaryColor)
                }
            }
        }
    }
}
