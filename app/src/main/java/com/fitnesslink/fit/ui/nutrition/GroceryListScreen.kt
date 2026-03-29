package com.fitnesslink.fit.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.GroceryCategory
import com.fitnesslink.fit.model.GroceryItem
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.ProgressBG
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.MealPlanViewModel

@Composable
fun GroceryListScreen(
    onBack: () -> Unit
) {
    val viewModel: MealPlanViewModel = viewModel()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadData() }

    val filtered = if (searchText.isBlank()) {
        viewModel.groceryItems
    } else {
        viewModel.groceryItems.filter { it.name.contains(searchText, ignoreCase = true) }
    }

    val grouped = filtered
        .groupBy { it.category }
        .let { map ->
            GroceryCategory.entries.mapNotNull { cat ->
                map[cat]?.let { items -> cat to items }
            }
        }

    val checkedCount = viewModel.groceryItems.count { it.isChecked }
    val totalCount = viewModel.groceryItems.size
    val progress by animateFloatAsState(
        targetValue = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f,
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        // Title
        Text(
            text = "Grocery List",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Progress bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$checkedCount of $totalCount items",
                    fontSize = 13.sp,
                    color = TextSecondaryColor
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FLPrimary
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ProgressBG)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FLPrimary)
                )
            }
        }

        // Search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp)
                .background(BackgroundColor, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = TextSecondaryColor,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = TextPrimaryColor),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchText.isEmpty()) {
                            Text("Search items...", fontSize = 15.sp, color = TextSecondaryColor.copy(alpha = 0.5f))
                        }
                        innerTextField()
                    }
                }
            )
        }

        // List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            grouped.forEach { (category, items) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(White, RoundedCornerShape(12.dp))
                ) {
                    // Category header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = categoryEmoji(category),
                                fontSize = 14.sp
                            )
                            Text(
                                text = category.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryColor
                            )
                        }
                        Text(
                            text = "${items.count { it.isChecked }}/${items.size}",
                            fontSize = 12.sp,
                            color = TextSecondaryColor
                        )
                    }

                    // Items
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleGroceryItem(item.id) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = "Check",
                                tint = if (item.isChecked) FLPrimary else MediumGray,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    color = if (item.isChecked) TextSecondaryColor else TextPrimaryColor,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                )
                                if (item.quantity.isNotEmpty()) {
                                    Text(
                                        text = "${item.quantity} ${item.unit}",
                                        fontSize = 12.sp,
                                        color = TextSecondaryColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun categoryEmoji(category: GroceryCategory): String = when (category) {
    GroceryCategory.PRODUCE -> "\uD83C\uDF3F"
    GroceryCategory.PROTEIN -> "\uD83E\uDD69"
    GroceryCategory.DAIRY -> "\uD83E\uDD5B"
    GroceryCategory.GRAINS -> "\uD83C\uDF5E"
    GroceryCategory.PANTRY -> "\uD83C\uDFE0"
    GroceryCategory.FROZEN -> "\u2744\uFE0F"
    GroceryCategory.OTHER -> "\uD83E\uDDFA"
}
