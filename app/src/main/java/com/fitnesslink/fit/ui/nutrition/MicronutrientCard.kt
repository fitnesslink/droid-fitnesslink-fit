package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.api.Micronutrient
import com.fitnesslink.fit.network.dto.MicroSummaryLine
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Micronutrients card (FTNSSLNK-216/217): consumed vs target for the user's
 * DISPLAYED micros (server-side preference) from the daily summary's
 * `micros` block. A day with no micro data shows an em-dash — never 0. The
 * caller hides the card entirely when every micro is turned off.
 */
@Composable
fun MicronutrientCard(
    micros: List<Micronutrient>,
    lineFor: (Micronutrient) -> MicroSummaryLine?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (micros.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Micronutrients", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(14.dp))
            }
        }
        micros.forEach { micro -> MicroBarView(micro = micro, line = lineFor(micro)) }
    }
}
