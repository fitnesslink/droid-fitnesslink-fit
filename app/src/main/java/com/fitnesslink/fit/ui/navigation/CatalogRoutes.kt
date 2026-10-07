package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.fitnesslink.fit.ui.catalog.MyContentScreen

/**
 * Catalog-hub routes (CONTRACTS.md, "Home + Catalog hub"): `myContent`, the
 * My Content "See All" screen. Wired into `FeatureRoutes.featureRoutes` by
 * the integrator.
 */
object CatalogRoutes {
    const val MY_CONTENT = "myContent"
}

fun NavGraphBuilder.catalogRoutes(navController: NavHostController) {
    composable(CatalogRoutes.MY_CONTENT) {
        MyContentScreen(
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) }
        )
    }
}
