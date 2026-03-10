package com.fitnesslink.fit.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.components.SocialSignInButton
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    initialTab: Int = 1,
    onLogin: (needsPersonalization: Boolean) -> Unit
) {
    val viewModel: LoginViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var animating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { animating = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black),
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "FitnessLink",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = FLPrimary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
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
                        .background(BackgroundColor)
                        .padding(top = 20.dp)
                ) {
                    // Tab bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TabButton("Sign Up", selectedTab == 0) { selectedTab = 0 }
                        Spacer(modifier = Modifier.width(20.dp))
                        TabButton("Login", selectedTab == 1) { selectedTab = 1 }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (selectedTab == 1) {
                        LoginContent(viewModel = viewModel, onLogin = onLogin)
                    } else {
                        SignUpContent(onLogin = onLogin)
                    }
                }
            }
        }
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

@Composable
private fun LoginContent(
    viewModel: LoginViewModel,
    onLogin: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Text("Login", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            placeholder = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            placeholder = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            if (viewModel.isInvalidCredentials) {
                Text("Invalid email or password", fontSize = 11.sp, color = Color.Red)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Forgot Password?",
                fontSize = 14.sp,
                color = FLPrimary,
                modifier = Modifier.clickable { }
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        ActionButtonView(
            text = "Login",
            isEnabled = viewModel.isFormValid,
            isLoading = viewModel.isLoading,
            onClick = {
                viewModel.login()
                if (viewModel.isAuthenticated) {
                    onLogin(viewModel.needsPersonalization)
                }
            }
        )

        // Check auth after delay
        LaunchedEffect(viewModel.isAuthenticated) {
            if (viewModel.isAuthenticated) {
                onLogin(viewModel.needsPersonalization)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LoginSeparator()

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            SocialSignInButton(icon = "google", onClick = {
                viewModel.loginWithGoogle()
                onLogin(viewModel.needsPersonalization)
            })
            Spacer(modifier = Modifier.width(10.dp))
            SocialSignInButton(icon = "facebook", onClick = {
                viewModel.loginWithFacebook()
                onLogin(viewModel.needsPersonalization)
            })
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SignUpContent(onLogin: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Text("Sign Up", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(30.dp))

        ActionButtonView(
            text = "Create Account",
            isEnabled = false,
            isLoading = false,
            onClick = { onLogin(true) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        LoginSeparator()

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            SocialSignInButton(icon = "google", onClick = {})
            Spacer(modifier = Modifier.width(10.dp))
            SocialSignInButton(icon = "facebook", onClick = {})
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun LoginSeparator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
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
                .background(BackgroundColor)
                .padding(horizontal = 10.dp)
        )
    }
}
