package com.fitnesslink.fit.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.GoalCreationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailsStep(viewModel: GoalCreationViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Define your goal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )

        FieldLabel("Goal Title")
        TextFieldCard(
            value = viewModel.title,
            onValueChange = { viewModel.title = it },
            placeholder = "e.g., Run a 5K"
        )

        FieldLabel("Description (optional)")
        TextFieldCard(
            value = viewModel.description,
            onValueChange = { viewModel.description = it },
            placeholder = "What does success look like?",
            singleLine = false,
            minHeightDp = 90
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Target")
                TextFieldCard(
                    value = viewModel.targetValue,
                    onValueChange = { v -> viewModel.targetValue = v.filter { it.isDigit() || it == '.' } },
                    placeholder = "e.g., 5",
                    keyboardType = KeyboardType.Decimal
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Unit")
                TextFieldCard(
                    value = viewModel.targetUnit,
                    onValueChange = { viewModel.targetUnit = it },
                    placeholder = "km"
                )
            }
        }

        FieldLabel("Target Date")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(White, RoundedCornerShape(10.dp))
                .clickable { showDatePicker = true }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dateFormatter.format(Date(viewModel.targetDate)),
                fontSize = 16.sp,
                color = TextPrimaryColor
            )
            Text(text = "Edit", fontSize = 14.sp, color = FLPrimary, fontWeight = FontWeight.SemiBold)
        }

        FieldLabel("Identity Statement")
        Text(
            text = "Who do you want to become?",
            fontSize = 12.sp,
            color = TextSecondaryColor
        )
        TextFieldCard(
            value = viewModel.identityStatement,
            onValueChange = { viewModel.identityStatement = it },
            placeholder = "I am someone who..."
        )

        Spacer(modifier = Modifier.height(8.dp))
        StepPrimaryButton(
            label = "Generate Habits",
            enabled = viewModel.canProceedStep2,
            onClick = viewModel::next
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = viewModel.targetDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.targetDate = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondaryColor
    )
}

@Composable
private fun TextFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeightDp: Int = 0
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .then(if (minHeightDp > 0) Modifier.height(minHeightDp.dp) else Modifier)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            cursorBrush = SolidColor(FLPrimary),
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, fontSize = 16.sp, color = TextSecondaryColor)
                }
                inner()
            }
        )
    }
}

private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
