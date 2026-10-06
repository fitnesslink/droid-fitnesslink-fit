package com.fitnesslink.fit.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.viewmodel.LoginViewModel

/**
 * Port of iOS `ForgotPasswordSheet`. Collects an email and fires the
 * platform reset (Firebase fallback). The actual password change happens on
 * the hosted page via the emailed link, so the sheet's job ends at "check
 * your inbox".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordSheet(
    viewModel: LoginViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (viewModel.resetEmailSent) {
                SentContent(email = viewModel.resetEmail.trim(), onDone = onDismiss)
            } else {
                FormContent(viewModel)
            }
        }
    }
}

@Composable
private fun FormContent(viewModel: LoginViewModel) {
    Text("Reset Password", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Enter your email address and we'll send you a link to reset your password.",
        fontSize = 14.sp,
        color = TextSecondaryColor
    )
    Spacer(modifier = Modifier.height(16.dp))
    CapsuleTextField(
        value = viewModel.resetEmail,
        onValueChange = { viewModel.resetEmail = it },
        placeholder = "Email Address",
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Send,
        keyboardActions = KeyboardActions(onSend = { viewModel.sendPasswordReset() })
    )
    viewModel.resetErrorMessage?.let {
        Spacer(modifier = Modifier.height(10.dp))
        AuthErrorText(it)
    }
    Spacer(modifier = Modifier.height(24.dp))
    ActionButtonView(
        text = "Send Reset Link",
        isEnabled = viewModel.isResetFormValid,
        isLoading = viewModel.isSendingReset,
        onClick = { viewModel.sendPasswordReset() }
    )
}

@Composable
private fun SentContent(email: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(
            imageVector = Icons.Filled.MarkEmailRead,
            contentDescription = null,
            tint = FLPrimary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Check Your Email", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "If an account exists for $email, a password reset link is on its way. Follow it to set a new password, then log in.",
            fontSize = 14.sp,
            color = TextSecondaryColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        ActionButtonView(text = "Done", isEnabled = true, isLoading = false, onClick = onDone)
    }
}
