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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.model.FoodEntry
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.FavoriteFoodSummary
import com.fitnesslink.fit.nutrition.FoodDetailHandoff
import com.fitnesslink.fit.nutrition.FoodLogStore
import com.fitnesslink.fit.nutrition.NutritionFavoritesStore
import com.fitnesslink.fit.persistence.DatabaseManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Recent & My Foods (iOS `RecentFoodsListView`): Favorites (starred catalog
 * foods), My Foods (saved custom templates, editable in place) and the
 * recent diary history from `GET /food-entries/me/foods`, searchable.
 * Tapping any row opens Food Detail pre-filled with the last-used portion —
 * nothing is logged until the user saves there.
 *
 * The legacy `(onBack, onNavigateToCustomFoodForm)` signature from
 * `AppNavigation.kt` is kept; the new params are optional.
 */
@Composable
fun RecentFoodsScreen(
    onBack: () -> Unit,
    onNavigateToCustomFoodForm: (String) -> Unit = {},
    mealTypeName: String = "BREAKFAST",
    date: String? = null,
    mode: String = "log",
    onNavigate: (String) -> Unit = {}
) {
    val mealType = parseMealTypeArg(mealTypeName)
    val dateArg = date ?: FoodLoggingRoutes.today()
    val logDate = parseLogDate(dateArg)
    val flow = FoodLogFlow.parse(mode)
    val scope = rememberCoroutineScope()

    var recentFoods by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }
    var customFoods by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val favorites by NutritionFavoritesStore.favorites.collectAsState()
    val availableFavorites = favorites.filter { it.kind.equals("food", true) && it.isAvailable && it.food != null }

    fun loadLocal() {
        customFoods = FoodLogStore.customTemplates()
        recentFoods = FoodLogStore.recentFoodsLocal()
    }

    LaunchedEffect(Unit) {
        loadLocal()
        launch { NutritionFavoritesStore.refresh() }
        // FTNSSLNK-201: recents from the server (deduped by food, most recent first, last-used portion riding along).
        val remote = try { ApiClient.nutritionApi.searchLoggedFoods() } catch (_: Exception) { null }
        if (remote != null) {
            remote.forEach { DatabaseManager.insertFoodEntry(it) }
            if (query.trim().isEmpty()) loadLocal()
        }
    }

    fun scheduleSearch(newValue: String) {
        searchJob?.cancel()
        val trimmed = newValue.trim()
        if (trimmed.isEmpty()) { isSearching = false; loadLocal(); return }
        searchJob = scope.launch {
            delay(300)
            isSearching = true
            try {
                val results = try { ApiClient.nutritionApi.searchLoggedFoods(trimmed) } catch (_: Exception) { null }
                if (results != null) recentFoods = results
            } finally { isSearching = false }
        }
    }

    fun openTemplate(food: FoodEntry) = onNavigate(FoodLoggingRoutes.foodDetailTemplate(food.id, mealType, dateArg, flow.wire))

    fun openFavorite(food: FavoriteFoodSummary) {
        val result = food.toFoodSearchResult()
        FoodDetailHandoff.putFood(result)
        onNavigate(FoodLoggingRoutes.foodDetail(result.id, mealType, dateArg, flow.wire))
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recent Foods", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text(
                    if (flow.stagesIntoMeal) "Adding to the meal you're building" else "Adding to ${mealType.displayName}${backfillSuffix(logDate)}",
                    fontSize = 12.sp, color = TextSecondaryColor
                )
            }

            SearchView(
                text = query,
                onTextChange = { query = it; scheduleSearch(it) },
                placeholder = "Search foods you've logged",
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Create Custom Food link
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))
                    .clickable { onNavigateToCustomFoodForm("") }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = painterResource(R.drawable.greenplus), contentDescription = "Add", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Custom Food", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
            }

            val searching = query.trim().isNotEmpty()

            // FTNSSLNK-215: starred foods, one tap from logging. Hidden while searching.
            if (!searching && availableFavorites.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodLogSectionTitle("Favorites")
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))) {
                        availableFavorites.forEachIndexed { index, favorite ->
                            val food = favorite.food ?: return@forEachIndexed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FavoriteRow(food, modifier = Modifier.weight(1f).clickable { openFavorite(food) })
                                Icon(
                                    Icons.Default.Star, contentDescription = "Unfavorite", tint = OrangeTheme,
                                    modifier = Modifier.clickable { scope.launch { NutritionFavoritesStore.toggleFood(food.id) } }
                                        .padding(horizontal = 14.dp, vertical = 12.dp).size(20.dp)
                                )
                            }
                            if (index < availableFavorites.lastIndex) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // FTNSSLNK-202: saved custom foods, editable in place. Hidden while searching.
            if (!searching && customFoods.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodLogSectionTitle("My Foods")
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))) {
                        customFoods.forEachIndexed { index, food ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RecentFoodRow(food, modifier = Modifier.weight(1f).clickable { openTemplate(food) })
                                Icon(
                                    Icons.Default.Edit, contentDescription = "Edit", tint = FLPrimary,
                                    modifier = Modifier.clickable { onNavigateToCustomFoodForm(food.id) }.padding(horizontal = 14.dp, vertical = 12.dp).size(18.dp)
                                )
                            }
                            if (index < customFoods.lastIndex) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            when {
                isSearching && recentFoods.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FLPrimary)
                }
                recentFoods.isEmpty() -> Text(
                    if (query.isEmpty()) "No foods logged yet. Try adding one via Search or Scan." else "No logged foods match “$query”.",
                    fontSize = 13.sp, color = TextSecondaryColor, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 20.dp)
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!searching) FoodLogSectionTitle("Recent")
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(White, RoundedCornerShape(12.dp))) {
                        recentFoods.forEachIndexed { index, food ->
                            RecentFoodRow(food, modifier = Modifier.fillMaxWidth().clickable { openTemplate(food) })
                            if (index < recentFoods.lastIndex) SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentFoodRow(food: FoodEntry, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(food.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            // Last-used structured portion when present, legacy serving fields otherwise.
            val qty = food.quantity ?: food.servingSize
            Text("${qty.roundToInt()} ${food.portionUnit ?: food.servingUnit}", fontSize = 12.sp, color = TextSecondaryColor)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${food.calories} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text("P:${food.protein.toInt()}  F:${food.fat.toInt()}  C:${food.carbs.toInt()}", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}

@Composable
private fun FavoriteRow(food: FavoriteFoodSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(food.description, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
            val brand = food.brandOwner ?: food.brandName
            when {
                !brand.isNullOrEmpty() -> Text(brand, fontSize = 12.sp, color = TextSecondaryColor)
                !food.householdServingText.isNullOrEmpty() -> Text(food.householdServingText, fontSize = 12.sp, color = TextSecondaryColor)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${food.calories.roundToInt()} cal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
            Text("P:${food.protein.toInt()}  F:${food.fat.toInt()}  C:${food.carbs.toInt()}", fontSize = 11.sp, color = TextSecondaryColor)
        }
    }
}
