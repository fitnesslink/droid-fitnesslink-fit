package com.fitnesslink.fit.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitnesslink.fit.ui.programs.MyProgramScreen
import com.fitnesslink.fit.ui.programs.ProgramProgressScreen
import com.fitnesslink.fit.ui.programs.StartProgramScreen

/**
 * Program-domain routes added by the parity work (CONTRACTS "Programs").
 * The pre-existing `programs`, `programDetail/{programId}`, `programEditor`
 * and `programEditor/{programId}` routes stay in `AppNavigation.kt`.
 */
object ProgramRoutes {
    const val START_PROGRAM = "startProgram/{programId}?replacing={replacing}"
    const val MY_PROGRAM = "myProgram?enrollmentId={enrollmentId}"
    const val PROGRAM_PROGRESS = "programProgress?enrollmentId={enrollmentId}"
    const val PROGRAMS = "programs"

    /** Start Your Program; [replacing] = enrollment being restarted (removed on start). */
    fun startProgram(programId: String, replacing: String? = null): String =
        if (replacing.isNullOrEmpty()) "startProgram/$programId" else "startProgram/$programId?replacing=$replacing"

    /** My Program; null = the active program. */
    fun myProgram(enrollmentId: String? = null): String =
        if (enrollmentId.isNullOrEmpty()) "myProgram" else "myProgram?enrollmentId=$enrollmentId"

    /** Program Progress; null = the active program. */
    fun programProgress(enrollmentId: String? = null): String =
        if (enrollmentId.isNullOrEmpty()) "programProgress" else "programProgress?enrollmentId=$enrollmentId"
}

fun NavGraphBuilder.programRoutes(navController: NavHostController) {
    composable(
        ProgramRoutes.START_PROGRAM,
        arguments = listOf(
            navArgument("programId") { type = NavType.StringType },
            navArgument("replacing") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { backStackEntry ->
        StartProgramScreen(
            programId = backStackEntry.arguments?.getString("programId") ?: "",
            replacingEnrollmentId = backStackEntry.arguments?.getString("replacing"),
            onBack = { navController.popBackStack() }
        )
    }
    composable(
        ProgramRoutes.MY_PROGRAM,
        arguments = listOf(navArgument("enrollmentId") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { backStackEntry ->
        MyProgramScreen(
            enrollmentId = backStackEntry.arguments?.getString("enrollmentId"),
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) }
        )
    }
    composable(
        ProgramRoutes.PROGRAM_PROGRESS,
        arguments = listOf(navArgument("enrollmentId") { type = NavType.StringType; nullable = true; defaultValue = null })
    ) { backStackEntry ->
        ProgramProgressScreen(
            enrollmentId = backStackEntry.arguments?.getString("enrollmentId"),
            onBack = { navController.popBackStack() }
        )
    }
}
