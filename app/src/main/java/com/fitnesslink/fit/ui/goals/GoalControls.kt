package com.fitnesslink.fit.ui.goals

import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.ui.theme.OrangeTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.goals.GoalAction
import com.fitnesslink.fit.model.GoalSubtype
import com.fitnesslink.fit.model.GoalType
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.viewmodel.GoalSetupViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.fitnesslink.fit.ui.theme.WarningAmber

// Shared controls for the Goals PRD screens (iOS `GoalPrimaryButton`,
// `GoalOptionCard`, `GoalCard`, `GoalNumberField`, `GoalNote`,
// `WeekdayPicker` in GoalSetupFlowView.swift) plus Android stand-ins for
// SwiftUI's segmented Picker, Stepper, Toggle and DatePicker rows.

val GoalWarning = WarningAmber

@Composable
fun goalSurface(): Color = MaterialTheme.colorScheme.surface

@Composable
fun goalSecondaryText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

fun goalSubtypeIcon(subtype: GoalSubtype): ImageVector = when (subtype) {
    GoalSubtype.LoseWeight -> Icons.Filled.MonitorWeight
    GoalSubtype.ImproveLift -> Icons.Filled.ShowChart
    GoalSubtype.PersonalRecord -> Icons.Filled.EmojiEvents
    GoalSubtype.RepsAtWeight -> Icons.Filled.Repeat
    GoalSubtype.TrainingVolume -> Icons.Filled.Layers
    GoalSubtype.SleepDuration -> Icons.Filled.Bedtime
    GoalSubtype.SleepConsistency -> Icons.Filled.NightsStay
    GoalSubtype.RecoveryMetric -> Icons.Filled.MonitorHeart
    GoalSubtype.RecoveryRoutine -> Icons.Filled.Spa
    GoalSubtype.CalorieTarget -> Icons.Filled.LocalFireDepartment
    GoalSubtype.MacroTarget -> Icons.Filled.PieChart
    GoalSubtype.Hydration -> Icons.Filled.LocalDrink
    GoalSubtype.HealthyHabit -> Icons.Filled.Eco
    GoalSubtype.WorkoutConsistency -> Icons.Filled.FitnessCenter
    GoalSubtype.MovementRoutine -> Icons.Filled.DirectionsRun
    GoalSubtype.WellnessRoutine -> Icons.Filled.SelfImprovement
    GoalSubtype.DailyHabit -> Icons.Filled.CheckCircle
    GoalSubtype.CustomRoutine -> Icons.Filled.Tune
    GoalSubtype.CustomGoal -> Icons.Filled.Star
}

fun goalCategoryIcon(type: GoalType): ImageVector = goalTypeIcon(type)

fun goalActionIcon(action: GoalAction): ImageVector = when (action) {
    GoalAction.LogWeight -> Icons.Filled.MonitorWeight
    GoalAction.LogFood -> Icons.Filled.Restaurant
    GoalAction.LogWater -> Icons.Filled.LocalDrink
    GoalAction.LogSleep -> Icons.Filled.Bedtime
    GoalAction.LogWorkout -> Icons.Filled.FitnessCenter
    GoalAction.ConnectHealth -> Icons.Filled.MonitorHeart
    is GoalAction.CheckIn -> Icons.Filled.CheckCircle
    is GoalAction.LogAmount -> Icons.Filled.AddCircle
}

@Composable
fun GoalPrimaryButton(title: String, enabled: Boolean = true, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) FLPrimary else Color.Gray.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = FLTheme.colors.onBrand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = FLTheme.colors.onBrand, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GoalSmallButton(title: String, onClick: () -> Unit) {
    Text(
        title,
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
        modifier = Modifier
            .clip(CircleShape)
            .background(FLPrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
fun GoalLinkButton(title: String, onClick: () -> Unit) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp))
}

@Composable
fun GoalOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    example: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) FLPrimary.copy(alpha = 0.08f) else goalSurface())
            .border(BorderStroke(1.5.dp, if (isSelected) FLPrimary else Color.Transparent), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(FLPrimary.copy(alpha = 0.12f))
        ) { Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (badge != null) {
                    Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FLPrimary,
                        modifier = Modifier.clip(CircleShape).background(FLPrimary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(subtitle, fontSize = 13.sp, color = goalSecondaryText())
            if (example != null) Text(example, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
        }
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) FLPrimary else FLTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GoalCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(goalSurface())
            .padding(16.dp)
    ) {
        if (title != null) Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = goalSecondaryText())
        content()
    }
}

@Composable
fun GoalNumberField(label: String, text: String, onChange: (String) -> Unit, unit: String? = null, decimal: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        TextField(
            value = text,
            onValueChange = { raw -> onChange(raw.filter { it.isDigit() || (decimal && it == '.') }) },
            placeholder = { Text("0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = FLPrimary, unfocusedIndicatorColor = FLTheme.colors.textTertiary.copy(alpha = 0.4f)
            ),
            modifier = Modifier.widthIn(max = 130.dp).semantics { contentDescription = label }
        )
        if (!unit.isNullOrEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(unit, fontSize = 15.sp, color = goalSecondaryText())
        }
    }
}

@Composable
fun GoalTextField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun GoalNote(text: String, icon: ImageVector = Icons.Filled.Info, tint: Color? = null) {
    val color = tint ?: goalSecondaryText()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(text, fontSize = 13.sp, color = color)
    }
}

/** Segmented control stand-in for SwiftUI `Picker(.segmented)`. */
@Composable
fun <T> GoalSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.Gray.copy(alpha = 0.15f))
            .padding(2.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) goalSurface() else Color.Transparent)
                    .clickable { onSelect(value) }
                    .semantics { this.selected = on }
                    .padding(vertical = 7.dp)
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
        }
    }
}

/** SwiftUI `Stepper` stand-in: label with − / + buttons, clamped to [range]. */
@Composable
fun GoalStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, step: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        StepperButton(Icons.Filled.Remove, "Decrease", value - step >= range.first) { onChange((value - step).coerceIn(range)) }
        Spacer(Modifier.width(8.dp))
        StepperButton(Icons.Filled.Add, "Increase", value + step <= range.last) { onChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.15f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
    ) { Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else FLTheme.colors.textTertiary, modifier = Modifier.size(18.dp)) }
}

@Composable
fun GoalToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = goalSecondaryText())
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FLPrimary, checkedThumbColor = FLTheme.colors.onBrand))
    }
}

/** iOS `WeekdayPicker`: 1 = Sunday … 7 = Saturday. */
@Composable
fun WeekdayPicker(selection: List<Int>, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        (1..7).forEach { day ->
            val on = day in selection
            val name = GoalSetupViewModel.weekdayShortName(day)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(if (on) FLPrimary else Color.Gray.copy(alpha = 0.15f))
                    .clickable { onToggle(day) }
                    .semantics { contentDescription = name; selected = on }
            ) {
                Text(name.take(1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) FLTheme.colors.onBrand else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Chip row of quick values (water presets, frequency presets). */
@Composable
fun GoalChip(label: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) FLPrimary else Color.Gray.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) FLTheme.colors.onBrand else MaterialTheme.colorScheme.onSurface)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun utcMillisToLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** A "Label — date" row that opens Material's DatePickerDialog limited to [min]…[max]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDateRow(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, min: LocalDate? = null, max: LocalDate? = null) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 4.dp)) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = utcMillisToLocalDate(utcTimeMillis)
                    return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(utcMillisToLocalDate(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

/** A "Label — time" row that opens a Material TimePicker in a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalTimeRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 4.dp)) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(LocalTime.of(state.hour, state.minute)); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) }
        )
    }
}

/** Two-line label/value stat used by the time-frame summary and progress grid. */
@Composable
fun GoalStatText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(label, fontSize = 11.sp, color = goalSecondaryText())
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun GoalProgressBar(fraction: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                .height(6.dp)
                .clip(CircleShape)
                .background(FLPrimary)
        )
    }
}
