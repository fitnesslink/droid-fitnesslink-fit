package com.fitnesslink.fit.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.CompleteProfileViewModel

/**
 * Port of iOS `Views/Auth/CompleteProfileSheet.swift`. Second step of a
 * first-time social sign-in: Google/Facebook hand us a display name and
 * (usually) an email, but the platform profile also needs a unique handle,
 * and the provider's name split is a guess — so the user confirms all of it
 * here before entering the app.
 *
 * Dismissing without saving signs the Firebase session back out
 * (`onAbandon`): an account with an empty profile would otherwise land on
 * Home with no name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileSheet(
    hints: AuthManager.SocialProfileHints?,
    onCompleted: () -> Unit,
    onAbandon: () -> Unit
) {
    val model: CompleteProfileViewModel = viewModel()
    LaunchedEffect(Unit) { model.seed(hints) }
    LaunchedEffect(model.didComplete) { if (model.didComplete) onCompleted() }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !model.isSubmitting }
    )
    ModalBottomSheet(
        onDismissRequest = { if (!model.isSubmitting) onAbandon() },
        sheetState = sheetState,
        containerColor = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Finish Your Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Just a couple of details and you're in.", fontSize = 14.sp, color = TextSecondaryColor)

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CapsuleTextField(
                    value = model.firstName,
                    onValueChange = { model.firstName = it },
                    placeholder = "First Name",
                    modifier = Modifier.weight(1f),
                    capitalization = KeyboardCapitalization.Words
                )
                CapsuleTextField(
                    value = model.lastName,
                    onValueChange = { model.lastName = it },
                    placeholder = "Last Name",
                    modifier = Modifier.weight(1f),
                    capitalization = KeyboardCapitalization.Words
                )
            }

            // Facebook can withhold the email when the user declines that
            // permission; only then do we ask for it.
            if (model.needsEmail) {
                Spacer(modifier = Modifier.height(10.dp))
                CapsuleTextField(
                    value = model.email,
                    onValueChange = { model.email = it },
                    placeholder = "Email Address",
                    keyboardType = KeyboardType.Email
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            UsernameField(
                value = model.username,
                onValueChange = model::onUsernameChanged,
                state = model.usernameState,
                onSuggestionTap = model::applySuggestion,
                imeAction = ImeAction.Done
            )

            model.errorMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                AuthErrorText(it)
            }

            Spacer(modifier = Modifier.height(30.dp))
            ActionButtonView(
                text = "Continue",
                isEnabled = model.isFormValid && !model.isSubmitting,
                isLoading = model.isSubmitting,
                onClick = { model.submit() }
            )
        }
    }
}
