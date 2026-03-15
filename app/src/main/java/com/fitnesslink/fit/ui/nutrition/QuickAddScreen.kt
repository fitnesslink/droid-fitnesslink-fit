package com.fitnesslink.fit.ui.nutrition

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

@Composable
fun QuickAddScreen(
    mealTypeName: String,
    onBack: () -> Unit,
    onNavigateToBarcodeScanner: (String) -> Unit = {},
    onNavigateToRecentFoods: () -> Unit = {}
) {
    val mealType = MealType.entries.firstOrNull { it.name == mealTypeName } ?: MealType.BREAKFAST

    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var servingSize by remember { mutableStateOf("1") }
    var servingUnit by remember { mutableStateOf("serving") }

    val isValid = name.isNotEmpty() && calories.toIntOrNull() != null

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
                text = "Add to ${mealType.displayName}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
            )

            // Form fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
            ) {
                FormField("Food Name", name, { name = it })
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Calories", calories, { calories = it }, KeyboardType.Number)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Protein (g)", protein, { protein = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Fat (g)", fat, { fat = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Carbs (g)", carbs, { carbs = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Serving Size", servingSize, { servingSize = it }, KeyboardType.Decimal)
                SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                FormField("Serving Unit", servingUnit, { servingUnit = it })
            }

            // Scan Barcode link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .clickable { onNavigateToBarcodeScanner(mealType.name) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.search),
                    contentDescription = "Scan",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Barcode",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = FLPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.rightarrow),
                    contentDescription = "Go",
                    modifier = Modifier.size(8.dp, 14.dp)
                )
            }

            // Recent Foods link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .clickable(onClick = onNavigateToRecentFoods)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose from Recent Foods",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = FLPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.rightarrow),
                    contentDescription = "Go",
                    modifier = Modifier.size(8.dp, 14.dp)
                )
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(
                        if (isValid) FLPrimary else DisabledButton,
                        RoundedCornerShape(12.dp)
                    )
                    .then(if (isValid) Modifier.clickable(onClick = onBack) else Modifier)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Entry",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = White
                )
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondaryColor
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 16.sp,
                color = TextPrimaryColor
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
