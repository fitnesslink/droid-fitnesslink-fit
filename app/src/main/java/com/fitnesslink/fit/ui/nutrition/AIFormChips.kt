package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.White

// Shared chip controls for the AI request forms (iOS `AIFormChips.swift`):
// FLPrimary when selected, BackgroundColor otherwise, rounded 8.

@Composable
fun AIChipLabel(text: String, isSelected: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (isSelected) White else TextPrimaryColor,
        modifier = modifier
            .background(if (isSelected) FLPrimary else BackgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Single-select row; the caller decides whether tapping the selected chip clears it. */
@Composable
fun <V> AISingleChipRow(options: List<Pair<V, String>>, selected: V?, onSelect: (V) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            AIChipLabel(
                text = label,
                isSelected = selected == value,
                modifier = Modifier.clickable { onSelect(value) }
            )
        }
    }
}

/** Multi-select chips that wrap onto as many rows as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AIWrapChips(options: List<String>, isSelected: (String) -> Boolean, onTap: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            AIChipLabel(text = option, isSelected = isSelected(option), modifier = Modifier.clickable { onTap(option) })
        }
    }
}
