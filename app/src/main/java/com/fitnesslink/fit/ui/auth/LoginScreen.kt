package com.fitnesslink.fit.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.R
import com.fitnesslink.fit.auth.AuthConfig
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.components.SocialSignInButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.viewmodel.LoginViewModel
import com.fitnesslink.fit.viewmodel.SignUpViewModel

/**
 * Port of iOS `Views/Auth/LoginFormView.swift`: the Sign Up / Login tabbed
 * sheet over the welcome background, plus the Forgot Password and
 * Complete Profile sheets.
 */
@Composable
fun LoginScreen(
    initialTab: Int = 1,
    onLogin: (needsPersonalization: Boolean) -> Unit
) {
    val viewModel: LoginViewModel = viewModel()
    val signUpModel: SignUpViewModel = viewModel()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var animating by remember { mutableStateOf(false) }
    var showForgotPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { animating = true }

    // Hoisted so auth flips on either tab trigger navigation. Only refires on
    // transition.
    LaunchedEffect(viewModel.isAuthenticated) {
        if (viewModel.isAuthenticated) onLogin(viewModel.needsPersonalization)
    }
    LaunchedEffect(signUpModel.didSignUp) {
        // Straight into personalization — the entitlement gate in
        // AppNavigation decides what the user actually sees next.
        if (signUpModel.didSignUp) onLogin(true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.welcome),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black))) // theme-exempt: over media
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "FitnessLink",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(134.dp)
                    .padding(top = 80.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = animating,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                        .background(FLTheme.colors.background)
                        .padding(top = 20.dp)
                        // Edge-to-edge: the sheet background runs under the
                        // nav bar, but content (Google button) must clear it.
                        // navigationBarsPadding consumes those insets first so
                        // imePadding only adds the keyboard's extra height.
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.weight(1f))
                        TabButton("Sign Up", selectedTab == 0) { selectedTab = 0 }
                        Spacer(modifier = Modifier.width(20.dp))
                        TabButton("Login", selectedTab == 1) { selectedTab = 1 }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {
                        if (selectedTab == 1) {
                            LoginContent(
                                viewModel = viewModel,
                                onForgotPassword = {
                                    viewModel.beginForgotPassword()
                                    showForgotPassword = true
                                }
                            )
                        } else {
                            SignUpContent(model = signUpModel, socialModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showForgotPassword) {
        ForgotPasswordSheet(viewModel = viewModel, onDismiss = { showForgotPassword = false })
    }

    if (viewModel.needsProfileCompletion) {
        CompleteProfileSheet(
            hints = AuthManager.socialProfileHints,
            onCompleted = { viewModel.profileCompleted() },
            onAbandon = { viewModel.profileCompletionAbandoned() }
        )
    }
}

@Composable
private fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(2.dp)
                .background(if (isSelected) FLPrimary else Color.Transparent)
        )
    }
}

// MARK: - Login tab

@Composable
private fun LoginContent(
    viewModel: LoginViewModel,
    onForgotPassword: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Login", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))
        CapsuleTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            placeholder = "Email Address",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        )

        Spacer(modifier = Modifier.height(10.dp))
        RevealablePasswordField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            placeholder = "Password",
            imeAction = ImeAction.Go,
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                viewModel.login()
            })
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.isInvalidCredentials) {
                Text(
                    text = viewModel.errorMessage ?: "Invalid email or password",
                    fontSize = 11.sp,
                    color = FLTheme.colors.danger,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = "Forgot Password?",
                fontSize = 14.sp,
                color = FLPrimary,
                modifier = Modifier.clickable {
                    focusManager.clearFocus()
                    onForgotPassword()
                }
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
        ActionButtonView(
            text = "Login",
            isEnabled = viewModel.isFormValid,
            isLoading = viewModel.isLoading,
            onClick = {
                focusManager.clearFocus()
                viewModel.login()
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
        LoginSeparator()
        Spacer(modifier = Modifier.height(10.dp))
        SocialButtons(viewModel)

        Spacer(modifier = Modifier.height(30.dp))
    }
}

// MARK: - Sign Up tab (FTNSSLNK-285)

@Composable
private fun SignUpContent(model: SignUpViewModel, socialModel: LoginViewModel) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Sign Up", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))
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

        Spacer(modifier = Modifier.height(10.dp))
        CapsuleTextField(
            value = model.email,
            onValueChange = { model.email = it },
            placeholder = "Email Address",
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(10.dp))
        CapsuleTextField(
            value = model.phone,
            onValueChange = { model.phone = it },
            placeholder = "Phone (optional)",
            keyboardType = KeyboardType.Phone
        )

        Spacer(modifier = Modifier.height(10.dp))
        UsernameField(
            value = model.username,
            onValueChange = model::onUsernameChanged,
            state = model.usernameState,
            onSuggestionTap = model::applySuggestion
        )

        Spacer(modifier = Modifier.height(10.dp))
        RevealablePasswordField(
            value = model.password,
            onValueChange = { model.password = it },
            placeholder = "Password (8+ characters)",
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        model.errorMessage?.let {
            Spacer(modifier = Modifier.height(10.dp))
            AuthErrorText(it)
        }

        Spacer(modifier = Modifier.height(30.dp))
        ActionButtonView(
            text = "Create Account",
            isEnabled = model.isFormValid && !model.isSubmitting,
            isLoading = model.isSubmitting,
            onClick = {
                focusManager.clearFocus()
                model.submit()
            }
        )

        // Social sign-in creates the account too (allowsSocialAccountCreation);
        // a first-time Google user finishes on CompleteProfileSheet.
        Spacer(modifier = Modifier.height(20.dp))
        LoginSeparator()
        Spacer(modifier = Modifier.height(10.dp))
        SocialButtons(socialModel)

        Spacer(modifier = Modifier.height(30.dp))
    }
}

// Social sign-in (Google; Facebook hidden until the provider is enabled in
// Firebase). Since 2026-10-27 a first-time social sign-in creates the account
// (AuthConfig.allowsSocialAccountCreation) and finishes on CompleteProfileSheet,
// so the row appears on both tabs, matching iOS `LoginFormView.socialButtons`.
// Sign in with Apple is iOS-only.
@Composable
private fun SocialButtons(viewModel: LoginViewModel) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SocialSignInButton(
                    icon = "google",
                    onClick = {
                        if (viewModel.socialProvider == null) {
                            focusManager.clearFocus()
                            viewModel.loginWithGoogle(context)
                        }
                    }
                )
                if (AuthConfig.isFacebookSignInEnabled) {
                    Spacer(modifier = Modifier.width(10.dp))
                    SocialSignInButton(
                        icon = "facebook",
                        onClick = {
                            if (viewModel.socialProvider == null) {
                                focusManager.clearFocus()
                                viewModel.loginWithFacebook(context)
                            }
                        }
                    )
                }
            }

            viewModel.socialErrorMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                AuthErrorText(it, centered = true)
            }
    }
}

@Composable
private fun LoginSeparator() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FLPrimary)
        )
        Text(
            text = "Or login with",
            fontSize = 14.sp,
            color = FLPrimary,
            modifier = Modifier
                .background(FLTheme.colors.background)
                .padding(horizontal = 10.dp)
        )
    }
}
