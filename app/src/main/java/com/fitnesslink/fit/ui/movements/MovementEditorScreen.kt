package com.fitnesslink.fit.ui.movements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.workout.MovementCreateForm

/**
 * Catalog entry point for creating a movement (iOS `MovementEditorView`):
 * reuses the shared [MovementCreateForm] so the "+" on the Movements screen
 * and the workout editor's Create New Exercise can't drift apart. Pops on
 * success (after [onCreated], when provided).
 */
@Composable
fun MovementEditorScreen(
    onBack: () -> Unit,
    onCreated: ((MovementLibraryItem) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        MovementCreateForm(
            title = "New Exercise",
            onCancel = onBack,
            onCreated = { item ->
                onCreated?.invoke(item)
                onBack()
            }
        )
    }
}
