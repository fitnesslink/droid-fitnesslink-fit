package com.fitnesslink.fit.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.data.MockDataProvider
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

@Composable
fun NutritionGoalSettingsScreen(onBack: () -> Unit) {
    var calorieGoal by remember { mutableStateOf("2000") }
    var proteinTarget by remember { mutableStateOf("150") }
    var fatTarget by remember { mutableStateOf("65") }
    var carbsTarget by remember { mutableStateOf("250") }

    // TDEE calculator
    var showTDEE by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    var activityIndex by remember { mutableIntStateOf(2) }
    var calculatedTDEE by remember { mutableStateOf<Int?>(null) }

    val activityLevels = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active", "Extra Active")
    val activityMultipliers = listOf(1.2, 1.375, 1.55, 1.725, 1.9)

    LaunchedEffect(Unit) {
        val goal = MockDataProvider.nutritionGoal
        calorieGoal = "${goal.calorieGoal}"
        proteinTarget = "${goal.proteinTarget}"
        fatTarget = "${goal.fatTarget}"
        carbsTarget = "${goal.carbsTarget}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Nutrition Goals",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
            )

            // Goals card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
            ) {
                GoalField("Daily Calories", calorieGoal, { calorieGoal = it }, "cal")
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                GoalField("Protein Target", proteinTarget, { proteinTarget = it }, "g")
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                GoalField("Fat Target", fatTarget, { fatTarget = it }, "g")
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                GoalField("Carbs Target", carbsTarget, { carbsTarget = it }, "g")
            }

            // TDEE Calculator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTDEE = !showTDEE },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TDEE Calculator",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryColor
                    )
                    Icon(
                        imageVector = if (showTDEE) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = TextSecondaryColor
                    )
                }

                AnimatedVisibility(visible = showTDEE) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundColor, RoundedCornerShape(8.dp))
                        ) {
                            GoalField("Weight", weight, { weight = it }, "lbs")
                            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                            GoalField("Height", heightCm, { heightCm = it }, "cm")
                            SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                            GoalField("Age", age, { age = it }, "yrs")
                        }

                        // Sex picker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GenderButton("Male", isMale, Modifier.weight(1f)) { isMale = true }
                            GenderButton("Female", !isMale, Modifier.weight(1f)) { isMale = false }
                        }

                        // Activity level
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Activity Level", fontSize = 12.sp, color = TextSecondaryColor)
                            activityLevels.forEachIndexed { index, level ->
                                Row(
                                    modifier = Modifier.clickable { activityIndex = index },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .width(14.dp)
                                            .background(
                                                if (activityIndex == index) FLPrimary else BackgroundColor,
                                                RoundedCornerShape(7.dp)
                                            )
                                    )
                                    Text(level, fontSize = 14.sp, color = TextPrimaryColor)
                                }
                            }
                        }

                        // Calculate button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FLPrimary, RoundedCornerShape(8.dp))
                                .clickable {
                                    val w = weight.toDoubleOrNull() ?: return@clickable
                                    val h = heightCm.toDoubleOrNull() ?: return@clickable
                                    val a = age.toDoubleOrNull() ?: return@clickable
                                    val weightKg = w * 0.453592
                                    val bmr = if (isMale) {
                                        10 * weightKg + 6.25 * h - 5 * a + 5
                                    } else {
                                        10 * weightKg + 6.25 * h - 5 * a - 161
                                    }
                                    calculatedTDEE = (bmr * activityMultipliers[activityIndex]).toInt()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Calculate",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = White
                            )
                        }

                        calculatedTDEE?.let { tdee ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Estimated TDEE:", fontSize = 14.sp, color = TextSecondaryColor)
                                    Text(
                                        "$tdee cal/day",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FLPrimary
                                    )
                                }
                                Text(
                                    text = "Use",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FLPrimary,
                                    modifier = Modifier.clickable {
                                        calorieGoal = "$tdee"
                                        calculatedTDEE = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(FLPrimary, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Goals",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondaryColor)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryColor,
                    textAlign = TextAlign.End
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )
            Text(unit, fontSize = 12.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun GenderButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) FLPrimary else BackgroundColor,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) White else TextPrimaryColor
        )
    }
}
