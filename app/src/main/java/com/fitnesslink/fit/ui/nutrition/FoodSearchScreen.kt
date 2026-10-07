package com.fitnesslink.fit.ui.nutrition

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.dto.FoodSearchResult
import com.fitnesslink.fit.nutrition.FoodDetailHandoff
import com.fitnesslink.fit.nutrition.NutritionFavoritesStore
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SearchView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.FoodSearchViewModel
import com.fitnesslink.fit.viewmodel.asTemplateEntry
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Food search (iOS `FoodSearchView`): USDA catalog + the user's custom foods
 * merged above ("My Food" badge), favorite star per catalog row, and a
 * no-results state offering manual entry and an AI estimate. Selecting a row
 * opens Food Detail — nothing is logged until the user saves there.
 */
@Composable
fun FoodSearchScreen(
    mealType: MealType,
    date: String,
    barcode: String?,
    flow: FoodLogFlow,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: FoodSearchViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val favoriteIds by NutritionFavoritesStore.foodIds.collectAsState()
    val logDate = parseLogDate(date)
    val mode = flow.wire

    LaunchedEffect(Unit) { NutritionFavoritesStore.refreshIfNeeded() }

    fun open(result: FoodSearchResult) {
        if (viewModel.isTemplate(result)) {
            val local = viewModel.localEntry(result)
            val route = if (local != null) FoodLoggingRoutes.foodDetailTemplate(local.id, mealType, date, mode)
            else FoodLoggingRoutes.foodDetailHandoff(FoodDetailHandoff.putEntry(result.asTemplateEntry(mealType)), mealType, date, mode)
            onNavigate(route)
        } else {
            FoodDetailHandoff.putFood(result)
            onNavigate(FoodLoggingRoutes.foodDetail(result.id, mealType, date, mode))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Search Foods", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
            SearchView(text = viewModel.query, onTextChange = viewModel::onQueryChange, placeholder = "e.g. chicken breast, oats, banana")
            Text(
                if (flow.stagesIntoMeal) "Adding to the meal you're building" else "Adding to ${mealType.displayName}${backfillSuffix(logDate)}",
                fontSize = 12.sp, color = TextSecondaryColor
            )
        }
        Spacer(Modifier.height(12.dp))

        val query = viewModel.query.trim()
        when {
            viewModel.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FLPrimary) }
            viewModel.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 40.dp)) {
                    Icon(Icons.Default.Warning, null, tint = OrangeTheme, modifier = Modifier.size(24.dp))
                    Text(viewModel.errorMessage ?: "", fontSize = 14.sp, color = TextSecondaryColor, textAlign = TextAlign.Center)
                }
            }
            viewModel.results.isEmpty() && viewModel.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("No results for “$query”", fontSize = 14.sp, color = TextSecondaryColor)
                    // FTNSSLNK-200: the empty state offers manual entry seeded with the term that found nothing.
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(White, RoundedCornerShape(10.dp))
                            .clickable { onNavigate(FoodLoggingRoutes.quickAdd(mealType, date, prefillName = query, barcode = barcode, mode = mode)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                        Text("Add “$query” manually", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                    }
                    // FTNSSLNK-446: the AI estimate lands in the same Quick Add form, prefilled and auto-filled.
                    Row(
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(FLPrimary, RoundedCornerShape(10.dp))
                            .clickable { onNavigate(FoodLoggingRoutes.quickAdd(mealType, date, prefillName = query, barcode = barcode, autoFillWithAI = true, mode = mode)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = White, modifier = Modifier.size(16.dp))
                        Text("Can't find it? Estimate macros with AI", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = White)
                    }
                }
            }
            viewModel.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type to search the food database", fontSize = 14.sp, color = TextSecondaryColor)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(viewModel.results, key = { i, r -> "${r.id}-$i" }) { index, result ->
                    val isTemplate = viewModel.isTemplate(result)
                    val shape = when {
                        viewModel.results.size == 1 -> RoundedCornerShape(12.dp)
                        index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        index == viewModel.results.lastIndex -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Column(modifier = Modifier.fillMaxWidth().clip(shape).background(White)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchResultRow(result, isTemplate, modifier = Modifier.weight(1f).clickable { open(result) })
                            // FTNSSLNK-215: star catalog rows in place (template rows have a FoodEntry id — nothing to favorite).
                            if (!isTemplate) {
                                val starred = result.id.lowercase(Locale.US) in favoriteIds
                                Icon(
                                    if (starred) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (starred) "Unfavorite" else "Favorite",
                                    tint = OrangeTheme,
                                    modifier = Modifier
                                        .clickable { scope.launch { NutritionFavoritesStore.toggleFood(result.id) } }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                        if (index < viewModel.results.lastIndex) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: FoodSearchResult, isTemplate: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, modifier = Modifier.weight(1f, fill = false))
                if (isTemplate) FoodLogBadge("My Food")
            }
            if (!result.brand.isNullOrEmpty()) Text(result.brand, fontSize = 12.sp, color = TextSecondaryColor)
            Text("P:${result.protein.toInt()}  F:${result.fat.toInt()}  C:${result.carbs.toInt()}", fontSize = 11.sp, color = TextSecondaryColor)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${result.calories.roundToInt()} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            result.servingSizeGrams?.let { grams ->
                Text("per ${grams.toInt()} ${result.servingUnit ?: "g"}", fontSize = 11.sp, color = TextSecondaryColor)
            }
        }
    }
}
