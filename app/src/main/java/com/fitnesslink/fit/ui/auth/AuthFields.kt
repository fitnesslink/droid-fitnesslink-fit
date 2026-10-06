package com.fitnesslink.fit.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.UsernameState

/** The pill text-field look shared by every auth form (iOS `capsuleField`). */
@Composable
fun pillFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = White,
    unfocusedContainerColor = White,
    disabledContainerColor = White,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent
)

@Composable
fun CapsuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        colors = pillFieldColors(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization,
            autoCorrectEnabled = false
        ),
        keyboardActions = keyboardActions
    )
}

/**
 * Port of iOS `RevealablePasswordField`: a pill password field with a
 * trailing eye button that toggles between masked and visible entry.
 * Starts masked; reveal state is local.
 */
@Composable
fun RevealablePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isRevealed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        colors = pillFieldColors(),
        singleLine = true,
        visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        keyboardActions = keyboardActions,
        trailingIcon = {
            IconButton(onClick = { isRevealed = !isRevealed }) {
                Icon(
                    imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (isRevealed) "Hide password" else "Show password",
                    tint = TextSecondaryColor
                )
            }
        }
    )
}

/**
 * FTNSSLNK-285. The '@' is chrome, not content — the field holds the bare
 * handle so what the user sees matches what gets stored. Shows the live
 * availability state and tappable suggestion chips when taken.
 */
@Composable
fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    state: UsernameState,
    onSuggestionTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("username") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            colors = pillFieldColors(),
            singleLine = true,
            prefix = {
                Text("@", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextSecondaryColor)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = imeAction,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false
            ),
            keyboardActions = keyboardActions,
            trailingIcon = {
                when (state) {
                    UsernameState.Checking -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = FLPrimary
                    )
                    UsernameState.Available -> Icon(
                        Icons.Filled.CheckCircle, contentDescription = "Available", tint = FLPrimary
                    )
                    is UsernameState.Unavailable -> Icon(
                        Icons.Filled.Cancel, contentDescription = "Unavailable", tint = Color.Red
                    )
                    UsernameState.Idle -> Unit
                }
            }
        )

        if (state is UsernameState.Unavailable) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(state.reason, fontSize = 12.sp, color = Color.Red)
                // Tappable alternatives — the point of the endpoint returning
                // suggestions at all is that the user never has to invent one.
                if (state.suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.suggestions.forEach { suggestion ->
                            Text(
                                text = "@$suggestion",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = FLPrimary,
                                modifier = Modifier
                                    .background(FLPrimary.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                                    .clickable { onSuggestionTap(suggestion) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthErrorText(message: String?, modifier: Modifier = Modifier, centered: Boolean = false) {
    if (message.isNullOrEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = message, fontSize = 12.sp, color = Color.Red)
    }
}
